package dev.hobin.storepay.payment.presentation;

import dev.hobin.storepay.common.response.ApiResponse;
import dev.hobin.storepay.payment.application.PaymentService;
import dev.hobin.storepay.payment.application.dto.PaymentResponse;
import dev.hobin.storepay.payment.presentation.dto.PaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "결제 승인 / 취소 / 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "결제 승인",
            description = """
                    금액은 받지 않는다. 서버가 주문 총액을 그대로 사용한다.
                    Idempotency-Key 헤더가 필수이며, 같은 키로 다시 호출하면 처음 결과를 그대로 돌려준다.
                    같은 키에 다른 내용을 보내면 409 로 거부한다.""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> approve(
            @Parameter(description = "요청 고유 키 (재시도 시 동일 값 사용)", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest.Approve request) {
        return ApiResponse.ok(
                paymentService.approve(idempotencyKey, request.orderKey(), request.method()));
    }

    @Operation(summary = "결제 취소", description = "부분 취소를 지원한다. 취소 가능 잔액을 넘으면 거부한다.")
    @PostMapping("/{paymentKey}/cancel")
    public ApiResponse<PaymentResponse> cancel(
            @PathVariable String paymentKey,
            @Valid @RequestBody PaymentRequest.Cancel request) {
        return ApiResponse.ok(paymentService.cancel(paymentKey, request.amount(), request.reason()));
    }

    @Operation(summary = "결제 단건 조회")
    @GetMapping("/{paymentKey}")
    public ApiResponse<PaymentResponse> get(@PathVariable String paymentKey) {
        return ApiResponse.ok(paymentService.getByKey(paymentKey));
    }
}
