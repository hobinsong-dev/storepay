package dev.hobin.storepay.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.catalog.domain.Product;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.merchant.domain.Merchant;
import dev.hobin.storepay.order.application.OrderService;
import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderSearchCondition;
import dev.hobin.storepay.order.domain.OrderStatus;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.store.domain.Store;
import dev.hobin.storepay.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("주문 통합")
class OrderApiIntegrationTest extends IntegrationTest {

    @Autowired
    private MerchantService merchantService;
    @Autowired
    private StoreService storeService;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;

    private Store store;
    private Product coffee;
    private Product cake;

    @BeforeEach
    void setUp() {
        Merchant merchant = merchantService.register("호빈상사", "123-45-67890");
        merchantService.activate(merchant.getMerchantKey());

        store = storeService.open(merchant.getMerchantKey(), "구로점", "서울 구로구 디지털로");
        coffee = productService.create(store.getStoreKey(), "아메리카노", new BigDecimal("4500"));
        cake = productService.create(store.getStoreKey(), "치즈케이크", new BigDecimal("6800"));
    }

    @Test
    @DisplayName("주문을 생성하면 서버가 계산한 총액으로 저장된다")
    void placesOrder() {
        Order order = orderService.place(store.getStoreKey(), List.of(
                new OrderService.OrderLineCommand(coffee.getProductKey(), 2),
                new OrderService.OrderLineCommand(cake.getProductKey(), 1)));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("15800");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("조건 검색은 지정한 조건만 적용한다")
    void searchesByCondition() {
        orderService.place(store.getStoreKey(),
                List.of(new OrderService.OrderLineCommand(coffee.getProductKey(), 1)));
        Order canceled = orderService.place(store.getStoreKey(),
                List.of(new OrderService.OrderLineCommand(cake.getProductKey(), 3)));
        orderService.cancel(canceled.getOrderKey());

        Page<Order> all = orderService.search(
                new OrderSearchCondition(store.getStoreKey(), null, null, null, null, null),
                PageRequest.of(0, 20));
        assertThat(all.getTotalElements()).isEqualTo(2);

        Page<Order> createdOnly = orderService.search(
                new OrderSearchCondition(store.getStoreKey(), OrderStatus.CREATED, null, null, null, null),
                PageRequest.of(0, 20));
        assertThat(createdOnly.getTotalElements()).isEqualTo(1);
        assertThat(createdOnly.getContent().get(0).getTotalAmount()).isEqualByComparingTo("4500");

        Page<Order> expensive = orderService.search(
                new OrderSearchCondition(store.getStoreKey(), null, null, null, new BigDecimal("10000"), null),
                PageRequest.of(0, 20));
        assertThat(expensive.getTotalElements()).isEqualTo(1);
        assertThat(expensive.getContent().get(0).getTotalAmount()).isEqualByComparingTo("20400");
    }
}
