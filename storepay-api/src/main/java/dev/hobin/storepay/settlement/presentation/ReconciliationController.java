package dev.hobin.storepay.settlement.presentation;

import dev.hobin.storepay.common.response.ApiResponse;
import dev.hobin.storepay.settlement.application.ReconciliationService;
import dev.hobin.storepay.settlement.application.dto.ReconciliationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대사", description = "결제사 거래내역과 내부 장부 대조")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reconciliations")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @Operation(
            summary = "일별 대사 실행",
            description = """
                    끝난 영업일만 대사할 수 있다. 같은 날짜를 다시 실행하면 이전 결과를 대체한다.
                    불일치는 기록만 하며 결제 상태를 바꾸지 않는다.""")
    @PostMapping("/{businessDate}")
    public ApiResponse<ReconciliationResponse> reconcile(
            @Parameter(description = "영업일 (yyyy-MM-dd)", example = "2026-10-29")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.ok(reconciliationService.reconcile(businessDate));
    }

    @Operation(summary = "대사 결과 조회")
    @GetMapping("/{businessDate}")
    public ApiResponse<ReconciliationResponse> get(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.ok(reconciliationService.getByDate(businessDate));
    }
}
