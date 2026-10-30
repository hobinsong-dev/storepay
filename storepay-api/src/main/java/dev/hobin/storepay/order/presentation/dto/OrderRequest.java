package dev.hobin.storepay.order.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public final class OrderRequest {

    private OrderRequest() {
    }

    public record Place(
            @NotBlank(message = "매장 키는 필수입니다.")
            String storeKey,

            @Valid
            @NotEmpty(message = "주문 항목은 최소 1건이어야 합니다.")
            List<Line> items) {
    }

    public record Line(
            @NotBlank(message = "상품 키는 필수입니다.")
            String productKey,

            @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
            int quantity) {
    }
}
