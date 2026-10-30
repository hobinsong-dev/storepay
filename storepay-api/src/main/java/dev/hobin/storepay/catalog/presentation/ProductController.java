package dev.hobin.storepay.catalog.presentation;

import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.catalog.presentation.dto.ProductRequest;
import dev.hobin.storepay.catalog.presentation.dto.ProductResponse;
import dev.hobin.storepay.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품", description = "매장 상품 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest.Create request) {
        return ApiResponse.ok(ProductResponse.from(
                productService.create(request.storeKey(), request.name(), request.unitPrice())));
    }

    @Operation(summary = "상품 판매 상태 변경")
    @PatchMapping("/{productKey}/status")
    public ApiResponse<ProductResponse> changeStatus(
            @PathVariable String productKey,
            @Valid @RequestBody ProductRequest.ChangeStatus request) {
        return ApiResponse.ok(
                ProductResponse.from(productService.changeStatus(productKey, request.status())));
    }

    @Operation(summary = "상품 단건 조회")
    @GetMapping("/{productKey}")
    public ApiResponse<ProductResponse> get(@PathVariable String productKey) {
        return ApiResponse.ok(ProductResponse.from(productService.getByKey(productKey)));
    }
}
