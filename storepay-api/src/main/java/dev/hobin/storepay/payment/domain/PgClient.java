package dev.hobin.storepay.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 결제사 연동 포트.
 *
 * <p>구현은 infrastructure 에 둔다. 도메인은 "승인을 요청하면 거래번호가 돌아온다"까지만 알고,
 * 상대가 HTTP 인지 전문(電文) 인지는 모른다. 덕분에 결제사를 바꾸거나 추가할 때
 * 도메인 규칙은 건드리지 않는다.
 */
public interface PgClient {

    ApproveResult approve(ApproveCommand command);

    CancelResult cancel(CancelCommand command);

    record ApproveCommand(String paymentKey, String orderKey, PaymentMethod method, BigDecimal amount) {
    }

    record ApproveResult(boolean approved, String pgTransactionId, Instant approvedAt, String failureReason) {

        public static ApproveResult approved(String pgTransactionId, Instant approvedAt) {
            return new ApproveResult(true, pgTransactionId, approvedAt, null);
        }

        public static ApproveResult declined(String failureReason) {
            return new ApproveResult(false, null, null, failureReason);
        }
    }

    record CancelCommand(String paymentKey, String pgTransactionId, BigDecimal amount, String reason) {
    }

    record CancelResult(String pgCancelId, Instant canceledAt) {
    }
}
