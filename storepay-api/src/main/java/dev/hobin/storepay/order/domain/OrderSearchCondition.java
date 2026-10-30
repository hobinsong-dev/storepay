package dev.hobin.storepay.order.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 주문 검색 조건. 모든 필드가 선택이고, 지정된 것만 WHERE 절에 붙는다.
 */
public record OrderSearchCondition(
        String storeKey,
        OrderStatus status,
        Instant orderedFrom,
        Instant orderedTo,
        BigDecimal minAmount,
        BigDecimal maxAmount) {
}
