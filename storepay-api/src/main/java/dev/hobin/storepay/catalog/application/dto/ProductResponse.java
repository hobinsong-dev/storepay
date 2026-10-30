package dev.hobin.storepay.catalog.application.dto;

import dev.hobin.storepay.catalog.domain.Product;
import dev.hobin.storepay.catalog.domain.ProductStatus;
import java.math.BigDecimal;

public record ProductResponse(
        String productKey,
        String storeKey,
        String name,
        BigDecimal unitPrice,
        ProductStatus status) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getProductKey(),
                product.getStore().getStoreKey(),
                product.getName(),
                product.getUnitPrice(),
                product.getStatus());
    }
}
