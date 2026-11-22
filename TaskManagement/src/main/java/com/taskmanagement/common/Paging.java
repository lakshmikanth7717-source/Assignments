package com.taskmanagement.common;

import org.springframework.data.domain.*;

import java.util.Set;

public final class Paging {
    private Paging() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> allowed) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw ApiException.badRequest("page must be nonnegative and size must be between 1 and 100");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !allowed.contains(parts[0]) || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
            throw ApiException.badRequest("sort must be <field>,asc or <field>,desc; allowed fields: " + String.join(", ", allowed));
        }
        Sort order = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
        if (!parts[0].equals("id")) {
            order = order.and(Sort.by("id"));
        }
        return PageRequest.of(page, size, order);
    }
}
