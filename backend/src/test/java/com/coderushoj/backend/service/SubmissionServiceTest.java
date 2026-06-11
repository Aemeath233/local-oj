package com.coderushoj.backend.service;

import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.mapper.ContestMapper;
import com.coderushoj.common.mapper.ContestProblemMapper;
import com.coderushoj.common.mapper.ContestRegistrationMapper;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.SubmissionCaseResultMapper;
import com.coderushoj.common.mapper.SubmissionMapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.Submission;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionServiceTest {

    @Test
    void contestSubmissionDetailIsHiddenFromOtherStudentsAfterContestEnds() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        Submission submission = contestSubmission(10L, 1L, 100L, 200L);
        when(submissionMapper.selectById(10L)).thenReturn(submission);

        SubmissionService service = service(submissionMapper);
        CurrentUser otherStudent = new CurrentUser(2L, "bob", Role.STUDENT, 0);

        assertThrows(IllegalArgumentException.class, () -> service.requireVisibleSubmission(otherStudent, 10L));
    }

    @Test
    void contestSubmissionDetailIsVisibleToOwner() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        Submission submission = contestSubmission(10L, 1L, 100L, 200L);
        when(submissionMapper.selectById(10L)).thenReturn(submission);

        SubmissionService service = service(submissionMapper);
        CurrentUser owner = new CurrentUser(1L, "alice", Role.STUDENT, 0);

        assertSame(submission, service.requireVisibleSubmission(owner, 10L));
    }

    private SubmissionService service(SubmissionMapper submissionMapper) {
        return new SubmissionService(
                mock(ProblemMapper.class),
                submissionMapper,
                mock(SubmissionCaseResultMapper.class),
                mock(UserMapper.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                mock(ContestMapper.class),
                mock(ContestProblemMapper.class),
                mock(ContestRegistrationMapper.class),
                mock(SystemLogService.class),
                "judge:queue"
        );
    }

    private Submission contestSubmission(Long id, Long userId, Long problemId, Long contestId) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setUserId(userId);
        submission.setProblemId(problemId);
        submission.setContestId(contestId);
        return submission;
    }
}
