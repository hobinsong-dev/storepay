package dev.hobin.storepay.payment.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.idempotency.IdempotencyService;
import dev.hobin.storepay.common.observability.PaymentMetrics;
import dev.hobin.storepay.payment.application.dto.PaymentResponse;
import dev.hobin.storepay.payment.domain.Payment;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.payment.domain.PaymentRepository;
import dev.hobin.storepay.payment.domain.PgClient;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentApprover paymentApprover;
    private final PgClient pgClient;
    private final PaymentMetrics metrics;

    /**
     * 결제 승인.
     *
     * <p>POS 단말은 네트워크가 끊기면 같은 요청을 다시 보낸다. 사용자가 결제 버튼을 두 번 누르기도 한다.
     * 그래서 {@code Idempotency-Key} 로 선점한 뒤에만 결제사에 승인을 건다.
     *
     * <p>이 메서드 자체는 트랜잭션을 열지 않는다({@code NOT_SUPPORTED}). 선점은 반드시 독립 트랜잭션으로
     * 먼저 커밋돼야 하는데, 바깥 트랜잭션을 깔고 그 안에서 {@code REQUIRES_NEW} 를 열면
     * 요청 하나가 커넥션을 <b>두 개</b> 잡는다. 동시 요청이 풀 크기를 넘는 순간, 모든 스레드가
     * 바깥 커넥션을 쥔 채 안쪽 커넥션을 기다리며 풀이 교착된다. 실제로 16스레드 동시 승인 테스트에서
     * 10건이 "Could not open JPA EntityManager for transaction" 으로 죽었다.
     * 세 단계를 차례로 열면 어느 시점에도 커넥션은 하나다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse approve(String idempotencyKey, String orderKey, PaymentMethod method) {
        String fingerprint = IdempotencyService.fingerprint(orderKey, method.name());

        // 선점은 try 바깥에 둔다. 안에 두면 선점 경쟁에서 진 쪽이 catch 로 들어가
        // release() 로 <b>이긴 쪽의</b> 선점을 지워버린다. 그러면 지운 틈으로 다른 요청이 들어와
        // 같은 주문에 결제를 또 만들고, 이긴 쪽의 complete() 는 지워진 행을 찾지 못해 조용히 넘어간다.
        IdempotencyService.Reservation reservation = idempotencyService.reserve(idempotencyKey, fingerprint);

        if (reservation.replayed()) {
            log.info("멱등 재요청: idempotencyKey={}, paymentKey={}", idempotencyKey, reservation.resourceKey());
            return getByKey(reservation.resourceKey());
        }

        PaymentResponse response;
        try {
            response = paymentApprover.execute(orderKey, method);
        } catch (RuntimeException e) {
            // 처리에 실패했으면 선점을 풀어 같은 키로 다시 시도할 수 있게 한다.
            // 풀지 않으면 클라이언트가 영원히 IDEMPOTENCY_IN_PROGRESS 만 받는다.
            idempotencyService.release(idempotencyKey);
            metrics.approveFailed(method, errorCodeOf(e));
            log.warn("승인 실패: orderKey={}, error={}", orderKey, errorCodeOf(e), e);
            throw e;
        }

        idempotencyService.complete(idempotencyKey, response.paymentKey());
        return response;
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

        PgClient.CancelResult result = metrics.recordPgCall("cancel",
                () -> pgClient.cancel(new PgClient.CancelCommand(
                        payment.getPaymentKey(), payment.getPgTransactionId(), amount, reason)));

        payment.cancel(amount, reason, result.pgCancelId(), result.canceledAt());
        metrics.canceled(payment.getMethod());
        log.info("취소 완료: paymentKey={}, amount={}, 잔액={}",
                paymentKey, amount, payment.cancelableAmount());
        return PaymentResponse.from(payment);
    }

    public PaymentResponse getByKey(String paymentKey) {
        return PaymentResponse.from(getEntityByKey(paymentKey));
    }

    private static String errorCodeOf(RuntimeException e) {
        return e instanceof BusinessException business
                ? business.getErrorCode().getCode()
                : ErrorCode.INTERNAL_ERROR.getCode();
    }

    private Payment getEntityByKey(String paymentKey) {
        return paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
