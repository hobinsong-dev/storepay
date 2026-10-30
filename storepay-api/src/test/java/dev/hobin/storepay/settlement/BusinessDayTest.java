package dev.hobin.storepay.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hobin.storepay.settlement.application.BusinessDay;
import dev.hobin.storepay.settlement.infrastructure.SettlementProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("영업일 경계")
class BusinessDayTest {

    private final BusinessDay businessDay = new BusinessDay(new SettlementProperties(
            ZoneId.of("Asia/Seoul"), new BigDecimal("0.023"), Map.of()));

    @Test
    @DisplayName("KST 자정부터 다음 날 자정까지로 자른다")
    void cutsAtSeoulMidnight() {
        LocalDate date = LocalDate.of(2026, 10, 29);

        // KST 2026-10-29 00:00 == UTC 2026-10-28 15:00
        assertThat(businessDay.startOf(date)).isEqualTo(Instant.parse("2026-10-28T15:00:00Z"));
        assertThat(businessDay.endOf(date)).isEqualTo(Instant.parse("2026-10-29T15:00:00Z"));
    }

    /**
     * 구간이 반열린([from, to))이 아니면 자정에 승인된 건이
     * 어제와 오늘 정산에 두 번 잡혀 그만큼 돈이 두 번 나간다.
     */
    @Test
    @DisplayName("어제의 끝과 오늘의 시작이 같은 시각이라 경계 거래가 한 번만 잡힌다")
    void boundariesMeetExactly() {
        LocalDate yesterday = LocalDate.of(2026, 10, 29);
        LocalDate today = LocalDate.of(2026, 10, 30);

        assertThat(businessDay.endOf(yesterday)).isEqualTo(businessDay.startOf(today));
    }

    @Test
    @DisplayName("끝나지 않은 영업일은 닫히지 않은 것으로 본다")
    void todayIsNotClosed() {
        LocalDate date = LocalDate.of(2026, 10, 29);

        assertThat(businessDay.isClosed(date, Instant.parse("2026-10-29T14:59:59Z"))).isFalse();
        assertThat(businessDay.isClosed(date, Instant.parse("2026-10-29T15:00:00Z"))).isTrue();
        assertThat(businessDay.isClosed(date, Instant.parse("2026-10-29T15:00:01Z"))).isTrue();
    }

    /**
     * 서버 타임존이 UTC 여도 영업일은 KST 로 잘려야 한다.
     * UTC 로 자르면 KST 오전 9시 이전 거래가 전날 정산으로 넘어간다.
     */
    @Test
    @DisplayName("서버 타임존과 무관하게 설정한 타임존으로 자른다")
    void independentOfServerZone() {
        // KST 2026-10-30 08:00 == UTC 2026-10-29 23:00
        Instant earlyMorningKst = Instant.parse("2026-10-29T23:00:00Z");

        assertThat(businessDay.today(earlyMorningKst)).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(earlyMorningKst.atZone(ZoneId.of("UTC")).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 10, 29));
    }
}
