package dev.hobin.storepay.settlement.application;

import dev.hobin.storepay.settlement.infrastructure.SettlementProperties;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 영업일 → 시각 구간 변환.
 *
 * <p>구간은 <b>반열린</b>([from, to))이다. 양끝을 모두 포함하면 자정에 승인된 건이
 * 어제와 오늘 정산에 두 번 잡힌다.
 */
@Component
public class BusinessDay {

    private final SettlementProperties properties;

    public BusinessDay(SettlementProperties properties) {
        this.properties = properties;
    }

    public Instant startOf(LocalDate businessDate) {
        return businessDate.atStartOfDay(properties.zone()).toInstant();
    }

    public Instant endOf(LocalDate businessDate) {
        return businessDate.plusDays(1).atStartOfDay(properties.zone()).toInstant();
    }

    /** 오늘(정산 타임존 기준)은 아직 끝나지 않았으므로 대사·정산 대상이 아니다. */
    public boolean isClosed(LocalDate businessDate, Instant now) {
        return endOf(businessDate).isBefore(now) || endOf(businessDate).equals(now);
    }

    public LocalDate today(Instant now) {
        return now.atZone(properties.zone()).toLocalDate();
    }
}
