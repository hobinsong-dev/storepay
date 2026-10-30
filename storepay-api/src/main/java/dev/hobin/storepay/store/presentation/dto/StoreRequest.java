package dev.hobin.storepay.store.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class StoreRequest {

    private StoreRequest() {
    }

    public record Open(
            @NotBlank(message = "가맹점 키는 필수입니다.")
            String merchantKey,

            @NotBlank(message = "매장명은 필수입니다.")
            @Size(max = 100, message = "매장명은 100자를 넘을 수 없습니다.")
            String name,

            @Size(max = 255, message = "주소는 255자를 넘을 수 없습니다.")
            String address) {
    }
}
