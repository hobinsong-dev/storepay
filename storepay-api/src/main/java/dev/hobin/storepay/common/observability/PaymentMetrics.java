package dev.hobin.storepay.common.observability;

import dev.hobin.storepay.payment.domain.PaymentMethod;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 결제 지표.
 *
 * <p>태그에 {@code paymentKey} 나 {@code orderKey} 같은 값은 절대 넣지 않는다. 메트릭은
 * 태그 조합마다 시계열이 하나씩 생기므로, 건별로 유일한 값을 태그에 넣으면 거래 수만큼
 * 시계열이 생겨 수집기가 먼저 죽는다. 건별 추적은 로그(MDC)의 일이고, 메트릭은 집계의 일이다.
 *
 * <p>결제사 호출은 성공·실패·타임아웃을 <b>나눠서</b> 잰다. 하나로 합치면
 * 빠르게 떨어지는 실패가 평균을 끌어내려 "응답시간은 정상"으로 보인다.
 */
@Component
public class PaymentMetrics {

    private static final String APPROVE = "storepay.payment.approve";
    private static final String CANCEL = "storepay.payment.cancel";
    private static final String PG_CALL = "storepay.pg.call";

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void approveSucceeded(PaymentMethod method) {
        counter(APPROVE, "method", method.name(), "outcome", "approved").increment();
    }

    /** 결제사가 정상 응답으로 <b>거절</b>한 경우. 장애가 아니라 한도 초과 같은 정상 흐름이다. */
    public void approveDeclined(PaymentMethod method) {
        counter(APPROVE, "method", method.name(), "outcome", "declined").increment();
    }

    /** 우리 쪽 규칙이나 장애로 승인에 이르지 못한 경우. 거절과 구분해야 알림 기준이 선다. */
    public void approveFailed(PaymentMethod method, String errorCode) {
        counter(APPROVE, "method", method.name(), "outcome", "failed", "error", errorCode).increment();
    }

    public void canceled(PaymentMethod method) {
        counter(CANCEL, "method", method.name(), "outcome", "canceled").increment();
    }

    /** 결제사 호출을 재고 성공/실패를 태그로 나눈다. */
    public <T> T recordPgCall(String operation, Supplier<T> call) {
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            return call.get();
        } catch (RuntimeException e) {
            outcome = classify(e);
            throw e;
        } finally {
            Timer.builder(PG_CALL)
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    // 히스토그램은 버킷 하나하나가 시계열이다. 범위를 안 정하면 기본값이
                    // 1ms~30s 를 덮어 태그 조합마다 70개 넘게 생긴다. 결제사 응답은 수십 ms~읽기
                    // 타임아웃 사이에 몰려 있으므로 그 구간만 남기고 버킷 수를 줄인다.
                    .minimumExpectedValue(Duration.ofMillis(20))
                    .maximumExpectedValue(Duration.ofSeconds(10))
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 타임아웃은 따로 센다. 결제에서 타임아웃은 "실패"가 아니라 <b>결과를 모르는 상태</b>라,
     * 같은 실패로 묶으면 미결 거래가 늘고 있다는 신호를 놓친다.
     */
    private String classify(RuntimeException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String name = cause.getClass().getSimpleName();
            if (name.contains("Timeout") || name.contains("ResourceAccess")) {
                return "timeout";
            }
        }
        return "error";
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }
}
