package dev.hobin.storepay.store.presentation.dto;

import dev.hobin.storepay.store.domain.Store;
import dev.hobin.storepay.store.domain.StoreStatus;
import java.time.Instant;

public record StoreResponse(
        String storeKey,
        String merchantKey,
        String name,
        String address,
        StoreStatus status,
        Instant createdAt) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getStoreKey(),
                store.getMerchant().getMerchantKey(),
                store.getName(),
                store.getAddress(),
                store.getStatus(),
                store.getCreatedAt());
    }
}
