package dev.hobin.storepay.order.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {

    /** 주문서가 만들어졌고 아직 결제되지 않음. */
    CREATED,
    /** 결제 승인 완료. */
    PAID,
    /** 전액 취소. */
    CANCELED;

    private static final Set<OrderStatus> CANCELABLE = EnumSet.of(CREATED, PAID);

    public boolean isCancelable() {
        return CANCELABLE.contains(this);
    }
}
