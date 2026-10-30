package dev.hobin.storepay.catalog.presentation.dto;

import dev.hobin.storepay.catalog.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class ProductRequest {

    private ProductRequest() {
    }

    public record Create(
            @NotBlank(message = "매장 키는 필수입니다.")
            String storeKey,

            @NotBlank(message = "상품명은 필수입니다.")
            @Size(max = 100, message = "상품명은 100자를 넘을 수 없습니다.")
            String name,

            @NotNull(message = "단가는 필수입니다.")
            @DecimalMin(value = "0", message = "단가는 0 이상이어야 합니다.")
            @Digits(integer = 13, fraction = 2, message = "단가 형식이 올바르지 않습니다.")
            BigDecimal unitPrice) {
    }

    public record ChangeStatus(
            @NotNull(message = "상태는 필수입니다.")
            ProductStatus status) {
    }
}
