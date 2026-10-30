package dev.hobin.storepay.settlement.domain;

import dev.hobin.storepay.common.entity.BaseTimeEntity;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.support.PublicKeyGenerator;
import dev.hobin.storepay.merchant.domain.Merchant;
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
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가맹점 하루치 정산.
 *
 * <p>지급액은 {@code 승인 - 취소 - 수수료} 이며 <b>음수가 될 수 있다</b>.
 * 어제 판 것을 오늘 취소하면 오늘 정산은 마이너스다. 0 으로 깎아 버리면 그만큼을
 * 영원히 회수하지 못하므로, 음수를 그대로 남기고 다음 지급에서 상계한다.
 */
@Getter
@Entity
@Table(name = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_key", nullable = false, unique = true, length = 40)
    private String settlementKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    @Column(name = "approved_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "canceled_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal canceledAmount;

    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "payout_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal payoutAmount;

    @Column(name = "paid_at")
    private Instant paidAt;

    private Settlement(Merchant merchant, LocalDate businessDate, int transactionCount,
                       BigDecimal approvedAmount, BigDecimal canceledAmount, BigDecimal feeAmount) {
        this.settlementKey = PublicKeyGenerator.generate("stl");
        this.merchant = merchant;
        this.businessDate = businessDate;
        this.status = SettlementStatus.PENDING;
        this.transactionCount = transactionCount;
        this.approvedAmount = approvedAmount;
        this.canceledAmount = canceledAmount;
        this.feeAmount = feeAmount;
        this.payoutAmount = approvedAmount.subtract(canceledAmount).subtract(feeAmount);
    }

    public static Settlement of(Merchant merchant, LocalDate businessDate, int transactionCount,
                                BigDecimal approvedAmount, BigDecimal canceledAmount, BigDecimal feeAmount) {
        return new Settlement(merchant, businessDate, transactionCount,
                approvedAmount, canceledAmount, feeAmount);
    }

    /**
     * 지급 완료 처리.
     *
     * <p>이미 지급된 정산은 다시 지급할 수 없다. 재실행 가능한 배치에서 이 검사가 없으면
     * 운영자가 같은 날짜를 두 번 돌리는 것만으로 돈이 두 번 나간다.
     */
    public void markPaid(Instant paidAt) {
        if (this.status == SettlementStatus.PAID) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ALREADY_PAID,
                    "이미 지급된 정산입니다. settlementKey=%s".formatted(this.settlementKey));
        }
        this.status = SettlementStatus.PAID;
        this.paidAt = paidAt;
    }
}
