package dev.hobin.storepay.settlement.presentation;

import dev.hobin.storepay.common.response.ApiResponse;
import dev.hobin.storepay.settlement.application.SettlementService;
import dev.hobin.storepay.settlement.application.dto.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정산", description = "가맹점 일 정산 집계 / 지급")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    @Operation(
            summary = "일 정산 마감",
            description = """
                    대사가 끝나고 불일치가 없는 영업일만 마감할 수 있다.
                    이미 마감된 날짜는 409 로 거부한다 — 지급이라는 비가역 동작이 뒤에 달려 있다.""")
    @PostMapping("/{businessDate}")
    public ApiResponse<List<SettlementResponse>> close(
            @Parameter(description = "영업일 (yyyy-MM-dd)", example = "2026-10-29")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.ok(settlementService.close(businessDate));
    }

    @Operation(summary = "일 정산 조회")
    @GetMapping("/{businessDate}")
    public ApiResponse<List<SettlementResponse>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.ok(settlementService.getByDate(businessDate));
    }

    @Operation(summary = "정산 지급 완료 처리")
    @PostMapping("/keys/{settlementKey}/payout")
    public ApiResponse<SettlementResponse> markPaid(@PathVariable String settlementKey) {
        return ApiResponse.ok(settlementService.markPaid(settlementKey));
    }
}
