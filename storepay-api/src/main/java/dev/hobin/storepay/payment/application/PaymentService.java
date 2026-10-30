package dev.hobin.storepay.payment.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.idempotency.IdempotencyService;
import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderRepository;
import dev.hobin.storepay.payment.application.dto.PaymentResponse;
import dev.hobin.storepay.payment.domain.Payment;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.payment.domain.PaymentRepository;
import dev.hobin.storepay.payment.domain.PgClient;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final PgClient pgClient;

    /**
     * 결제 승인.
     *
     * <p>POS 단말은 네트워크가 끊기면 같은 요청을 다시 보낸다. 사용자가 결제 버튼을 두 번 누르기도 한다.
     * 그래서 {@code Idempotency-Key} 로 선점한 뒤에만 결제사에 승인을 건다.
     *
     * <p>승인 실패는 예외로 던지지 않고 결제 건을 FAILED 로 남긴다.
     * 예외로 롤백해 버리면 "왜 실패했는지"가 사라져 고객 문의에 답할 수 없다.
     */
    @Transactional
    public PaymentResponse approve(String idempotencyKey, String orderKey, PaymentMethod method) {
        String fingerprint = IdempotencyService.fingerprint(orderKey, method.name());
        IdempotencyService.Reservation reservation = idempotencyService.reserve(idempotencyKey, fingerprint);

        if (reservation.replayed()) {
            log.info("멱등 재요청: idempotencyKey={}, paymentKey={}", idempotencyKey, reservation.resourceKey());
            return getByKey(reservation.resourceKey());
        }

        try {
            Order order = orderRepository.findByOrderKey(orderKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            if (paymentRepository.existsByOrder(order)) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
            }

            Payment payment = paymentRepository.save(Payment.ready(order, method));

            PgClient.ApproveResult result = pgClient.approve(new PgClient.ApproveCommand(
                    payment.getPaymentKey(), order.getOrderKey(), method, payment.getTotalAmount()));

            if (result.approved()) {
                payment.approve(result.pgTransactionId(), result.approvedAt());
            } else {
                payment.fail(result.failureReason());
            }

            idempotencyService.complete(idempotencyKey, payment.getPaymentKey());
            return PaymentResponse.from(payment);

        } catch (RuntimeException e) {
            // 처리에 실패했으면 선점을 풀어 같은 키로 다시 시도할 수 있게 한다.
            // 풀지 않으면 클라이언트가 영원히 IDEMPOTENCY_IN_PROGRESS 만 받는다.
            idempotencyService.release(idempotencyKey);
            throw e;
        }
    }

    @Transactional
    public PaymentResponse cancel(String paymentKey, BigDecimal amount, String reason) {
        Payment payment = getEntityByKey(paymentKey);

        // 결제사를 부르기 전에 도메인 규칙부터 검사한다. 취소 가능 금액을 넘는 요청을
        // 결제사에 먼저 보내면, 거기서는 성공하고 우리 쪽에서 거부해 장부가 어긋난다.
        BigDecimal cancelable = payment.cancelableAmount();
        if (amount.compareTo(cancelable) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT,
                    "취소 가능 금액을 초과했습니다. 요청: %s, 가능: %s".formatted(amount, cancelable));
        }

        PgClient.CancelResult result = pgClient.cancel(new PgClient.CancelCommand(
                payment.getPaymentKey(), payment.getPgTransactionId(), amount, reason));

        payment.cancel(amount, reason, result.pgCancelId(), result.canceledAt());
        return PaymentResponse.from(payment);
    }

    public PaymentResponse getByKey(String paymentKey) {
        return PaymentResponse.from(getEntityByKey(paymentKey));
    }

    private Payment getEntityByKey(String paymentKey) {
        return paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
