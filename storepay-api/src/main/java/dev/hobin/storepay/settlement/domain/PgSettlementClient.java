package dev.hobin.storepay.settlement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 결제사 거래내역 조회 포트.
 *
 * <p>대사의 상대편 장부다. 실제 운영에서는 결제사가 내려주는 일일 정산 파일(CSV/EDI)을 읽는
 * 어댑터가 붙지만, 포트 모양은 같다 — 영업일 하나를 주면 그날 거래 목록을 돌려준다.
 */
public interface PgSettlementClient {

    List<PgTransaction> fetchTransactions(LocalDate businessDate);

    /**
     * 결제사가 보는 거래 한 건.
     *
     * @param paymentKey     우리가 승인 요청에 실어 보낸 가맹점 주문번호. 대사의 조인 키다.
     * @param amount         결제사 기준 <b>잔여</b> 금액 (취소분이 이미 차감된 값)
     * @param status         결제사 기준 상태 문자열. 우리 enum 과 이름이 다를 수 있어 문자열로 받는다.
     */
    record PgTransaction(
            String paymentKey,
            String pgTransactionId,
            BigDecimal amount,
            String status) {
    }
}
