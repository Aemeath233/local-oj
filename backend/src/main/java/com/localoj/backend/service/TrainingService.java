package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.TrainingProblemRelationMapper;
import com.localoj.common.mapper.TrainingSetMapper;
import com.localoj.common.model.Problem;
import com.localoj.common.model.TrainingProblemRelation;
import com.localoj.common.model.TrainingSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrainingService {
    private final TrainingSetMapper trainingSetMapper;
    private final TrainingProblemRelationMapper trainingProblemRelationMapper;
    private final ProblemMapper problemMapper;
    private final ProblemService problemService;

    public TrainingService(
            TrainingSetMapper trainingSetMapper,
            TrainingProblemRelationMapper trainingProblemRelationMapper,
            ProblemMapper problemMapper,
            ProblemService problemService
    ) {
        this.trainingSetMapper = trainingSetMapper;
        this.trainingProblemRelationMapper = trainingProblemRelationMapper;
        this.problemMapper = problemMapper;
        this.problemService = problemService;
    }

    public List<TrainingSetDto> listTrainingSets(CurrentUser user, boolean includeInvisible) {
        QueryWrapper<TrainingSet> query = new QueryWrapper<>();
        if (!includeInvisible) {
            query.eq("visible", true);
        }
        query.orderByDesc("id");
        List<TrainingSet> sets = trainingSetMapper.selectList(query);

        List<TrainingSetDto> dtos = new ArrayList<>();
        for (TrainingSet set : sets) {
            List<Problem> problems = getProblemsInSet(set.getId(), !includeInvisible);
            int total = problems.size();
            int solved = 0;
            if (user != null && total > 0) {
                List<Long> problemIds = problems.stream().map(Problem::getId).toList();
                Map<Long, String> statuses = problemService.solveStatuses(user, problemIds);
                solved = (int) statuses.values().stream().filter("ACCEPTED"::equals).count();
            }
            dtos.add(new TrainingSetDto(
                    set.getId(),
                    set.getTitle(),
                    set.getDescription(),
                    set.getVisible(),
                    total,
                    solved,
                    set.getCreatedAt()
            ));
        }
        return dtos;
    }

    public TrainingSet requireTrainingSet(Long id) {
        TrainingSet set = trainingSetMapper.selectById(id);
        if (set == null) {
            throw new IllegalArgumentException("Training set not found");
        }
        return set;
    }

    public List<ProblemWithStatusDto> getProblemsWithStatus(Long trainingId, CurrentUser user) {
        List<Problem> problems = getProblemsInSet(trainingId, true);
        if (problems.isEmpty()) {
            return List.of();
        }

        List<Long> problemIds = problems.stream().map(Problem::getId).toList();
        Map<Long, String> statuses = problemService.solveStatuses(user, problemIds);

        return problems.stream().map(p -> new ProblemWithStatusDto(
                p.getId(),
                p.getSlug(),
                p.getTitle(),
                p.getDifficulty(),
                p.getTags(),
                statuses.getOrDefault(p.getId(), "UNATTEMPTED")
        )).collect(Collectors.toList());
    }

    public List<Problem> getProblemsInSet(Long trainingId, boolean visibleOnly) {
        List<TrainingProblemRelation> relations = trainingProblemRelationMapper.selectList(
                new QueryWrapper<TrainingProblemRelation>()
                        .eq("training_id", trainingId)
                        .orderByAsc("display_order")
                        .orderByAsc("id")
        );
        if (relations.isEmpty()) {
            return List.of();
        }

        List<Long> problemIds = relations.stream().map(TrainingProblemRelation::getProblemId).toList();
        QueryWrapper<Problem> query = new QueryWrapper<Problem>().in("id", problemIds);
        if (visibleOnly) {
            query.eq("visible", true);
        }
        List<Problem> problems = problemMapper.selectList(query);
        problemService.populateTags(problems);

        // Keep the display_order / sequence sorting!
        Map<Long, Problem> problemMap = problems.stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));

        List<Problem> sortedProblems = new ArrayList<>();
        for (TrainingProblemRelation rel : relations) {
            Problem p = problemMap.get(rel.getProblemId());
            if (p != null) {
                sortedProblems.add(p);
            }
        }
        return sortedProblems;
    }

    @Transactional
    public TrainingSet createTrainingSet(String title, String description, boolean visible) {
        TrainingSet set = new TrainingSet();
        set.setTitle(title);
        set.setDescription(description);
        set.setVisible(visible);
        LocalDateTime now = LocalDateTime.now();
        set.setCreatedAt(now);
        set.setUpdatedAt(now);
        trainingSetMapper.insert(set);
        return set;
    }

    @Transactional
    public TrainingSet updateTrainingSet(Long id, String title, String description, boolean visible) {
        TrainingSet set = requireTrainingSet(id);
        set.setTitle(title);
        set.setDescription(description);
        set.setVisible(visible);
        set.setUpdatedAt(LocalDateTime.now());
        trainingSetMapper.updateById(set);
        return set;
    }

    @Transactional
    public void deleteTrainingSet(Long id) {
        trainingProblemRelationMapper.delete(
                new QueryWrapper<TrainingProblemRelation>().eq("training_id", id)
        );
        trainingSetMapper.deleteById(id);
    }

    @Transactional
    public void linkProblems(Long trainingId, List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return;
        }
        // Fetch current max display_order to append nicely
        List<TrainingProblemRelation> existing = trainingProblemRelationMapper.selectList(
                new QueryWrapper<TrainingProblemRelation>().eq("training_id", trainingId)
        );
        int maxOrder = existing.stream()
                .mapToInt(TrainingProblemRelation::getDisplayOrder)
                .max()
                .orElse(0);

        for (Long problemId : problemIds) {
            TrainingProblemRelation rel = trainingProblemRelationMapper.selectOne(
                    new QueryWrapper<TrainingProblemRelation>()
                            .eq("training_id", trainingId)
                            .eq("problem_id", problemId)
            );
            if (rel == null) {
                rel = new TrainingProblemRelation();
                rel.setTrainingId(trainingId);
                rel.setProblemId(problemId);
                rel.setDisplayOrder(++maxOrder);
                trainingProblemRelationMapper.insert(rel);
            }
        }
    }

    @Transactional
    public void unlinkProblems(Long trainingId, List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return;
        }
        trainingProblemRelationMapper.delete(
                new QueryWrapper<TrainingProblemRelation>()
                        .eq("training_id", trainingId)
                        .in("problem_id", problemIds)
        );
    }

    @Transactional
    public void reorderProblems(Long trainingId, List<Long> problemIdsOrdered) {
        if (problemIdsOrdered == null || problemIdsOrdered.isEmpty()) {
            return;
        }
        int order = 1;
        for (Long problemId : problemIdsOrdered) {
            TrainingProblemRelation rel = trainingProblemRelationMapper.selectOne(
                    new QueryWrapper<TrainingProblemRelation>()
                            .eq("training_id", trainingId)
                            .eq("problem_id", problemId)
            );
            if (rel != null) {
                rel.setDisplayOrder(order++);
                trainingProblemRelationMapper.updateById(rel);
            }
        }
    }

    public record TrainingSetDto(
            Long id,
            String title,
            String description,
            Boolean visible,
            int totalProblems,
            int solvedProblems,
            LocalDateTime createdAt
    ) {}

    public record ProblemWithStatusDto(
            Long id,
            String slug,
            String title,
            String difficulty,
            String tags,
            String solveStatus
    ) {}
}
