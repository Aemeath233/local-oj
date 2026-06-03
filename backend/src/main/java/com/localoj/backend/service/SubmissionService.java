package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.enums.Language;
import com.localoj.common.enums.Role;
import com.localoj.common.enums.SubmissionStatus;
import com.localoj.common.mapper.ContestMapper;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.SubmissionCaseResultMapper;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.mapper.UserMapper;
import com.localoj.common.mapper.ContestProblemMapper;
import com.localoj.common.mapper.ContestRegistrationMapper;
import com.localoj.common.model.Contest;
import com.localoj.common.model.ContestProblem;
import com.localoj.common.model.ContestRegistration;
import com.localoj.common.model.Problem;
import com.localoj.common.model.Submission;
import com.localoj.common.model.SubmissionCaseResult;
import com.localoj.common.model.User;
import com.localoj.common.queue.JudgeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.TimeUnit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class SubmissionService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionCaseResultMapper caseResultMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestRegistrationMapper contestRegistrationMapper;
    private final SystemLogService systemLogService;
    private final String submissionQueueKey;

    public SubmissionService(
            ProblemMapper problemMapper,
            SubmissionMapper submissionMapper,
            SubmissionCaseResultMapper caseResultMapper,
            UserMapper userMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ContestMapper contestMapper,
            ContestProblemMapper contestProblemMapper,
            ContestRegistrationMapper contestRegistrationMapper,
            SystemLogService systemLogService,
            @Value("${app.queue.submission-key}") String submissionQueueKey
    ) {
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.caseResultMapper = caseResultMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.contestMapper = contestMapper;
        this.contestProblemMapper = contestProblemMapper;
        this.contestRegistrationMapper = contestRegistrationMapper;
        this.systemLogService = systemLogService;
        this.submissionQueueKey = submissionQueueKey;
    }

    @Transactional
    public Submission submit(CurrentUser user, Long problemId, Language language, String sourceCode) {
        return submit(user, problemId, language, sourceCode, null);
    }

    @Transactional
    public Submission submit(CurrentUser user, Long problemId, Language language, String sourceCode, Long contestId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new IllegalArgumentException("Problem not found");
        }
        if (contestId == null && !Boolean.TRUE.equals(problem.getVisible())) {
            throw new IllegalArgumentException("Problem not found");
        }

        if (contestId != null) {
            Contest contest = contestMapper.selectById(contestId);
            if (contest == null || (!Boolean.TRUE.equals(contest.getVisible()) && user.role() != Role.ADMIN && user.role() != Role.SUPER_ADMIN)) {
                throw new IllegalArgumentException("Contest not found");
            }
            ContestProblem contestProblem = contestProblemMapper.selectOne(new QueryWrapper<ContestProblem>()
                    .eq("contest_id", contestId)
                    .eq("problem_id", problemId));
            if (contestProblem == null) {
                throw new IllegalArgumentException("Problem not in this contest");
            }
            LocalDateTime now = LocalDateTime.now();
            if (!contest.getEndTime().isAfter(now)) {
                throw new IllegalArgumentException("比赛已结束，无法再向该比赛提交");
            }
            if (now.isBefore(contest.getStartTime()) && !isAdmin(user)) {
                throw new IllegalArgumentException("Contest is not active");
            }
            if (!isAdmin(user) && !isRegistered(contestId, user.id())) {
                throw new IllegalArgumentException("请先报名比赛");
            }
        }

        if (!isAdmin(user)) {
            String redisKey = "cooldown:problem:" + problemId + ":user:" + user.id();
            Boolean success = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", java.time.Duration.ofSeconds(5));
            if (success == null || !success) {
                throw new IllegalArgumentException("提交过于频繁，该题目每 5 秒仅允许提交或自测一次！");
            }
        }

        Submission submission = new Submission();
        submission.setUserId(user.id());
        submission.setProblemId(problemId);
        submission.setLanguage(language);
        submission.setSourceCode(sourceCode);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setScore(0);
        submission.setCreatedAt(LocalDateTime.now());

        if (contestId != null) {
            submission.setContestId(contestId);
        }

        submissionMapper.insert(submission);
        systemLogService.info(
                "submission",
                "created",
                "提交已创建，等待入队",
                submission.getId(),
                submission.getProblemId(),
                submission.getUserId(),
                "language=" + submission.getLanguage() + "; contestId=" + submission.getContestId()
        );
        publishJudgeJobAfterCommit(submission.getId());
        redisTemplate.delete(SUBMISSIONS_CACHE_KEY);
        return submission;
    }

    public List<Submission> list(CurrentUser user) {
        return list(user, null, null);
    }

    public List<Submission> list(CurrentUser user, Long problemId, Boolean mine) {
        QueryWrapper<Submission> query = new QueryWrapper<Submission>()
                .select("id", "user_id", "problem_id", "language", "status", "verdict", "score", "time_ms", "memory_kb", "created_at", "judged_at", "contest_id")
                .isNull("contest_id");
        if (Boolean.TRUE.equals(mine)) {
            query.eq("user_id", user.id());
        }
        if (problemId != null) {
            query.eq("problem_id", problemId);
        }
        query.orderByDesc("id")
                .last("LIMIT 100");
        return submissionMapper.selectList(query);
    }

    private static final String SUBMISSIONS_CACHE_KEY = "cache:submissions:latest100";

    public List<SubmissionSummary> listSummaries(CurrentUser user) {
        return listSummaries(user, null, null);
    }

    public List<SubmissionSummary> listSummaries(CurrentUser user, Long problemId, Boolean mine) {
        boolean useCache = (problemId == null && (mine == null || !mine));
        if (useCache) {
            try {
                String cachedJson = redisTemplate.opsForValue().get(SUBMISSIONS_CACHE_KEY);
                if (cachedJson != null && !cachedJson.isBlank()) {
                    List<SubmissionSummary> cachedList = objectMapper.readValue(
                            cachedJson,
                            new TypeReference<List<SubmissionSummary>>() {}
                    );
                    if (cachedList != null && !cachedList.isEmpty()) {
                        return cachedList;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to read submissions list from Redis cache", e);
            }
        }

        List<Submission> submissions = list(user, problemId, mine);
        if (submissions.isEmpty()) {
            return List.of();
        }

        // Batch load users to prevent N+1 query loop
        List<Long> userIds = submissions.stream()
                .map(Submission::getUserId)
                .distinct()
                .toList();
        Map<Long, User> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        // Batch load problems to prevent N+1 query loop
        List<Long> problemIds = submissions.stream()
                .map(Submission::getProblemId)
                .distinct()
                .toList();
        Map<Long, Problem> problemMap = Collections.emptyMap();
        if (!problemIds.isEmpty()) {
            List<Problem> problems = problemMapper.selectBatchIds(problemIds);
            problemMap = problems.stream().collect(Collectors.toMap(Problem::getId, p -> p));
        }

        final Map<Long, User> finalUserMap = userMap;
        final Map<Long, Problem> finalProblemMap = problemMap;

        List<SubmissionSummary> list = submissions.stream()
                .map(s -> {
                    User submitter = finalUserMap.get(s.getUserId());
                    Problem problem = finalProblemMap.get(s.getProblemId());
                    return new SubmissionSummary(
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

        if (useCache && list != null && !list.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(list);
                redisTemplate.opsForValue().set(SUBMISSIONS_CACHE_KEY, json, 30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to write submissions list to Redis cache", e);
            }
        }

        return list;
    }

    public Submission requireVisibleSubmission(CurrentUser user, Long id) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new IllegalArgumentException("Submission not found");
        }

        // Admins or Super Admins can view any submission
        if (user.role() == Role.ADMIN || user.role() == Role.SUPER_ADMIN) {
            return submission;
        }

        // Submission owner can view their own submission
        if (submission.getUserId().equals(user.id())) {
            return submission;
        }

        // For contest submissions, normal users cannot view other people's submissions
        if (submission.getContestId() != null) {
            Contest contest = contestMapper.selectById(submission.getContestId());
            if (contest != null && !contest.getEndTime().isAfter(LocalDateTime.now())) {
                return submission;
            }
            throw new IllegalArgumentException("Submission not found");
        }

        // For public submissions, the user must have AC'd the problem to view others' code
        boolean hasPassed = hasUserPassedProblem(user.id(), submission.getProblemId());
        if (!hasPassed) {
            throw new IllegalArgumentException("您需要先通过（AC）该题目，才能查看其他人的提交详情！");
        }

        return submission;
    }

    private boolean hasUserPassedProblem(Long userId, Long problemId) {
        Long count = submissionMapper.selectCount(new QueryWrapper<Submission>()
                .eq("user_id", userId)
                .eq("problem_id", problemId)
                .eq("verdict", com.localoj.common.enums.Verdict.AC.name()));
        return count != null && count > 0;
    }

    private boolean isRegistered(Long contestId, Long userId) {
        Long count = contestRegistrationMapper.selectCount(new QueryWrapper<ContestRegistration>()
                .eq("contest_id", contestId)
                .eq("user_id", userId));
        return count != null && count > 0;
    }

    private boolean isAdmin(CurrentUser user) {
        return user.role() == Role.ADMIN || user.role() == Role.SUPER_ADMIN;
    }

    public Problem problemForSubmission(Submission submission) {
        return problemMapper.selectById(submission.getProblemId());
    }

    public List<SubmissionCaseResult> caseResults(CurrentUser user, Long submissionId) {
        requireVisibleSubmission(user, submissionId);
        return caseResultMapper.selectList(new QueryWrapper<SubmissionCaseResult>()
                .eq("submission_id", submissionId)
                .orderByAsc("case_index"));
    }

    @Transactional
    public Submission rejudge(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("Submission not found");
        }
        resetForJudge(submission);
        submissionMapper.updateById(submission);
        caseResultMapper.delete(new QueryWrapper<SubmissionCaseResult>().eq("submission_id", submissionId));
        systemLogService.info(
                "submission",
                "rejudge_requested",
                "提交已重置并准备重新判题",
                submission.getId(),
                submission.getProblemId(),
                submission.getUserId(),
                null
        );
        publishJudgeJobAfterCommit(submissionId);
        return submission;
    }

    @Transactional
    public int requeueUnfinished() {
        List<Submission> submissions = submissionMapper.selectList(new QueryWrapper<Submission>()
                .in("status", SubmissionStatus.PENDING.name(), SubmissionStatus.RUNNING.name())
                .orderByAsc("id")
                .last("LIMIT 100"));
        for (Submission submission : submissions) {
            resetForJudge(submission);
            submissionMapper.updateById(submission);
            systemLogService.info(
                    "submission",
                    "requeue_requested",
                    "未完成提交已重新入队",
                    submission.getId(),
                    submission.getProblemId(),
                    submission.getUserId(),
                    null
            );
            publishJudgeJobAfterCommit(submission.getId());
        }
        return submissions.size();
    }

    private void publishJudgeJob(Long submissionId) {
        try {
            redisTemplate.opsForList().leftPush(submissionQueueKey, objectMapper.writeValueAsString(new JudgeJob(submissionId)));
            systemLogService.info(
                    "judge-queue",
                    "job_published",
                    "判题任务已写入 Redis 队列",
                    submissionId,
                    null,
                    null,
                    "queue=" + submissionQueueKey
            );
        } catch (JsonProcessingException ex) {
            systemLogService.error("judge-queue", "job_serialize_failed", "判题任务序列化失败", submissionId, null, null, ex);
            throw new IllegalStateException("Failed to publish judge job", ex);
        } catch (RuntimeException ex) {
            systemLogService.error("judge-queue", "job_publish_failed", "判题任务写入 Redis 队列失败", submissionId, null, null, ex);
            throw ex;
        }
    }

    private void publishJudgeJobAfterCommit(Long submissionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishJudgeJob(submissionId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publishJudgeJob(submissionId);
                } catch (RuntimeException ex) {
                    log.error("Failed to publish judge job after commit for submission {}", submissionId, ex);
                    systemLogService.error("judge-queue", "job_publish_after_commit_failed", "事务提交后发布判题任务失败", submissionId, null, null, ex);
                }
            }
        });
    }

    private void resetForJudge(Submission submission) {
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setVerdict(null);
        submission.setScore(0);
        submission.setTimeMs(null);
        submission.setMemoryKb(null);
        submission.setErrorMessage(null);
        submission.setJudgedAt(null);
    }

    private SubmissionSummary toSummary(Submission submission) {
        User submitter = userMapper.selectById(submission.getUserId());
        Problem problem = problemMapper.selectById(submission.getProblemId());
        return new SubmissionSummary(
                submission.getId(),
                submission.getUserId(),
                submitter == null ? null : submitter.getUsername(),
                submitter == null ? null : submitter.getDisplayName(),
                submitter == null ? null : submitter.getAvatarUrl(),
                submission.getProblemId(),
                problem == null ? null : problem.getTitle(),
                submission.getLanguage().name(),
                submission.getStatus().name(),
                submission.getVerdict() == null ? null : submission.getVerdict().name(),
                submission.getScore(),
                submission.getTimeMs(),
                submission.getMemoryKb(),
                submission.getCreatedAt(),
                submission.getJudgedAt()
        );
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000)
    public void recoverPendingSubmissions() {
        LocalDateTime pendingThreshold = LocalDateTime.now().minusMinutes(2);
        List<Submission> stuckSubmissions = submissionMapper.selectList(new QueryWrapper<Submission>()
                .eq("status", SubmissionStatus.PENDING)
                .le("created_at", pendingThreshold));
        for (Submission sub : stuckSubmissions) {
            log.warn("Found stuck PENDING submission {}, re-publishing to Redis...", sub.getId());
            try {
                publishJudgeJob(sub.getId());
                systemLogService.info("judge-queue", "job_recovery_success", "成功自动重发卡死的 PENDING 任务", sub.getId(), sub.getProblemId(), sub.getUserId(), null);
            } catch (Exception ex) {
                log.error("Failed to recover submission " + sub.getId(), ex);
            }
        }
    }

    public record SubmissionSummary(
            Long id,
            Long userId,
            String username,
            String displayName,
            String avatarUrl,
            Long problemId,
            String problemTitle,
            String language,
            String status,
            String verdict,
            Integer score,
            Long timeMs,
            Long memoryKb,
            LocalDateTime createdAt,
            LocalDateTime judgedAt
    ) {
    }
}
