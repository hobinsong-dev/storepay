package dev.hobin.storepay.settlement.application.dto;

import dev.hobin.storepay.settlement.domain.MismatchType;
import dev.hobin.storepay.settlement.domain.ReconciliationMismatch;
import dev.hobin.storepay.settlement.domain.ReconciliationRun;
import dev.hobin.storepay.settlement.domain.ReconciliationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReconciliationResponse(
        String runKey,
        LocalDate businessDate,
        ReconciliationStatus status,
        int internalCount,
        int pgCount,
        int matchedCount,
        int mismatchCount,
        Instant startedAt,
        Instant finishedAt,
        String failureReason,
        List<MismatchDetail> mismatches) {

    public static ReconciliationResponse from(ReconciliationRun run) {
        return new ReconciliationResponse(
                run.getRunKey(),
                run.getBusinessDate(),
                run.getStatus(),
                run.getInternalCount(),
                run.getPgCount(),
                run.getMatchedCount(),
                run.getMismatchCount(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getFailureReason(),
                run.getMismatches().stream().map(MismatchDetail::from).toList());
    }

    public record MismatchDetail(
            MismatchType type,
            String paymentKey,
            String pgTransactionId,
            BigDecimal internalAmount,
            BigDecimal pgAmount,
            String internalStatus,
            String pgStatus,
            String detail) {

        static MismatchDetail from(ReconciliationMismatch mismatch) {
            return new MismatchDetail(
                    mismatch.getType(),
                    mismatch.getPaymentKey(),
                    mismatch.getPgTransactionId(),
                    mismatch.getInternalAmount(),
                    mismatch.getPgAmount(),
                    mismatch.getInternalStatus(),
                    mismatch.getPgStatus(),
                    mismatch.getDetail());
        }
    }
}
