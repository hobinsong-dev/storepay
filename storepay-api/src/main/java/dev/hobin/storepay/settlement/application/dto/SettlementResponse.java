package dev.hobin.storepay.settlement.application.dto;

import dev.hobin.storepay.settlement.domain.Settlement;
import dev.hobin.storepay.settlement.domain.SettlementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SettlementResponse(
        String settlementKey,
        String merchantKey,
        String merchantName,
        LocalDate businessDate,
        SettlementStatus status,
        int transactionCount,
        BigDecimal approvedAmount,
        BigDecimal canceledAmount,
        BigDecimal feeAmount,
        BigDecimal payoutAmount,
        Instant paidAt) {

    /** 트랜잭션 안에서만 호출해야 한다. merchant 가 지연 로딩이다. */
    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getSettlementKey(),
                settlement.getMerchant().getMerchantKey(),
                settlement.getMerchant().getName(),
                settlement.getBusinessDate(),
                settlement.getStatus(),
                settlement.getTransactionCount(),
                settlement.getApprovedAmount(),
                settlement.getCanceledAmount(),
                settlement.getFeeAmount(),
                settlement.getPayoutAmount(),
                settlement.getPaidAt());
    }
}
