package dev.hobin.storepay.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 불일치 한 건.
 *
 * <p>양쪽 값을 <b>둘 다</b> 남긴다. "다르다"만 적어두면 조사할 때 당시 값을 다시 못 구한다
 * (결제사 내역은 보관 기간이 있고, 우리 쪽 값은 그사이 바뀐다).
 */
@Getter
@Entity
@Table(name = "reconciliation_mismatch")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationMismatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private MismatchType type;

    @Column(name = "payment_key", length = 40)
    private String paymentKey;

    @Column(name = "pg_transaction_id", length = 64)
    private String pgTransactionId;

    @Column(name = "internal_amount", precision = 15, scale = 2)
    private BigDecimal internalAmount;

    @Column(name = "pg_amount", precision = 15, scale = 2)
    private BigDecimal pgAmount;

    @Column(name = "internal_status", length = 20)
    private String internalStatus;

    @Column(name = "pg_status", length = 20)
    private String pgStatus;

    @Column(name = "detail", nullable = false, length = 500)
    private String detail;

    private ReconciliationMismatch(ReconciliationRun run, MismatchType type, String detail) {
        this.run = run;
        this.type = type;
        this.detail = detail;
        run.addMismatch(this);
    }

    public static ReconciliationMismatch missingInPg(
            ReconciliationRun run, String paymentKey, String pgTransactionId,
            BigDecimal internalAmount, String internalStatus) {
        ReconciliationMismatch mismatch = new ReconciliationMismatch(run, MismatchType.MISSING_IN_PG,
                "내부에는 %s 로 있으나 결제사 내역에 없습니다.".formatted(internalStatus));
        mismatch.paymentKey = paymentKey;
        mismatch.pgTransactionId = pgTransactionId;
        mismatch.internalAmount = internalAmount;
        mismatch.internalStatus = internalStatus;
        return mismatch;
    }

    public static ReconciliationMismatch missingInInternal(
            ReconciliationRun run, String paymentKey, String pgTransactionId,
            BigDecimal pgAmount, String pgStatus) {
        ReconciliationMismatch mismatch = new ReconciliationMismatch(run, MismatchType.MISSING_IN_INTERNAL,
                "결제사에는 %s 로 있으나 내부에 결제 건이 없습니다.".formatted(pgStatus));
        mismatch.paymentKey = paymentKey;
        mismatch.pgTransactionId = pgTransactionId;
        mismatch.pgAmount = pgAmount;
        mismatch.pgStatus = pgStatus;
        return mismatch;
    }

    public static ReconciliationMismatch amountMismatch(
            ReconciliationRun run, String paymentKey, String pgTransactionId,
            BigDecimal internalAmount, BigDecimal pgAmount) {
        ReconciliationMismatch mismatch = new ReconciliationMismatch(run, MismatchType.AMOUNT_MISMATCH,
                "금액이 다릅니다. 내부: %s, 결제사: %s".formatted(internalAmount, pgAmount));
        mismatch.paymentKey = paymentKey;
        mismatch.pgTransactionId = pgTransactionId;
        mismatch.internalAmount = internalAmount;
        mismatch.pgAmount = pgAmount;
        return mismatch;
    }

    public static ReconciliationMismatch statusMismatch(
            ReconciliationRun run, String paymentKey, String pgTransactionId,
            String internalStatus, String pgStatus) {
        ReconciliationMismatch mismatch = new ReconciliationMismatch(run, MismatchType.STATUS_MISMATCH,
                "상태가 다릅니다. 내부: %s, 결제사: %s".formatted(internalStatus, pgStatus));
        mismatch.paymentKey = paymentKey;
        mismatch.pgTransactionId = pgTransactionId;
        mismatch.internalStatus = internalStatus;
        mismatch.pgStatus = pgStatus;
        return mismatch;
    }
}
