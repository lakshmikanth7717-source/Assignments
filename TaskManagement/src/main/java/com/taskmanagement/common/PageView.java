package com.taskmanagement.common;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageView<T> of(Page<T> page) {
        return new PageView<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
