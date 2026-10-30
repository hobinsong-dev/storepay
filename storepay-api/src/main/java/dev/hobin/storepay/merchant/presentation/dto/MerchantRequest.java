package dev.hobin.storepay.merchant.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MerchantRequest {

    private MerchantRequest() {
    }

    public record Register(
            @NotBlank(message = "가맹점명은 필수입니다.")
            @Size(max = 100, message = "가맹점명은 100자를 넘을 수 없습니다.")
            String name,

            @NotBlank(message = "사업자등록번호는 필수입니다.")
            @Pattern(regexp = "\\d{3}-\\d{2}-\\d{5}", message = "사업자등록번호 형식은 000-00-00000 입니다.")
            String businessNumber) {
    }
}
