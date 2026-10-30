package dev.hobin.storepay.settlement.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.settlement.application.dto.ReconciliationResponse;
import dev.hobin.storepay.settlement.domain.PgSettlementClient;
import dev.hobin.storepay.settlement.domain.ReconciliationMismatch;
import dev.hobin.storepay.settlement.domain.ReconciliationRun;
import dev.hobin.storepay.settlement.domain.ReconciliationRunRepository;
import dev.hobin.storepay.settlement.domain.SettlementQueryRepository;
import dev.hobin.storepay.settlement.domain.SettlementQueryRepository.ApprovedPayment;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 대사.
 *
 * <p>우리 장부와 결제사 장부를 같은 영업일 기준으로 맞대어 본다. 맞지 않는 건은
 * <b>기록만</b> 하고 결제 상태를 바꾸지 않는다. 어느 쪽이 옳은지는 결제사 원장과
 * 매입사 통보를 함께 봐야 정해지는데, 배치가 멋대로 한쪽에 맞춰 버리면
 * 틀린 쪽으로 맞춘 경우를 되돌릴 근거가 사라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReconciliationService {

    private final ReconciliationRunRepository runRepository;
    private final SettlementQueryRepository queryRepository;
    private final PgSettlementClient pgSettlementClient;
    private final BusinessDay businessDay;
    private final Clock clock;

    /**
     * 영업일 하나를 대사한다.
     *
     * <p>같은 날짜를 다시 돌리면 이전 결과를 <b>대체</b>한다. 결과가 여러 벌 남아 있으면
     * "그날 불일치가 몇 건이냐"에 답이 두 개가 된다. 또 타이밍 차이로 생긴 불일치
     * (승인과 취소가 창 경계에 걸친 경우)는 재실행하면 사라지는데, 대체하지 않으면
     * 사라진 사실을 알 수 없다.
     */
    @Transactional
    public ReconciliationResponse reconcile(LocalDate businessDate) {
        Instant now = clock.instant();
        if (!businessDay.isClosed(businessDate, now)) {
            throw new BusinessException(ErrorCode.RECONCILIATION_FUTURE_DATE,
                    "%s 은 아직 끝나지 않은 영업일입니다.".formatted(businessDate));
        }

        runRepository.findByBusinessDate(businessDate).ifPresent(previous -> {
            log.info("이전 대사 결과를 대체합니다. businessDate={}, runKey={}",
                    businessDate, previous.getRunKey());
            runRepository.delete(previous);
            runRepository.flush();
        });

        ReconciliationRun run = runRepository.save(ReconciliationRun.start(businessDate, now));

        List<ApprovedPayment> internal = queryRepository.findApprovedBetween(
                businessDay.startOf(businessDate), businessDay.endOf(businessDate));

        List<PgSettlementClient.PgTransaction> pgTransactions;
        try {
            pgTransactions = pgSettlementClient.fetchTransactions(businessDate);
        } catch (RuntimeException e) {
            // 결제사를 못 부른 것은 "불일치 0건"이 아니다. 실패로 남겨야 정산이 막힌다.
            run.fail(e.getMessage(), clock.instant());
            log.warn("결제사 거래내역 조회 실패. businessDate={}", businessDate, e);
            return ReconciliationResponse.from(run);
        }

        compare(run, internal, pgTransactions);
        run.complete(internal.size(), pgTransactions.size(),
                internal.size() - run.getMismatches().size(), clock.instant());

        log.info("대사 완료. businessDate={}, 내부={}건, 결제사={}건, 불일치={}건",
                businessDate, run.getInternalCount(), run.getPgCount(), run.getMismatchCount());
        return ReconciliationResponse.from(run);
    }

    private void compare(ReconciliationRun run,
                         List<ApprovedPayment> internal,
                         List<PgSettlementClient.PgTransaction> pgTransactions) {
        Map<String, PgSettlementClient.PgTransaction> pgByPaymentKey = new HashMap<>();
        for (PgSettlementClient.PgTransaction transaction : pgTransactions) {
            pgByPaymentKey.put(transaction.paymentKey(), transaction);
        }

        Map<String, ApprovedPayment> internalByPaymentKey = new LinkedHashMap<>();
        for (ApprovedPayment payment : internal) {
            internalByPaymentKey.put(payment.paymentKey(), payment);
        }

        for (ApprovedPayment payment : internal) {
            PgSettlementClient.PgTransaction counterpart = pgByPaymentKey.get(payment.paymentKey());
            if (counterpart == null) {
                ReconciliationMismatch.missingInPg(run, payment.paymentKey(), payment.pgTransactionId(),
                        payment.remainingAmount(), payment.status().name());
                continue;
            }
            // 금액 비교는 compareTo 로 한다. BigDecimal 의 equals 는 scale 까지 보기 때문에
            // 10000 과 10000.00 을 "다르다"로 판정해 매일 전 건이 불일치로 잡힌다.
            if (payment.remainingAmount().compareTo(counterpart.amount()) != 0) {
                ReconciliationMismatch.amountMismatch(run, payment.paymentKey(), payment.pgTransactionId(),
                        payment.remainingAmount(), counterpart.amount());
                continue;
            }
            if (!payment.status().name().equals(counterpart.status())) {
                ReconciliationMismatch.statusMismatch(run, payment.paymentKey(), payment.pgTransactionId(),
                        payment.status().name(), counterpart.status());
            }
        }

        // 결제사에만 있는 건. 승인 응답도 웹훅도 모두 놓친 경우라 가장 위험하다 —
        // 손님 카드는 긁혔는데 우리 쪽에는 매출이 없다.
        for (PgSettlementClient.PgTransaction transaction : pgTransactions) {
            if (!internalByPaymentKey.containsKey(transaction.paymentKey())) {
                ReconciliationMismatch.missingInInternal(run, transaction.paymentKey(),
                        transaction.pgTransactionId(), transaction.amount(), transaction.status());
            }
        }
    }

    public ReconciliationResponse getByDate(LocalDate businessDate) {
        return ReconciliationResponse.from(runRepository.findByBusinessDate(businessDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECONCILIATION_NOT_FOUND)));
    }
}
