package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.common.enums.Verdict;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContestStandingsService {

    private final ContestService contestService;
    private final ContestRegistrationMapper contestRegistrationMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;

    public ContestStandingsService(
            ContestService contestService,
            ContestRegistrationMapper contestRegistrationMapper,
            ContestProblemMapper contestProblemMapper,
            SubmissionMapper submissionMapper,
            UserMapper userMapper,
            ProblemMapper problemMapper
    ) {
        this.contestService = contestService;
        this.contestRegistrationMapper = contestRegistrationMapper;
        this.contestProblemMapper = contestProblemMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.problemMapper = problemMapper;
    }

    public List<ContestStandingsRow> calculateStandings(Long contestId) {
        return calculateStandings(contestId, null);
    }

    public List<ContestStandingsRow> calculateStandings(Long contestId, CurrentUser user) {
        Contest contest = contestService.requireContest(contestId, user);
        contestService.requireRegistration(contest, user);
        LocalDateTime start = contest.getStartTime();
        LocalDateTime end = contest.getEndTime();

        // Determine if standings are frozen for the current user
        LocalDateTime queryEnd = end;
        if (contest.getFreezeDurationMinutes() != null && contest.getFreezeDurationMinutes() > 0) {
            LocalDateTime freezeStart = end.minusMinutes(contest.getFreezeDurationMinutes());
            LocalDateTime now = LocalDateTime.now();
            boolean isAdmin = user != null && (user.role() == com.coderushoj.common.enums.Role.ADMIN || user.role() == com.coderushoj.common.enums.Role.SUPER_ADMIN);
            if (now.isAfter(freezeStart) && now.isBefore(end) && !isAdmin) {
                queryEnd = freezeStart;
            }
        }

        List<ContestRegistration> registrations = contestRegistrationMapper.selectList(new QueryWrapper<ContestRegistration>()
                .eq("contest_id", contestId)
                .orderByAsc("registered_at"));
        Set<Long> userIds = registrations.stream()
                .map(ContestRegistration::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) {
            return List.of();
        }

        // Fetch all problems in this contest
        List<ContestProblem> cpList = contestProblemMapper.selectList(new QueryWrapper<ContestProblem>()
                .eq("contest_id", contestId)
                .orderByAsc("sort_order"));
        List<Long> contestProblemIds = cpList.stream().map(ContestProblem::getProblemId).toList();
        Set<Long> contestProblemIdSet = new LinkedHashSet<>(contestProblemIds);

        // Fetch all submissions for this contest
        LocalDateTime finalQueryEnd = queryEnd;
        List<Submission> submissions = submissionMapper.selectList(new QueryWrapper<Submission>()
                .eq("contest_id", contestId)
                .in("user_id", userIds)
                .ge("created_at", start)
                .le("created_at", queryEnd)
                .orderByAsc("created_at")
                .orderByAsc("id")).stream()
                .filter(s -> s.getCreatedAt() != null)
                .filter(s -> !s.getCreatedAt().isBefore(start) && !s.getCreatedAt().isAfter(finalQueryEnd))
                .filter(s -> contestProblemIdSet.contains(s.getProblemId()))
                .toList();

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
            User contestant = userMap.get(userId);
            if (contestant == null) continue;

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
                    LocalDateTime problemBestScoreTime = null;
                    for (Submission s : pSubs) {
                        int sScore = s.getScore() != null ? s.getScore() : 0;
                        if (sScore > problemScore) {
                            problemScore = sScore;
                            problemBestScoreTime = s.getCreatedAt();
                        }
                        if (sScore == 100) {
                            accepted = true;
                        }
                    }
                    if (problemScore > 0) {
                        totalScore += problemScore;
                    }
                    if (problemBestScoreTime != null) {
                        if (lastSubTime == null || problemBestScoreTime.isAfter(lastSubTime)) {
                            lastSubTime = problemBestScoreTime;
                        }
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
                    contestant.getUsername(),
                    contestant.getDisplayName(),
                    contestant.getAvatarUrl(),
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

    public byte[] exportStandingsCsv(Long contestId, CurrentUser user) {
        Contest contest = contestService.requireContest(contestId, user);
        contestService.requireRegistration(contest, user);
        List<ContestStandingsRow> standings = calculateStandings(contestId, user);

        // Fetch problems to map headers
        List<ContestProblem> cpList = contestProblemMapper.selectList(new QueryWrapper<ContestProblem>()
                .eq("contest_id", contestId)
                .orderByAsc("sort_order"));

        List<Long> contestProblemIds = cpList.stream().map(ContestProblem::getProblemId).toList();

        // Fetch problem titles for header
        Map<Long, String> problemTitleMap = new HashMap<>();
        if (!contestProblemIds.isEmpty()) {
            List<Problem> problems = problemMapper.selectBatchIds(contestProblemIds);
            for (Problem p : problems) {
                problemTitleMap.put(p.getId(), p.getTitle());
            }
        }

        StringBuilder sb = new StringBuilder();
        // Microsoft Excel UTF-8 BOM
        sb.append("\uFEFF");

        boolean isOI = "OI".equals(contest.getType());

        // Header Row
        sb.append("排名,用户名,昵称");
        if (isOI) {
            sb.append(",总分");
        } else {
            sb.append(",通过数,总罚时");
        }
        for (int i = 0; i < cpList.size(); i++) {
            Long pid = cpList.get(i).getProblemId();
            String code = contestService.getSequenceCode(i);
            String title = problemTitleMap.getOrDefault(pid, "");
            sb.append(",").append(escapeCsv(code + " (" + title + ")"));
        }
        sb.append("\n");

        // Data Rows
        for (ContestStandingsRow row : standings) {
            sb.append(row.rank()).append(",")
              .append(escapeCsv(row.username())).append(",")
              .append(escapeCsv(row.displayName()));

            if (isOI) {
                sb.append(",").append(row.totalScore() != null ? row.totalScore() : 0);
            } else {
                sb.append(",").append(row.acceptedCount()).append(",")
                  .append(row.totalPenaltyMinutes());
            }

            for (Long pid : contestProblemIds) {
                ProblemStatusDetail detail = row.problemDetails().get(pid);
                sb.append(",");
                if (detail != null) {
                    if (isOI) {
                        sb.append(detail.score() != null ? detail.score() : 0);
                    } else {
                        if (detail.accepted()) {
                            if (detail.failedAttempts() > 0) {
                                sb.append(escapeCsv("+" + detail.failedAttempts() + " (" + detail.acElapsedMinutes() + ")"));
                            } else {
                                sb.append(escapeCsv("+ (" + detail.acElapsedMinutes() + ")"));
                            }
                        } else if (detail.failedAttempts() > 0) {
                            sb.append(escapeCsv("-" + detail.failedAttempts()));
                        }
                    }
                }
            }
            sb.append("\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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
