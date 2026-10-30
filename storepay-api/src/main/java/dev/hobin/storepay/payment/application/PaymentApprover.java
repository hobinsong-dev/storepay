package dev.hobin.storepay.payment.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderRepository;
import dev.hobin.storepay.payment.application.dto.PaymentResponse;
import dev.hobin.storepay.payment.domain.Payment;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.payment.domain.PaymentRepository;
import dev.hobin.storepay.payment.domain.PgClient;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 승인의 트랜잭션 단위.
 *
 * <p>{@link PaymentService} 와 한 클래스로 두면 안 된다. 승인은 "멱등키 선점 → 결제 처리 → 선점 완료"
 * 세 트랜잭션을 <b>차례대로</b> 열어야 하는데, 한 클래스 안에서 호출하면 프록시를 타지 않아
 * 트랜잭션이 분리되지 않는다. 별도 빈으로 떼어 두면 프록시를 반드시 거치게 된다.
 */
@Component
@RequiredArgsConstructor
class PaymentApprover {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PgClient pgClient;

    /**
     * 승인 실패는 예외로 던지지 않고 결제 건을 FAILED 로 남긴다.
     * 예외로 롤백해 버리면 "왜 실패했는지"가 사라져 고객 문의에 답할 수 없다.
     */
    @Transactional
    public PaymentResponse execute(String orderKey, PaymentMethod method) {
        Order order = orderRepository.findByOrderKey(orderKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (paymentRepository.existsByOrder(order)) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        // existsByOrder 를 통과한 동시 요청 둘이 여기까지 올 수 있다. 최종 판정은
        // uk_payment_order 유니크 제약이 하고, 진 쪽은 여기서 409 로 바뀐다.
        // PK 가 IDENTITY 라 save() 시점에 INSERT 가 바로 나가므로 제약 위반도 여기서 터진다.
        // (커밋 시점에 터진다면 이 try 로는 못 잡고 500 이 나간다.)
        Payment payment;
        try {
            payment = paymentRepository.save(Payment.ready(order, method));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        PgClient.ApproveResult result = pgClient.approve(new PgClient.ApproveCommand(
                payment.getPaymentKey(), order.getOrderKey(), method, payment.getTotalAmount()));

        if (result.approved()) {
            payment.approve(result.pgTransactionId(), result.approvedAt());
        } else {
            payment.fail(result.failureReason());
        }

        // 응답 DTO 는 트랜잭션 안에서 만든다. 밖에서 만들면 지연 로딩이 터진다.
        return PaymentResponse.from(payment);
    }
}
