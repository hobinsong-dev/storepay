package dev.hobin.storepay.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.settlement.application.FeePolicy;
import dev.hobin.storepay.settlement.infrastructure.SettlementProperties;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("수수료 정책")
class FeePolicyTest {

    private final FeePolicy feePolicy = new FeePolicy(new SettlementProperties(
            ZoneId.of("Asia/Seoul"),
            new BigDecimal("0.023"),
            Map.of(PaymentMethod.EASY_PAY, new BigDecimal("0.028"))));

    @Test
    @DisplayName("결제수단별 요율을 적용하고 설정에 없으면 기본 요율을 쓴다")
    void appliesRatePerMethod() {
        // 10,000 * 0.023 = 230
        assertThat(feePolicy.feeOf(PaymentMethod.CARD, new BigDecimal("10000")))
                .isEqualByComparingTo("230");
        // 10,000 * 0.028 = 280
        assertThat(feePolicy.feeOf(PaymentMethod.EASY_PAY, new BigDecimal("10000")))
                .isEqualByComparingTo("280");
        // 설정에 없는 수단은 기본 요율
        assertThat(feePolicy.feeOf(PaymentMethod.VIRTUAL_ACCOUNT, new BigDecimal("10000")))
                .isEqualByComparingTo("230");
    }

    @Test
    @DisplayName("원 미만은 절사한다")
    void truncatesBelowWon() {
        // 5,000 * 0.023 = 115.0 -> 115
        assertThat(feePolicy.feeOf(PaymentMethod.CARD, new BigDecimal("5000")))
                .isEqualByComparingTo("115");
        // 1,000 * 0.023 = 23.0 -> 23
        assertThat(feePolicy.feeOf(PaymentMethod.CARD, new BigDecimal("1000")))
                .isEqualByComparingTo("23");
        // 999 * 0.023 = 22.977 -> 22 (반올림이면 23)
        assertThat(feePolicy.feeOf(PaymentMethod.CARD, new BigDecimal("999")))
                .isEqualByComparingTo("22");
    }

    /**
     * 수수료를 건별로 계산하느냐 합산 후 계산하느냐는 취향이 아니다.
     * 결제사 청구서가 건별로 오므로 합산 후 계산하면 매일 몇 원씩 어긋난다.
     */
    @Nested
    @DisplayName("건별 계산과 합산 후 계산은 결과가 다르다")
    class PerTransactionVersusAggregate {

        private final List<BigDecimal> amounts = List.of(
                new BigDecimal("999"), new BigDecimal("999"), new BigDecimal("999"));

        @Test
        @DisplayName("건별로 계산해 더하면 66원이다")
        void perTransaction() {
            BigDecimal total = amounts.stream()
                    .map(amount -> feePolicy.feeOf(PaymentMethod.CARD, amount))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 22 + 22 + 22
            assertThat(total).isEqualByComparingTo("66");
        }

        @Test
        @DisplayName("합산 후 한 번에 계산하면 68원이라 2원이 어긋난다")
        void aggregate() {
            BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            // 2,997 * 0.023 = 68.931 -> 68
            BigDecimal total = feePolicy.feeOf(PaymentMethod.CARD, sum);

            assertThat(total).isEqualByComparingTo("68");
            assertThat(total).isNotEqualByComparingTo("66");
        }
    }
}
