package dev.hobin.storepay.payment.application.dto;

import dev.hobin.storepay.payment.domain.Payment;
import dev.hobin.storepay.payment.domain.PaymentCancel;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentResponse(
        String paymentKey,
        String orderKey,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal totalAmount,
        BigDecimal canceledAmount,
        BigDecimal cancelableAmount,
        String pgTransactionId,
        Instant approvedAt,
        String failureReason,
        List<Cancel> cancels) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentKey(),
                payment.getOrder().getOrderKey(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getTotalAmount(),
                payment.getCanceledAmount(),
                payment.cancelableAmount(),
                payment.getPgTransactionId(),
                payment.getApprovedAt(),
                payment.getFailureReason(),
                payment.getCancels().stream().map(Cancel::from).toList());
    }

    public record Cancel(BigDecimal amount, String reason, String pgCancelId, Instant canceledAt) {

        static Cancel from(PaymentCancel cancel) {
            return new Cancel(cancel.getAmount(), cancel.getReason(),
                    cancel.getPgCancelId(), cancel.getCanceledAt());
        }
    }
}
