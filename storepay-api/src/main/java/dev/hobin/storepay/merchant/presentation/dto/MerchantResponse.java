package dev.hobin.storepay.merchant.presentation.dto;

import dev.hobin.storepay.merchant.domain.Merchant;
import dev.hobin.storepay.merchant.domain.MerchantStatus;
import java.time.Instant;

public record MerchantResponse(
        String merchantKey,
        String name,
        String businessNumber,
        MerchantStatus status,
        Instant createdAt) {

    public static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(
                merchant.getMerchantKey(),
                merchant.getName(),
                merchant.getBusinessNumber(),
                merchant.getStatus(),
                merchant.getCreatedAt());
    }
}
