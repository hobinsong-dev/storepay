package dev.hobin.storepay.settlement.domain;

/** 대사 불일치 유형. 유형마다 원인과 대응이 다르므로 "틀렸다"로 뭉뚱그리지 않는다. */
public enum MismatchType {

    /** 우리는 승인으로 아는데 결제사 내역에 없다. 승인 응답은 받았으나 결제사에서 취소/실효된 경우. */
    MISSING_IN_PG,

    /** 결제사에는 있는데 우리에게 없다. 승인 응답과 웹훅을 모두 놓친 경우로, 가장 위험하다. */
    MISSING_IN_INTERNAL,

    /** 금액이 다르다. 부분취소 반영 시점 차이거나 실제 사고다. */
    AMOUNT_MISMATCH,

    /** 상태가 다르다. 취소가 한쪽에만 반영됐다. */
    STATUS_MISMATCH
}
