package dev.hobin.storepay.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 취소 이력.
 *
 * <p>결제 한 건에 부분취소가 여러 번 일어날 수 있어 별도 테이블로 남긴다.
 * 결제 엔티티의 누적 취소금액만 들고 있으면 "언제 얼마를 왜 취소했는지"를 복원할 수 없고,
 * 정산 대사(對査)와 고객 문의 대응이 불가능해진다.
 */
@Getter
@Entity
@Table(name = "payment_cancel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "pg_cancel_id", length = 64)
    private String pgCancelId;

    @Column(name = "canceled_at", nullable = false)
    private Instant canceledAt;

    PaymentCancel(Payment payment, BigDecimal amount, String reason, String pgCancelId, Instant canceledAt) {
        this.payment = payment;
        this.amount = amount;
        this.reason = reason;
        this.pgCancelId = pgCancelId;
        this.canceledAt = canceledAt;
    }
}
