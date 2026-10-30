package dev.hobin.storepay.order.presentation.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 페이지 응답.
 *
 * <p>Spring의 Page를 그대로 직렬화하면 Pageable 내부 구조가 그대로 노출되고
 * 스프링 버전이 올라갈 때 응답 스키마가 조용히 바뀐다. 필요한 필드만 직접 고정한다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
