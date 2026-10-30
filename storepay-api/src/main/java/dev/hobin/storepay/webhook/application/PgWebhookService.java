package dev.hobin.storepay.webhook.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.observability.RequestContext;
import dev.hobin.storepay.payment.domain.Payment;
import dev.hobin.storepay.payment.domain.PaymentRepository;
import dev.hobin.storepay.payment.domain.PaymentStatus;
import dev.hobin.storepay.webhook.domain.WebhookEvent;
import dev.hobin.storepay.webhook.domain.WebhookEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgWebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 결제사 웹훅 처리.
     *
     * <p>결제사는 at-least-once 로 보낸다. 같은 이벤트가 두 번 와도 결과가 같아야 한다.
     * 이벤트 ID 로 먼저 걸러내고, 통과한 것만 처리한다.
     *
     * <p>지금 처리하는 핵심 시나리오는 <b>유실된 승인 응답의 복구</b>다.
     * 승인 요청은 결제사에 닿았는데 응답이 네트워크에서 사라지면 우리 쪽 결제는 READY 로 남는다.
     * 돈은 빠져나갔는데 장부에는 미결제인 상태다. 웹훅이 그 간극을 메운다.
     */
    @Transactional
    public Result handle(String eventId, String rawBody) {
        if (webhookEventRepository.existsByEventId(eventId)) {
            log.info("중복 웹훅 무시: eventId={}", eventId);
            return Result.DUPLICATE;
        }

        Payload payload = parse(rawBody);
        // 웹훅은 POS 요청과 별개의 HTTP 요청으로 들어온다. 결제 키를 MDC 에 올려야
        // "승인은 됐는데 웹훅이 뭘 했는지"를 같은 키로 이어서 볼 수 있다.
        MDC.put(RequestContext.PAYMENT_KEY, payload.paymentKey());

        try {
            webhookEventRepository.saveAndFlush(
                    new WebhookEvent(eventId, payload.eventType(), rawBody));
        } catch (DataIntegrityViolationException e) {
            // 같은 이벤트가 동시에 두 번 들어왔다. 먼저 들어온 쪽이 처리한다.
            log.info("중복 웹훅 무시 (동시 수신): eventId={}", eventId);
            return Result.DUPLICATE;
        }

        return switch (payload.eventType()) {
            case "PAYMENT_APPROVED" -> reconcileApproval(payload);
            default -> {
                log.info("처리 대상이 아닌 이벤트: eventType={}", payload.eventType());
                yield Result.IGNORED;
            }
        };
    }

    private Result reconcileApproval(Payload payload) {
        return paymentRepository.findByPaymentKey(payload.paymentKey())
                .map(payment -> {
                    if (payment.getStatus() != PaymentStatus.READY) {
                        // 동기 응답으로 이미 처리됐다. 정상적인 경우이며 아무것도 하지 않는다.
                        // 그래도 한 줄은 남긴다 — 웹훅이 왔다는 사실 자체가 안 보이면
                        // "결제사가 웹훅을 안 쏜다"는 문의에 로그로 답할 수 없다.
                        log.info("웹훅 수신, 이미 처리된 결제라 건너뜀: status={}", payment.getStatus());
                        return Result.ALREADY_SETTLED;
                    }
                    payment.approve(payload.transactionId(), payload.occurredAt());
                    log.info("웹훅으로 승인 복구: transactionId={}", payload.transactionId());
                    return Result.RECONCILED;
                })
                .orElseThrow(() -> {
                    // 승인 요청을 보낸 트랜잭션이 아직 커밋되지 않았을 때 웹훅이 먼저 도착할 수 있다.
                    // 여기서 조용히 성공(2xx)으로 넘기면 방금 저장한 중복 방지 기록 때문에
                    // 결제사의 재전송까지 DUPLICATE 로 걸러져, 결제가 영원히 READY 로 남는다.
                    // 예외로 롤백해 기록을 지우고 비(非)2xx 를 돌려줘 재전송을 유도한다.
                    log.warn("아직 보이지 않는 결제 건의 웹훅. 재전송 유도");
                    return new BusinessException(ErrorCode.WEBHOOK_PAYMENT_NOT_READY,
                            "결제 건을 찾을 수 없습니다: %s".formatted(payload.paymentKey()));
                });
    }

    private Payload parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, Payload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("웹훅 본문을 해석할 수 없습니다.", e);
        }
    }

    public enum Result {
        /** READY 로 남아 있던 결제를 웹훅으로 승인 처리했다. */
        RECONCILED,
        /** 동기 응답으로 이미 처리된 건. 정상이며 아무것도 하지 않는다. */
        ALREADY_SETTLED,
        /** 같은 이벤트를 이미 처리했다. */
        DUPLICATE,
        /** 우리가 다루지 않는 이벤트 종류. */
        IGNORED
    }

    record Payload(String eventType, String paymentKey, String transactionId,
                   BigDecimal amount, Instant occurredAt) {
    }
}
