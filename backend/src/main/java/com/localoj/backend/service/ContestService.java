package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.enums.Role;
import com.localoj.common.enums.Verdict;
import com.localoj.common.mapper.ContestMapper;
import com.localoj.common.mapper.ContestProblemMapper;
import com.localoj.common.mapper.ContestRegistrationMapper;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.mapper.UserMapper;
import com.localoj.common.model.Contest;
import com.localoj.common.model.ContestProblem;
import com.localoj.common.model.ContestRegistration;
import com.localoj.common.model.Problem;
import com.localoj.common.model.Submission;
import com.localoj.common.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContestService {
    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestRegistrationMapper contestRegistrationMapper;
    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ContestProblemVisibilityService contestProblemVisibilityService;

    public ContestService(
            ContestMapper contestMapper,
            ContestProblemMapper contestProblemMapper,
            ContestRegistrationMapper contestRegistrationMapper,
            ProblemMapper problemMapper,
            SubmissionMapper submissionMapper,
            UserMapper userMapper,
            ContestProblemVisibilityService contestProblemVisibilityService
    ) {
        this.contestMapper = contestMapper;
        this.contestProblemMapper = contestProblemMapper;
        this.contestRegistrationMapper = contestRegistrationMapper;
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.contestProblemVisibilityService = contestProblemVisibilityService;
    }

    public List<Contest> listContests(CurrentUser user) {
        contestProblemVisibilityService.releaseEndedContestLocks();
        QueryWrapper<Contest> query = new QueryWrapper<Contest>().orderByDesc("id");
        if (user == null || (user.role() != Role.ADMIN && user.role() != Role.SUPER_ADMIN)) {
            query.eq("visible", true);
        }
        return contestMapper.selectList(query);
    }

    public Contest requireContest(Long id, CurrentUser user) {
        contestProblemVisibilityService.releaseEndedContestLocks();
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new IllegalArgumentException("Contest not found");
        }
        if (!Boolean.TRUE.equals(contest.getVisible())) {
            if (user == null || (user.role() != Role.ADMIN && user.role() != Role.SUPER_ADMIN)) {
                throw new IllegalArgumentException("Contest not found");
            }
        }
        return contest;
    }

    public ContestRegistrationStatus registrationStatus(Long contestId, CurrentUser user) {
        Contest contest = requireContest(contestId, user);
        ContestRegistration registration = user == null
                ? null
                : contestRegistrationMapper.selectOne(new QueryWrapper<ContestRegistration>()
                        .eq("contest_id", contestId)
                        .eq("user_id", user.id()));
        boolean registered = registration != null;
        boolean canRegister = user != null
                && !registered
                && Boolean.TRUE.equals(contest.getVisible())
                && LocalDateTime.now().isBefore(contest.getEndTime());
        return new ContestRegistrationStatus(
                registered,
                canRegister,
                countRegistrations(contestId),
                registration == null ? null : registration.getRegisteredAt()
        );
    }

    @Transactional
    public ContestRegistrationStatus registerContest(Long contestId, CurrentUser user) {
        Contest contest = requireContest(contestId, user);
        if (!Boolean.TRUE.equals(contest.getVisible())) {
            throw new IllegalArgumentException("Contest not found");
        }
        if (!LocalDateTime.now().isBefore(contest.getEndTime())) {
            throw new IllegalArgumentException("比赛已结束，无法报名");
        }
        boolean registered = isRegistered(contestId, user.id());
        if (!registered) {
            contestRegistrationMapper.insert(new ContestRegistration(contestId, user.id(), LocalDateTime.now()));
        }
        return registrationStatus(contestId, user);
    }

    public List<ContestProblemDetail> getContestProblems(Long contestId, CurrentUser user) {
        Contest contest = requireContest(contestId, user);
        LocalDateTime now = LocalDateTime.now();
        // Students cannot see problems before the contest starts!
        if (now.isBefore(contest.getStartTime())) {
            if (user == null || (user.role() != Role.ADMIN && user.role() != Role.SUPER_ADMIN)) {
                throw new IllegalArgumentException("Contest has not started yet");
            }
        }
        requireRegistration(contest, user);

        List<ContestProblem> cpList = contestProblemMapper.selectList(new QueryWrapper<ContestProblem>()
                .eq("contest_id", contestId)
                .orderByAsc("sort_order"));
        if (cpList.isEmpty()) {
            return List.of();
        }

        List<Long> problemIds = cpList.stream().map(ContestProblem::getProblemId).toList();
        List<Problem> problems = problemMapper.selectBatchIds(problemIds);
        Map<Long, Problem> problemMap = problems.stream().collect(Collectors.toMap(Problem::getId, p -> p));

        // Get solve stats
        List<Submission> submissions = submissionMapper.selectList(new QueryWrapper<Submission>()
                .eq("contest_id", contestId));
        Map<Long, List<Submission>> submissionsByProblem = submissions.stream()
                .collect(Collectors.groupingBy(Submission::getProblemId));

        List<ContestProblemDetail> result = new ArrayList<>();
        for (int i = 0; i < cpList.size(); i++) {
            ContestProblem cp = cpList.get(i);
            Problem problem = problemMap.get(cp.getProblemId());
            if (problem == null) continue;

            List<Submission> pSubs = submissionsByProblem.getOrDefault(problem.getId(), List.of());
            long total = pSubs.size();
            long ac = pSubs.stream().filter(s -> s.getVerdict() == Verdict.AC).count();

            // Sequence character mapper (0 -> A, 1 -> B, 2 -> C etc.)
            String sequenceCode = getSequenceCode(i);

            result.add(new ContestProblemDetail(
                    problem.getId(),
                    problem.getSlug(),
                    problem.getTitle(),
                    problem.getDifficulty(),
                    sequenceCode,
                    total,
                    ac
            ));
        }

        return result;
    }

    public Problem getContestProblemDetail(Long contestId, Long problemId, CurrentUser user) {
        Contest contest = requireContest(contestId, user);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) {
            if (user == null || (user.role() != Role.ADMIN && user.role() != Role.SUPER_ADMIN)) {
                throw new IllegalArgumentException("Contest has not started yet");
            }
        }
        requireRegistration(contest, user);

        ContestProblem cp = contestProblemMapper.selectOne(new QueryWrapper<ContestProblem>()
                .eq("contest_id", contestId)
                .eq("problem_id", problemId));
        if (cp == null) {
            throw new IllegalArgumentException("Problem not in this contest");
        }

        return problemMapper.selectById(problemId);
    }

    public List<Submission> listContestSubmissions(Long contestId, CurrentUser user) {
        Contest contest = requireContest(contestId, user);
        requireRegistration(contest, user);
        QueryWrapper<Submission> query = new QueryWrapper<Submission>()
                .eq("contest_id", contestId)
                .orderByDesc("id");
        if (!isEnded(contest) && !isAdmin(user)) {
            query.eq("user_id", user.id());
        }
        return submissionMapper.selectList(query);
    }

    @Transactional
    public Contest createContest(ContestCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Contest contest = new Contest();
        fillContest(contest, command);
        contest.setCreatedAt(now);
        contest.setUpdatedAt(now);
        contestMapper.insert(contest);
        saveContestProblems(contest.getId(), command.problemIds());
        contestProblemVisibilityService.hideForContest(contest.getId(), command.problemIds());
        return contest;
    }

    @Transactional
    public Contest updateContest(Long id, ContestCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new IllegalArgumentException("Contest not found");
        }
        fillContest(contest, command);
        contest.setUpdatedAt(now);
        contestMapper.updateById(contest);
        contestProblemVisibilityService.releaseForContest(id);
        contestProblemMapper.delete(new QueryWrapper<ContestProblem>().eq("contest_id", id));
        saveContestProblems(id, command.problemIds());
        contestProblemVisibilityService.hideForContest(id, command.problemIds());
        return contest;
    }

    @Transactional
    public void deleteContest(Long id) {
        contestProblemVisibilityService.releaseForContest(id);
        contestMapper.deleteById(id);
    }

    private void fillContest(Contest contest, ContestCommand command) {
        contest.setTitle(command.title());
        contest.setDescription(command.description());
        contest.setStartTime(command.startTime());
        contest.setEndTime(command.endTime());
        contest.setVisible(command.visible());
        contest.setType(command.type() != null ? command.type() : "ACM");
    }

    private void saveContestProblems(Long contestId, List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) return;
        for (int i = 0; i < problemIds.size(); i++) {
            ContestProblem cp = new ContestProblem(contestId, problemIds.get(i), i);
            contestProblemMapper.insert(cp);
        }
    }

    public List<ContestStandingsRow> calculateStandings(Long contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new IllegalArgumentException("Contest not found");
        }
        LocalDateTime start = contest.getStartTime();

        List<ContestRegistration> registrations = contestRegistrationMapper.selectList(new QueryWrapper<ContestRegistration>()
                .eq("contest_id", contestId)
                .orderByAsc("registered_at"));
        Set<Long> userIds = registrations.stream()
                .map(ContestRegistration::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) {
            return List.of();
        }

        // Fetch all submissions for this contest
        List<Submission> submissions = submissionMapper.selectList(new QueryWrapper<Submission>()
                .eq("contest_id", contestId)
                .in("user_id", userIds)
                .orderByAsc("created_at")
                .orderByAsc("id"));

        // Fetch all problems in this contest
        List<ContestProblem> cpList = contestProblemMapper.selectList(new QueryWrapper<ContestProblem>()
                .eq("contest_id", contestId)
                .orderByAsc("sort_order"));
        List<Long> contestProblemIds = cpList.stream().map(ContestProblem::getProblemId).toList();

        // Map users
        List<User> users = userIds.isEmpty() ? List.of() : userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        // Find first-to-solve for each problem
        Map<Long, Long> problemFirstAcSubmissionId = new HashMap<>();
        Map<Long, LocalDateTime> problemFirstAcTime = new HashMap<>();
        for (Submission s : submissions) {
            if (s.getVerdict() == Verdict.AC) {
                if (!problemFirstAcTime.containsKey(s.getProblemId()) || s.getCreatedAt().isBefore(problemFirstAcTime.get(s.getProblemId()))) {
                    problemFirstAcTime.put(s.getProblemId(), s.getCreatedAt());
                    problemFirstAcSubmissionId.put(s.getProblemId(), s.getId());
                }
            }
        }

        // Compute for each user
        Map<Long, List<Submission>> submissionsByUser = submissions.stream()
                .collect(Collectors.groupingBy(Submission::getUserId));

        List<ContestStandingsRow> rows = new ArrayList<>();
        boolean isOI = "OI".equals(contest.getType());

        for (Long userId : userIds) {
            User user = userMap.get(userId);
            if (user == null) continue;

            List<Submission> userSubs = submissionsByUser.getOrDefault(userId, List.of());

            int acceptedCount = 0;
            long totalPenaltyMinutes = 0;
            Integer totalScore = isOI ? 0 : null;
            Map<Long, ProblemStatusDetail> problemDetails = new HashMap<>();

            // Group user submissions by problem
            Map<Long, List<Submission>> subsByProblem = userSubs.stream()
                    .collect(Collectors.groupingBy(Submission::getProblemId));

            LocalDateTime lastAcTime = null;
            LocalDateTime lastSubTime = null;

            for (Long problemId : contestProblemIds) {
                List<Submission> pSubs = subsByProblem.getOrDefault(problemId, List.of());

                boolean accepted = false;
                int failedAttempts = 0;
                Long acElapsedMinutes = null;
                boolean firstToSolve = false;
                Integer problemScore = isOI ? 0 : null;

                if (isOI) {
                    for (Submission s : pSubs) {
                        int sScore = s.getScore() != null ? s.getScore() : 0;
                        if (sScore > problemScore) {
                            problemScore = sScore;
                        }
                        if (sScore == 100) {
                            accepted = true;
                        }
                        if (lastSubTime == null || s.getCreatedAt().isBefore(contest.getEndTime())) {
                            if (lastSubTime == null || s.getCreatedAt().isAfter(lastSubTime)) {
                                lastSubTime = s.getCreatedAt();
                            }
                        }
                    }
                    if (problemScore > 0) {
                        totalScore += problemScore;
                    }
                } else {
                    for (Submission s : pSubs) {
                        if (s.getVerdict() == Verdict.AC) {
                            accepted = true;
                            acElapsedMinutes = Duration.between(start, s.getCreatedAt()).toMinutes();
                            if (acElapsedMinutes < 0) acElapsedMinutes = 0L;

                            if (Objects.equals(s.getId(), problemFirstAcSubmissionId.get(problemId))) {
                                firstToSolve = true;
                            }

                            if (lastAcTime == null || s.getCreatedAt().isAfter(lastAcTime)) {
                                lastAcTime = s.getCreatedAt();
                            }
                            break; // Stop counting submissions after first AC
                        } else {
                            // CE does not count towards penalty attempts
                            if (s.getVerdict() != Verdict.CE) {
                                failedAttempts++;
                            }
                        }
                    }

                    if (accepted) {
                        acceptedCount++;
                        totalPenaltyMinutes += acElapsedMinutes + (failedAttempts * 20L);
                    }
                }

                problemDetails.put(problemId, new ProblemStatusDetail(accepted, failedAttempts, acElapsedMinutes, firstToSolve, problemScore));
            }

            rows.add(new ContestStandingsRow(
                    0, // Rank to be populated later
                    userId,
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getAvatarUrl(),
                    acceptedCount,
                    totalPenaltyMinutes,
                    totalScore,
                    problemDetails,
                    isOI ? lastSubTime : lastAcTime
            ));
        }

        // Sort rows
        rows.sort((a, b) -> {
            if (isOI) {
                if (!Objects.equals(a.totalScore(), b.totalScore())) {
                    return Integer.compare(b.totalScore(), a.totalScore()); // Descending
                }
                if (a.lastAcTime() == null && b.lastAcTime() == null) return 0;
                if (a.lastAcTime() == null) return 1;
                if (b.lastAcTime() == null) return -1;
                return a.lastAcTime().compareTo(b.lastAcTime()); // Ascending (earlier sub first)
            } else {
                if (a.acceptedCount() != b.acceptedCount()) {
                    return Integer.compare(b.acceptedCount(), a.acceptedCount()); // Descending
                }
                if (a.totalPenaltyMinutes() != b.totalPenaltyMinutes()) {
                    return Long.compare(a.totalPenaltyMinutes(), b.totalPenaltyMinutes()); // Ascending
                }
                if (a.lastAcTime() == null && b.lastAcTime() == null) return 0;
                if (a.lastAcTime() == null) return 1;
                if (b.lastAcTime() == null) return -1;
                return a.lastAcTime().compareTo(b.lastAcTime()); // Ascending
            }
        });

        // Assign ranks
        for (int i = 0; i < rows.size(); i++) {
            ContestStandingsRow old = rows.get(i);
            rows.set(i, new ContestStandingsRow(
                    i + 1,
                    old.userId(),
                    old.username(),
                    old.displayName(),
                    old.avatarUrl(),
                    old.acceptedCount(),
                    old.totalPenaltyMinutes(),
                    old.totalScore(),
                    old.problemDetails(),
                    old.lastAcTime()
            ));
        }

        return rows;
    }

    private String getSequenceCode(int index) {
        StringBuilder sb = new StringBuilder();
        int temp = index;
        while (temp >= 0) {
            sb.insert(0, (char) ('A' + (temp % 26)));
            temp = (temp / 26) - 1;
        }
        return sb.toString();
    }

    private void requireRegistration(Contest contest, CurrentUser user) {
        if (isEnded(contest) || isAdmin(user)) {
            return;
        }
        if (user == null || !isRegistered(contest.getId(), user.id())) {
            throw new IllegalArgumentException("请先报名比赛");
        }
    }

    private boolean isEnded(Contest contest) {
        return contest != null && !contest.getEndTime().isAfter(LocalDateTime.now());
    }

    private boolean isAdmin(CurrentUser user) {
        return user != null && (user.role() == Role.ADMIN || user.role() == Role.SUPER_ADMIN);
    }

    private boolean isRegistered(Long contestId, Long userId) {
        Long count = contestRegistrationMapper.selectCount(new QueryWrapper<ContestRegistration>()
                .eq("contest_id", contestId)
                .eq("user_id", userId));
        return count != null && count > 0;
    }

    private long countRegistrations(Long contestId) {
        Long count = contestRegistrationMapper.selectCount(new QueryWrapper<ContestRegistration>()
                .eq("contest_id", contestId));
        return count == null ? 0 : count;
    }

    public record ContestRegistrationStatus(
            boolean registered,
            boolean canRegister,
            long registrationCount,
            LocalDateTime registeredAt
    ) {
    }

    public record ContestProblemDetail(
            Long id,
            String slug,
            String title,
            String difficulty,
            String sequenceCode,
            long submissionCount,
            long acceptedCount
    ) {
    }

    public record ContestCommand(
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Boolean visible,
            String type,
            List<Long> problemIds
    ) {
    }

    public record ContestStandingsRow(
            int rank,
            Long userId,
            String username,
            String displayName,
            String avatarUrl,
            int acceptedCount,
            long totalPenaltyMinutes,
            Integer totalScore,
            Map<Long, ProblemStatusDetail> problemDetails,
            LocalDateTime lastAcTime
    ) {
    }

    public record ProblemStatusDetail(
            boolean accepted,
            int failedAttempts,
            Long acElapsedMinutes,
            boolean firstToSolve,
            Integer score
    ) {
    }
}
