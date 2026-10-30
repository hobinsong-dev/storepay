package dev.hobin.storepay.payment.domain;

public enum PaymentStatus {

    /** 결제 건은 만들었지만 아직 PG 승인 전. */
    READY,
    /** PG 승인 완료. */
    APPROVED,
    /** PG 승인 실패. 재시도하려면 결제 건을 새로 만든다. */
    FAILED,
    /** 일부 금액만 취소됨. 남은 금액은 여전히 승인 상태. */
    PARTIAL_CANCELED,
    /** 전액 취소됨. */
    CANCELED;

    public boolean isCancelable() {
        return this == APPROVED || this == PARTIAL_CANCELED;
    }
}
