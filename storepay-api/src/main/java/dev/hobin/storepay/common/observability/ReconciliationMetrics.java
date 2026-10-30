package dev.hobin.storepay.common.observability;

import dev.hobin.storepay.settlement.domain.MismatchType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 대사 지표.
 *
 * <p>불일치는 <b>유형별로</b> 센다. 합쳐 놓으면 "오늘 3건"이라는 숫자만 남는데,
 * 타이밍 차이로 생겼다 사라지는 {@code AMOUNT_MISMATCH} 3건과
 * 손님 카드가 긁혔는데 우리 매출이 없는 {@code MISSING_IN_INTERNAL} 3건은
 * 대응의 급박함이 전혀 다르다. 알림 기준도 유형별로 달라야 한다.
 */
@Component
public class ReconciliationMetrics {

    private static final String MISMATCH = "storepay.reconciliation.mismatch";
    private static final String RUN = "storepay.reconciliation.run";

    private final MeterRegistry registry;

    public ReconciliationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void mismatch(MismatchType type) {
        registry.counter(MISMATCH, "type", type.name()).increment();
    }

    public void completed(int mismatchCount) {
        registry.counter(RUN, "outcome", mismatchCount == 0 ? "clean" : "dirty").increment();
    }

    /** 결제사를 못 불러 끝내지 못한 경우. "불일치 0건"과 반드시 구분돼야 한다. */
    public void failed() {
        registry.counter(RUN, "outcome", "failed").increment();
    }
}
