package dev.hobin.storepay.settlement.application;

import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.settlement.infrastructure.SettlementProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * 수수료 계산.
 *
 * <p>두 가지를 명시적으로 정한다. 둘 다 "기본값"이 존재하지 않는 <b>사업 결정</b>이고,
 * 말로 정해두지 않으면 구현한 사람의 손끝에서 가맹점 수익이 달라진다.
 *
 * <ol>
 *   <li><b>건별 계산 후 합산</b>. 합산 후 한 번에 계산하면 같은 매출에도 건수에 따라 금액이 달라진다.
 *       결제사 청구서는 건별로 오므로, 합산 후 계산하면 우리 장부와 청구서가 매일 몇 원씩 어긋난다.</li>
 *   <li><b>원 단위 절사</b>({@code RoundingMode.DOWN}). 수수료를 버리는 방향이라 가맹점에 유리하다.
 *       {@code HALF_UP} 으로 두면 반올림된 1원이 건수만큼 쌓여 분쟁이 된다.</li>
 * </ol>
 */
@Component
public class FeePolicy {

    private final SettlementProperties properties;

    public FeePolicy(SettlementProperties properties) {
        this.properties = properties;
    }

    /** 거래 한 건의 수수료. 호출자가 건별로 부르고 결과를 더해야 한다. */
    public BigDecimal feeOf(PaymentMethod method, BigDecimal amount) {
        return amount.multiply(properties.feeRateOf(method)).setScale(0, RoundingMode.DOWN);
    }
}
