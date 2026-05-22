package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.common.mapper.ProblemTagMapper;
import com.localoj.common.model.ProblemTag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProblemTagService {
    private final ProblemTagMapper problemTagMapper;

    public ProblemTagService(ProblemTagMapper problemTagMapper) {
        this.problemTagMapper = problemTagMapper;
    }

    public List<ProblemTag> list() {
        return problemTagMapper.selectList(new QueryWrapper<ProblemTag>().orderByAsc("id"));
    }

    @Transactional
    public ProblemTag create(ProblemTagCommand command) {
        String name = normalizeName(command.name());
        ensureUnique(name, null);
        LocalDateTime now = LocalDateTime.now();
        ProblemTag tag = new ProblemTag();
        tag.setName(name);
        tag.setColor(normalizeColor(command.color()));
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        problemTagMapper.insert(tag);
        return tag;
    }

    @Transactional
    public ProblemTag update(Long id, ProblemTagCommand command) {
        ProblemTag tag = requireTag(id);
        String name = normalizeName(command.name());
        ensureUnique(name, id);
        tag.setName(name);
        tag.setColor(normalizeColor(command.color()));
        tag.setUpdatedAt(LocalDateTime.now());
        problemTagMapper.updateById(tag);
        return tag;
    }

    @Transactional
    public void delete(Long id) {
        problemTagMapper.deleteById(id);
    }

    public ProblemTag requireTag(Long id) {
        ProblemTag tag = problemTagMapper.selectById(id);
        if (tag == null) {
            throw new IllegalArgumentException("标签不存在");
        }
        return tag;
    }

    private void ensureUnique(String name, Long currentId) {
        QueryWrapper<ProblemTag> query = new QueryWrapper<ProblemTag>().eq("name", name);
        if (currentId != null) {
            query.ne("id", currentId);
        }
        if (problemTagMapper.selectCount(query) > 0) {
            throw new IllegalArgumentException("标签已存在");
        }
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("标签名不能为空");
        }
        if (name.length() > 64) {
            throw new IllegalArgumentException("标签名过长");
        }
        if (name.contains(",") || name.contains("，")) {
            throw new IllegalArgumentException("标签名不能包含逗号");
        }
        return name;
    }

    private String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String color = value.trim();
        if (!color.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("颜色格式应为 #RRGGBB");
        }
        return color;
    }

    public record ProblemTagCommand(String name, String color) {
    }
}
