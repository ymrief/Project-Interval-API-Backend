package com.interval.apibackend.common.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Format data berhalaman: { items, page, size, totalItems, totalPages }.
 * page dimulai dari 0 (sama seperti Spring Data).
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /** Memetakan entity -> DTO sekaligus. Contoh: PageResponse.from(page, mapper::toResponse) */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> items = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
