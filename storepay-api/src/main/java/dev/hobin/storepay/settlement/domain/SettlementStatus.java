package dev.hobin.storepay.settlement.domain;

public enum SettlementStatus {
    /** 집계만 끝난 상태. 아직 돈이 나가지 않았다. */
    PENDING,
    /** 지급 완료. */
    PAID
}
