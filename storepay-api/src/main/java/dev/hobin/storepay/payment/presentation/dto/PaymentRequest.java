package dev.hobin.storepay.payment.presentation.dto;

import dev.hobin.storepay.payment.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class PaymentRequest {

    private PaymentRequest() {
    }

    /** 승인 요청에 금액이 없다. 서버가 주문 총액을 그대로 쓴다. */
    public record Approve(
            @NotBlank(message = "주문 키는 필수입니다.")
            String orderKey,

            @NotNull(message = "결제 수단은 필수입니다.")
            PaymentMethod method) {
    }

    public record Cancel(
            @NotNull(message = "취소 금액은 필수입니다.")
            @DecimalMin(value = "0.01", message = "취소 금액은 0보다 커야 합니다.")
            @Digits(integer = 13, fraction = 2, message = "취소 금액 형식이 올바르지 않습니다.")
            BigDecimal amount,

            @Size(max = 200, message = "취소 사유는 200자를 넘을 수 없습니다.")
            String reason) {
    }
}
