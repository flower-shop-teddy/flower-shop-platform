package com.flowershop.common.api;

import java.util.List;

/** Không trả thẳng Page của Spring ra ngoài: cấu trúc của nó có thể đổi theo version. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
