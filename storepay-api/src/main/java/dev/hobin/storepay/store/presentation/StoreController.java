package dev.hobin.storepay.store.presentation;

import dev.hobin.storepay.common.response.ApiResponse;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.store.presentation.dto.StoreRequest;
import dev.hobin.storepay.store.presentation.dto.StoreResponse;
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

@Tag(name = "매장", description = "가맹점 하위 매장 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    @Operation(summary = "매장 개설", description = "활성 가맹점에만 매장을 열 수 있다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoreResponse> open(@Valid @RequestBody StoreRequest.Open request) {
        return ApiResponse.ok(StoreResponse.from(
                storeService.open(request.merchantKey(), request.name(), request.address())));
    }

    @Operation(summary = "매장 단건 조회")
    @GetMapping("/{storeKey}")
    public ApiResponse<StoreResponse> get(@PathVariable String storeKey) {
        return ApiResponse.ok(StoreResponse.from(storeService.getByKey(storeKey)));
    }
}
