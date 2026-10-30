package dev.hobin.storepay.payment.domain;

import dev.hobin.storepay.common.entity.BaseTimeEntity;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.support.PublicKeyGenerator;
import dev.hobin.storepay.order.domain.Order;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 애그리게잇 루트.
 *
 * <p>금액 계산과 상태 전이 규칙을 전부 이 안에 가둔다. 서비스가 상태를 직접 바꿀 수 있게 열어두면
 * 호출 경로가 늘어날 때마다 "이 경로에서는 검사를 빠뜨렸다" 같은 구멍이 생긴다.
 */
@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_key", nullable = false, unique = true, length = 40)
    private String paymentKey;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "canceled_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal canceledAmount;

    /** PG 가 발급한 거래 번호. 대사(對査)와 문의 대응의 유일한 연결고리라 승인 즉시 저장한다. */
    @Column(name = "pg_transaction_id", length = 64)
    private String pgTransactionId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<PaymentCancel> cancels = new ArrayList<>();

    /**
     * 같은 결제 건에 승인과 취소가 동시에 들어오는 경우를 막는다.
     * PG 웹훅과 POS 단말 요청이 겹치면 실제로 발생한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Payment(Order order, PaymentMethod method) {
        this.paymentKey = PublicKeyGenerator.generate("pay");
        this.order = order;
        this.method = method;
        this.status = PaymentStatus.READY;
        this.totalAmount = order.getTotalAmount();
        this.canceledAmount = BigDecimal.ZERO;
    }

    /**
     * 결제 건을 만든다. 금액은 주문에서 가져오며 인자로 받지 않는다.
     * 호출자가 금액을 넘길 수 있게 하면 주문 총액과 결제 금액이 갈라질 길이 생긴다.
     */
    public static Payment ready(Order order, PaymentMethod method) {
        return new Payment(order, method);
    }

    public void approve(String pgTransactionId, Instant approvedAt) {
        if (this.status != PaymentStatus.READY) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS,
                    "승인 가능한 상태가 아닙니다. 현재 상태: %s".formatted(this.status));
        }
        this.status = PaymentStatus.APPROVED;
        this.pgTransactionId = pgTransactionId;
        this.approvedAt = approvedAt;
        this.order.markPaid();
    }

    public void fail(String reason) {
        if (this.status != PaymentStatus.READY) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS,
                    "실패 처리할 수 있는 상태가 아닙니다. 현재 상태: %s".formatted(this.status));
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * 부분 또는 전액 취소.
     *
     * <p>취소 가능 잔액을 넘는 요청은 거부한다. 이 검사가 없으면 1,000원 결제에 500원 취소를
     * 세 번 넣어 1,500원을 환불해 줄 수 있다. 금액 비교는 {@code compareTo} 로 한다 —
     * BigDecimal 의 {@code equals} 는 scale 까지 보기 때문에 1000 과 1000.00 을 다르게 판단한다.
     */
    public PaymentCancel cancel(BigDecimal amount, String reason, String pgCancelId, Instant canceledAt) {
        if (!this.status.isCancelable()) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS,
                    "취소 가능한 상태가 아닙니다. 현재 상태: %s".formatted(this.status));
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT, "취소 금액은 0보다 커야 합니다.");
        }
        if (amount.compareTo(cancelableAmount()) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT,
                    "취소 가능 금액을 초과했습니다. 요청: %s, 가능: %s".formatted(amount, cancelableAmount()));
        }

        PaymentCancel cancel = new PaymentCancel(this, amount, reason, pgCancelId, canceledAt);
        this.cancels.add(cancel);
        this.canceledAmount = this.canceledAmount.add(amount);
        this.status = cancelableAmount().compareTo(BigDecimal.ZERO) == 0
                ? PaymentStatus.CANCELED
                : PaymentStatus.PARTIAL_CANCELED;

        if (this.status == PaymentStatus.CANCELED) {
            this.order.cancel();
        }
        return cancel;
    }

    public BigDecimal cancelableAmount() {
        return this.totalAmount.subtract(this.canceledAmount);
    }

    public List<PaymentCancel> getCancels() {
        return Collections.unmodifiableList(this.cancels);
    }
}
