package dev.hobin.storepay.settlement.domain;

import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 대사·정산 집계 조회 포트.
 *
 * <p>구현은 {@code infrastructure} 쪽 QueryDSL 어댑터다. Spring Data 프래그먼트로 붙이지 않는 이유는
 * {@code OrderQueryRepository} 와 같다 — 구현 클래스가 인터페이스와 같은 패키지에 있어야만 발견되어
 * QueryDSL 의존이 도메인 패키지로 끌려 들어온다.
 */
public interface SettlementQueryRepository {

    /** 영업일 창([from, to)) 안에 <b>승인된</b> 결제. 대사와 정산이 함께 쓴다. */
    List<ApprovedPayment> findApprovedBetween(Instant from, Instant to);

    /** 영업일 창 안에 <b>취소된</b> 금액. 승인일이 아니라 취소일에 잡힌다. */
    List<CanceledAmount> findCanceledBetween(Instant from, Instant to);

    record ApprovedPayment(
            Long merchantId,
            String paymentKey,
            String pgTransactionId,
            PaymentMethod method,
            PaymentStatus status,
            BigDecimal totalAmount,
            BigDecimal canceledAmount) {

        /** 결제사가 내려주는 값과 같은 기준의 잔여 금액. */
        public BigDecimal remainingAmount() {
            return totalAmount.subtract(canceledAmount);
        }
    }

    record CanceledAmount(Long merchantId, PaymentMethod method, BigDecimal amount) {
    }
}
