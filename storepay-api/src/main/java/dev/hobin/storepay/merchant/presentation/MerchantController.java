package dev.hobin.storepay.merchant.presentation;

import dev.hobin.storepay.common.response.ApiResponse;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.merchant.presentation.dto.MerchantRequest;
import dev.hobin.storepay.merchant.application.dto.MerchantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가맹점", description = "가맹점 등록 및 상태 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    @Operation(summary = "가맹점 등록", description = "등록 직후 상태는 PENDING 이며 심사 후 활성화한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MerchantResponse> register(@Valid @RequestBody MerchantRequest.Register request) {
        return ApiResponse.ok(merchantService.register(request.name(), request.businessNumber()));
    }

    @Operation(summary = "가맹점 활성화")
    @PostMapping("/{merchantKey}/activate")
    public ApiResponse<MerchantResponse> activate(@PathVariable String merchantKey) {
        return ApiResponse.ok(merchantService.activate(merchantKey));
    }

    @Operation(summary = "가맹점 단건 조회")
    @GetMapping("/{merchantKey}")
    public ApiResponse<MerchantResponse> get(@PathVariable String merchantKey) {
        return ApiResponse.ok(merchantService.getByKey(merchantKey));
    }
}
