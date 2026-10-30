package dev.hobin.storepay.settlement.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.observability.RequestContext;
import dev.hobin.storepay.settlement.application.dto.ReconciliationResponse;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 전일 대사 · 정산 배치.
 *
 * <p>대사가 깨끗할 때만 정산까지 간다. 불일치가 있으면 <b>멈추고 사람을 기다린다</b> —
 * 어긋난 숫자로 지급을 걸면 회수에 가맹점 동의가 필요하고, 그사이 돈은 이미 쓰였다.
 *
 * <p>인스턴스가 여러 대면 이 배치는 동시에 여러 번 돈다. 지금은 영업일 유니크 제약
 * ({@code uk_reconciliation_run_business_date}, {@code uk_settlement_merchant_date}) 이
 * 중복 집계를 막지만, 진 쪽이 예외로 끝나 알림이 시끄러워진다. 분산 락은 로드맵에 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storepay.settlement.schedule.enabled", havingValue = "true")
public class DailySettlementJob {

    private final ReconciliationService reconciliationService;
    private final SettlementService settlementService;
    private final BusinessDay businessDay;
    private final Clock clock;

    @Scheduled(cron = "${storepay.settlement.schedule.cron}", zone = "Asia/Seoul")
    public void run() {
        LocalDate businessDate = businessDay.today(clock.instant()).minusDays(1);
        // 스케줄러 스레드는 RequestIdFilter 를 타지 않아 추적 키가 비어 있다.
        // 배치 로그도 한 번의 실행으로 묶여야 해서 직접 넣고 직접 지운다.
        MDC.put(RequestContext.REQUEST_ID, "batch-settlement-" + businessDate);
        try {
            runFor(businessDate);
        } finally {
            MDC.remove(RequestContext.REQUEST_ID);
        }
    }

    private void runFor(LocalDate businessDate) {
        log.info("전일 대사·정산 배치 시작. businessDate={}", businessDate);

        ReconciliationResponse reconciliation;
        try {
            reconciliation = reconciliationService.reconcile(businessDate);
        } catch (BusinessException e) {
            log.error("대사 실패. businessDate={}, code={}", businessDate, e.getErrorCode().getCode(), e);
            return;
        }

        if (reconciliation.mismatchCount() > 0 || !reconciliation.status().isCompleted()) {
            log.error("대사 불일치로 정산을 중단합니다. businessDate={}, status={}, 불일치={}건",
                    businessDate, reconciliation.status(), reconciliation.mismatchCount());
            return;
        }

        try {
            settlementService.close(businessDate);
        } catch (BusinessException e) {
            log.error("정산 실패. businessDate={}, code={}", businessDate, e.getErrorCode().getCode(), e);
        }
    }
}
