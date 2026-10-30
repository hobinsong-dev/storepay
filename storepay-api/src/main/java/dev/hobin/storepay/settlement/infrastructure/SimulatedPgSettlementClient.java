package dev.hobin.storepay.settlement.infrastructure;

import dev.hobin.storepay.settlement.application.BusinessDay;
import dev.hobin.storepay.settlement.domain.PgSettlementClient;
import dev.hobin.storepay.settlement.domain.SettlementQueryRepository;
import dev.hobin.storepay.settlement.domain.SettlementQueryRepository.ApprovedPayment;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 결제사 거래내역 시뮬레이터.
 *
 * <p>내부 장부를 그대로 되비춘다. 즉 <b>기본적으로는 불일치가 0건</b>이다.
 * 일부러 어긋난 데이터를 심어두면 "대사가 불일치를 못 찾는 버그"와
 * "시뮬레이터가 이상한 값을 주는 것"을 구분할 수 없다.
 * 불일치 시나리오는 테스트에서 이 포트를 갈아 끼워 만든다.
 */
@Component
@Profile("!pg-http")
@RequiredArgsConstructor
public class SimulatedPgSettlementClient implements PgSettlementClient {

    private final SettlementQueryRepository queryRepository;
    private final BusinessDay businessDay;

    @Override
    public List<PgTransaction> fetchTransactions(LocalDate businessDate) {
        List<ApprovedPayment> payments = queryRepository.findApprovedBetween(
                businessDay.startOf(businessDate), businessDay.endOf(businessDate));
        return payments.stream()
                .map(payment -> new PgTransaction(
                        payment.paymentKey(),
                        payment.pgTransactionId(),
                        payment.remainingAmount(),
                        payment.status().name()))
                .toList();
    }
}
