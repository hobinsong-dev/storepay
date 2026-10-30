package dev.hobin.storepay.settlement.infrastructure;

import dev.hobin.storepay.payment.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 정산 기준값.
 *
 * <p>영업일을 자르는 타임존을 설정으로 빼는 이유는, 서버 기본 타임존을 쓰면
 * 컨테이너 TZ 가 UTC 인 순간 영업일 경계가 9시간 밀려 하루치 정산이 통째로 어긋나기 때문이다.
 *
 * @param zone      영업일 경계를 자르는 타임존
 * @param feeRates  결제수단별 수수료율. 설정에 없는 수단은 {@code defaultFeeRate} 를 쓴다.
 */
@ConfigurationProperties("storepay.settlement")
public record SettlementProperties(
        ZoneId zone,
        BigDecimal defaultFeeRate,
        Map<PaymentMethod, BigDecimal> feeRates) {

    public SettlementProperties {
        zone = zone != null ? zone : ZoneId.of("Asia/Seoul");
        defaultFeeRate = defaultFeeRate != null ? defaultFeeRate : new BigDecimal("0.023");
        // new EnumMap<>(map) 은 빈 맵을 받으면 키 타입을 못 정해 IllegalArgumentException 을 던진다.
        // 설정에 fee-rates 를 안 적는 경우가 정상이므로 생성자가 아니라 putAll 로 채운다.
        Map<PaymentMethod, BigDecimal> rates = new EnumMap<>(PaymentMethod.class);
        if (feeRates != null) {
            rates.putAll(feeRates);
        }
        feeRates = Map.copyOf(rates);
    }

    public BigDecimal feeRateOf(PaymentMethod method) {
        return feeRates.getOrDefault(method, defaultFeeRate);
    }
}
