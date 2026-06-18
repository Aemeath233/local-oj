package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.enums.Verdict;
import com.coderushoj.common.mapper.ContestMapper;
import com.coderushoj.common.mapper.ContestProblemMapper;
import com.coderushoj.common.mapper.ContestRegistrationMapper;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.SubmissionMapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.Contest;
import com.coderushoj.common.model.ContestProblem;
import com.coderushoj.common.model.ContestRegistration;
import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.Submission;
import com.coderushoj.common.model.User;
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
        List<Contest> list = contestMapper.selectList(query);
        for (Contest c : list) {
            c.setParticipantCount(countRegistrations(c.getId()));
            c.setProblemCount(countContestProblems(c.getId()));
        }
        return list;
    }

    public List<AdminContestSummary> listAdminContestSummaries() {
        contestProblemVisibilityService.releaseEndedContestLocks();
        return contestMapper.selectList(new QueryWrapper<Contest>().orderByDesc("id")).stream()
                .map(contest -> new AdminContestSummary(
                        contest.getId(),
                        contest.getTitle(),
                        contest.getDescription(),
                        contest.getStartTime(),
                        contest.getEndTime(),
                        contest.getVisible(),
                        contest.getType(),
                        contest.getCreatedAt(),
                        contest.getUpdatedAt(),
                        contestStatus(contest),
                        countContestProblems(contest.getId()),
                        countRegistrations(contest.getId()),
                        countContestSubmissions(contest.getId())
                ))
                .toList();
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
        contest.setParticipantCount(countRegistrations(contest.getId()));
        contest.setProblemCount(countContestProblems(contest.getId()));
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
                .eq("contest_id", contestId)
                .ge("created_at", contest.getStartTime())
                .le("created_at", contest.getEndTime()));
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

    public List<SubmissionService.SubmissionSummary> listContestSubmissions(Long contestId, CurrentUser user) {
        Contest contest = requireContest(contestId, user);
        requireRegistration(contest, user);
        QueryWrapper<Submission> query = new QueryWrapper<Submission>()
                .select("id", "user_id", "problem_id", "language", "status", "verdict", "score", "time_ms", "memory_kb", "created_at", "judged_at", "contest_id")
                .eq("contest_id", contestId)
                .orderByDesc("id");
        if (!isEnded(contest) && !isAdmin(user)) {
            query.eq("user_id", user.id());
        }
        List<Submission> submissions = submissionMapper.selectList(query);
        if (submissions.isEmpty()) {
            return List.of();
        }

        // Batch load users
        List<Long> userIds = submissions.stream()
                .map(Submission::getUserId)
                .distinct()
                .toList();
        Map<Long, User> userMap = java.util.Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        // Batch load problems
        List<Long> problemIds = submissions.stream()
                .map(Submission::getProblemId)
                .distinct()
                .toList();
        Map<Long, Problem> problemMap = java.util.Collections.emptyMap();
        if (!problemIds.isEmpty()) {
            List<Problem> problems = problemMapper.selectBatchIds(problemIds);
            problemMap = problems.stream().collect(Collectors.toMap(Problem::getId, p -> p));
        }

        final Map<Long, User> finalUserMap = userMap;
        final Map<Long, Problem> finalProblemMap = problemMap;

        return submissions.stream()
                .map(s -> {
                    User submitter = finalUserMap.get(s.getUserId());
                    Problem problem = finalProblemMap.get(s.getProblemId());
                    return new SubmissionService.SubmissionSummary(
                            s.getId(),
                            s.getUserId(),
                            submitter == null ? null : submitter.getUsername(),
                            submitter == null ? null : submitter.getDisplayName(),
                            submitter == null ? null : submitter.getAvatarUrl(),
                            s.getProblemId(),
                            problem == null ? null : problem.getTitle(),
                            s.getLanguage().name(),
                            s.getStatus().name(),
                            s.getVerdict() == null ? null : s.getVerdict().name(),
                            s.getScore(),
                            s.getTimeMs(),
                            s.getMemoryKb(),
                            s.getCreatedAt(),
                            s.getJudgedAt()
                    );
                })
                .toList();
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
        syncProblemVisibility(contest, command.problemIds());
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
        syncProblemVisibility(contest, command.problemIds());
        return contest;
    }

    @Transactional
    public Contest setContestVisibility(Long id, boolean visible) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new IllegalArgumentException("Contest not found");
        }
        contest.setVisible(visible);
        contest.setUpdatedAt(LocalDateTime.now());
        contestMapper.updateById(contest);
        syncProblemVisibility(contest, contestProblemIds(id));
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
        contest.setFreezeDurationMinutes(command.freezeDurationMinutes() != null ? command.freezeDurationMinutes() : 0);
    }

    private void syncProblemVisibility(Contest contest, List<Long> problemIds) {
        if (Boolean.TRUE.equals(contest.getVisible())) {
            contestProblemVisibilityService.hideForContest(contest.getId(), problemIds);
        } else {
            contestProblemVisibilityService.releaseForContest(contest.getId());
        }
    }

    private void saveContestProblems(Long contestId, List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) return;
        for (int i = 0; i < problemIds.size(); i++) {
            ContestProblem cp = new ContestProblem(contestId, problemIds.get(i), i);
            contestProblemMapper.insert(cp);
        }
    }

    public boolean isEnded(Contest contest) {
        return contest != null && !contest.getEndTime().isAfter(LocalDateTime.now());
    }

    public void requireRegistration(Contest contest, CurrentUser user) {
        if (isEnded(contest) || isAdmin(user)) {
            return;
        }
        if (user == null || !isRegistered(contest.getId(), user.id())) {
            throw new IllegalArgumentException("请先报名比赛");
        }
    }

    public String getSequenceCode(int index) {
        StringBuilder sb = new StringBuilder();
        int temp = index;
        while (temp >= 0) {
            sb.insert(0, (char) ('A' + (temp % 26)));
            temp = (temp / 26) - 1;
        }
        return sb.toString();
    }

    private String contestStatus(Contest contest) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) {
            return "UPCOMING";
        }
        if (contest.getEndTime().isAfter(now)) {
            return "RUNNING";
        }
        return "FINISHED";
    }

    private List<Long> contestProblemIds(Long contestId) {
        return contestProblemMapper.selectList(new QueryWrapper<ContestProblem>()
                        .eq("contest_id", contestId)
                        .orderByAsc("sort_order"))
                .stream()
                .map(ContestProblem::getProblemId)
                .toList();
    }

    public boolean isAdmin(CurrentUser user) {
        return user != null && (user.role() == Role.ADMIN || user.role() == Role.SUPER_ADMIN);
    }

    public boolean isRegistered(Long contestId, Long userId) {
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

    private long countContestProblems(Long contestId) {
        Long count = contestProblemMapper.selectCount(new QueryWrapper<ContestProblem>()
                .eq("contest_id", contestId));
        return count == null ? 0 : count;
    }

    private long countContestSubmissions(Long contestId) {
        Long count = submissionMapper.selectCount(new QueryWrapper<Submission>()
                .eq("contest_id", contestId));
        return count == null ? 0 : count;
    }

    public record AdminContestSummary(
            Long id,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Boolean visible,
            String type,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String status,
            long problemCount,
            long registrationCount,
            long submissionCount
    ) {
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
            Integer freezeDurationMinutes,
            List<Long> problemIds
    ) {
    }
}
