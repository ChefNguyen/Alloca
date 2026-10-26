package com.alloca.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Paged list body, matching the OpenAPI {@code Paged*} and {@code PageMetadata} schemas. */
public record PageResponse<T>(List<T> items, PageMetadata page) {

    public record PageMetadata(
            int number, int size, long totalElements, int totalPages, boolean first, boolean last) {
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                new PageMetadata(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.isFirst(),
                        page.isLast()));
    }
}
