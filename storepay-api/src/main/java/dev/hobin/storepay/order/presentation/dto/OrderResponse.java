package dev.hobin.storepay.order.presentation.dto;

import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderKey,
        String storeKey,
        OrderStatus status,
        BigDecimal totalAmount,
        List<Item> items,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderKey(),
                order.getStore().getStoreKey(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().stream().map(Item::from).toList(),
                order.getCreatedAt());
    }

    public record Item(
            String productKey,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineAmount) {

        static Item from(dev.hobin.storepay.order.domain.OrderItem item) {
            return new Item(
                    item.getProduct().getProductKey(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getLineAmount());
        }
    }
}
