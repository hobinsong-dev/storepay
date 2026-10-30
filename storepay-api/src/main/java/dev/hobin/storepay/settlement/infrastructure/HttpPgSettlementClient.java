package dev.hobin.storepay.settlement.infrastructure;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.payment.infrastructure.PgProperties;
import dev.hobin.storepay.settlement.domain.PgSettlementClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 결제사 거래내역 HTTP 어댑터.
 *
 * <p>승인 API 와 달리 여기서는 <b>조회가 실패하면 그냥 던진다</b>. 승인은 재시도가 이중 결제를
 * 부를 수 있어 조심해야 하지만, 거래내역 조회는 부수효과가 없어 몇 번이고 다시 불러도 된다.
 * 대신 부분 응답을 성공으로 받아들이지 않는다 — 내역 절반만 받고 대사를 끝내면
 * 나머지 절반이 전부 MISSING_IN_PG 로 잡혀 정산이 통째로 막힌다.
 */
@Component
@Profile("pg-http")
public class HttpPgSettlementClient implements PgSettlementClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestClient restClient;

    public HttpPgSettlementClient(RestClient.Builder builder, PgProperties properties) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Api-Key", properties.apiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<PgTransaction> fetchTransactions(LocalDate businessDate) {
        try {
            List<PgTransaction> transactions = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/settlements")
                            .queryParam("date", DATE.format(businessDate))
                            .build())
                    .retrieve()
                    .body(SettlementFileResponse.class)
                    .transactions();
            return transactions == null ? List.of() : transactions;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.RECONCILIATION_PG_UNAVAILABLE,
                    "결제사 거래내역 조회 실패: %s".formatted(e.getMessage()));
        }
    }

    private record SettlementFileResponse(LocalDate date, List<PgTransaction> transactions) {
    }
}
