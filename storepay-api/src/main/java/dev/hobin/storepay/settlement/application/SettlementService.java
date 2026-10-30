package dev.hobin.storepay.settlement.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.domain.Merchant;
import dev.hobin.storepay.merchant.domain.MerchantRepository;
import dev.hobin.storepay.settlement.application.dto.SettlementResponse;
import dev.hobin.storepay.settlement.domain.ReconciliationRun;
import dev.hobin.storepay.settlement.domain.ReconciliationRunRepository;
import dev.hobin.storepay.settlement.domain.Settlement;
import dev.hobin.storepay.settlement.domain.SettlementQueryRepository;
import dev.hobin.storepay.settlement.domain.SettlementQueryRepository.ApprovedPayment;
import dev.hobin.storepay.settlement.domain.SettlementQueryRepository.CanceledAmount;
import dev.hobin.storepay.settlement.domain.SettlementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가맹점 일 정산.
 *
 * <p>대사가 끝나고 불일치가 없는 영업일만 집계한다. 어긋난 숫자로 돈을 내보내면
 * 되돌리는 데 가맹점 동의가 필요하고, 그사이 가맹점은 이미 그 돈을 썼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final ReconciliationRunRepository runRepository;
    private final SettlementQueryRepository queryRepository;
    private final MerchantRepository merchantRepository;
    private final FeePolicy feePolicy;
    private final BusinessDay businessDay;
    private final Clock clock;

    /**
     * 영업일 하루를 가맹점별로 집계한다.
     *
     * <p>이미 집계된 날짜는 거부한다. 대사와 달리 덮어쓰기를 허용하지 않는 이유는,
     * 정산에는 <b>지급</b>이라는 비가역 후속 동작이 달려 있기 때문이다.
     * 다시 집계하려면 기존 정산을 명시적으로 취소해야 한다.
     */
    @Transactional
    public List<SettlementResponse> close(LocalDate businessDate) {
        ReconciliationRun run = runRepository.findByBusinessDate(businessDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_RECONCILIATION_REQUIRED,
                        "%s 의 대사가 아직 없습니다.".formatted(businessDate)));
        if (!run.isClean()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_RECONCILIATION_REQUIRED,
                    "%s 의 대사 상태가 %s, 불일치 %d건입니다."
                            .formatted(businessDate, run.getStatus(), run.getMismatchCount()));
        }
        if (settlementRepository.existsByBusinessDate(businessDate)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ALREADY_CLOSED,
                    "%s 은 이미 마감됐습니다.".formatted(businessDate));
        }

        Instant from = businessDay.startOf(businessDate);
        Instant to = businessDay.endOf(businessDate);

        Map<Long, Tally> tallies = new LinkedHashMap<>();
        for (ApprovedPayment payment : queryRepository.findApprovedBetween(from, to)) {
            tallies.computeIfAbsent(payment.merchantId(), key -> new Tally())
                    .addApproval(payment.totalAmount(),
                            feePolicy.feeOf(payment.method(), payment.totalAmount()));
        }
        for (CanceledAmount cancel : queryRepository.findCanceledBetween(from, to)) {
            // 취소는 수수료도 함께 돌려준다. 돌려주지 않으면 전액 취소된 거래에서
            // 가맹점이 팔지도 않은 매출의 수수료만 부담한다.
            tallies.computeIfAbsent(cancel.merchantId(), key -> new Tally())
                    .addCancel(cancel.amount(), feePolicy.feeOf(cancel.method(), cancel.amount()));
        }

        if (tallies.isEmpty()) {
            log.info("정산 대상 거래가 없습니다. businessDate={}", businessDate);
            return List.of();
        }

        Map<Long, Merchant> merchants = new LinkedHashMap<>();
        merchantRepository.findAllById(new TreeSet<>(tallies.keySet()))
                .forEach(merchant -> merchants.put(merchant.getId(), merchant));

        List<SettlementResponse> responses = tallies.entrySet().stream()
                .map(entry -> {
                    Tally tally = entry.getValue();
                    Settlement settlement = Settlement.of(
                            merchants.get(entry.getKey()), businessDate, tally.count,
                            tally.approved, tally.canceled, tally.fee);
                    return SettlementResponse.from(settlementRepository.save(settlement));
                })
                .toList();

        log.info("정산 마감. businessDate={}, 가맹점={}곳", businessDate, responses.size());
        return responses;
    }

    @Transactional
    public SettlementResponse markPaid(String settlementKey) {
        Settlement settlement = settlementRepository.findBySettlementKey(settlementKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        settlement.markPaid(clock.instant());
        return SettlementResponse.from(settlement);
    }

    public List<SettlementResponse> getByDate(LocalDate businessDate) {
        return settlementRepository.findByBusinessDateOrderByIdAsc(businessDate).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    public SettlementResponse getByKey(String settlementKey) {
        return SettlementResponse.from(settlementRepository.findBySettlementKey(settlementKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND)));
    }

    /**
     * 가맹점 하나의 누계.
     *
     * <p>수수료는 거래마다 계산해 더한다. 합계에 요율을 한 번 곱하면 결제사 청구서와
     * 매일 몇 원씩 어긋난다 — 청구서도 건별로 끊어 오기 때문이다.
     */
    private static final class Tally {
        private int count;
        private BigDecimal approved = BigDecimal.ZERO;
        private BigDecimal canceled = BigDecimal.ZERO;
        private BigDecimal fee = BigDecimal.ZERO;

        void addApproval(BigDecimal amount, BigDecimal transactionFee) {
            this.count++;
            this.approved = this.approved.add(amount);
            this.fee = this.fee.add(transactionFee);
        }

        void addCancel(BigDecimal amount, BigDecimal refundedFee) {
            this.canceled = this.canceled.add(amount);
            this.fee = this.fee.subtract(refundedFee);
        }
    }
}
