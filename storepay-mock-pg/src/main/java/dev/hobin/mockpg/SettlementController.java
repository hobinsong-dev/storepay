package dev.hobin.mockpg;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 일별 거래내역 조회. 실제 결제사의 정산 파일(CSV/EDI) 다운로드에 해당한다. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/settlements")
public class SettlementController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MockPgProperties properties;
    private final PgLedger ledger;

    @GetMapping
    public ResponseEntity<?> transactions(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (!properties.apiKey().equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody("invalid api key"));
        }
        List<PgLedger.Transaction> transactions = ledger.transactionsOn(date, ZONE);
        log.info("거래내역 조회: date={}, {}건", date, transactions.size());
        return ResponseEntity.ok(new SettlementFileResponse(date, transactions));
    }

    record SettlementFileResponse(LocalDate date, List<PgLedger.Transaction> transactions) {
    }

    record ErrorBody(String message) {
    }
}
