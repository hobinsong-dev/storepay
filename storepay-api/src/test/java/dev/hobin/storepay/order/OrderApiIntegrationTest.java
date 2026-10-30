package dev.hobin.storepay.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.catalog.application.dto.ProductResponse;
import dev.hobin.storepay.common.response.PageResponse;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.merchant.application.dto.MerchantResponse;
import dev.hobin.storepay.order.application.OrderService;
import dev.hobin.storepay.order.application.dto.OrderResponse;
import dev.hobin.storepay.order.domain.OrderSearchCondition;
import dev.hobin.storepay.order.domain.OrderStatus;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.store.application.dto.StoreResponse;
import dev.hobin.storepay.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@DisplayName("주문 통합")
class OrderApiIntegrationTest extends IntegrationTest {

    /** 테스트마다 새 가맹점·매장을 만들어 격리한다. 사업자번호가 유니크 제약이라 충돌하지 않게 센다. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MerchantService merchantService;
    @Autowired
    private StoreService storeService;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;

    private StoreResponse store;
    private ProductResponse coffee;
    private ProductResponse cake;

    @BeforeEach
    void setUp() {
        MerchantResponse merchant = merchantService.register(
                "호빈상사", "%03d-45-67890".formatted(SEQUENCE.incrementAndGet()));
        merchantService.activate(merchant.merchantKey());

        store = storeService.open(merchant.merchantKey(), "구로점", "서울 구로구 디지털로");
        coffee = productService.create(store.storeKey(), "아메리카노", new BigDecimal("4500"));
        cake = productService.create(store.storeKey(), "치즈케이크", new BigDecimal("6800"));
    }

    @Test
    @DisplayName("주문을 생성하면 서버가 계산한 총액으로 저장된다")
    void placesOrder() {
        OrderResponse order = orderService.place(store.storeKey(), List.of(
                new OrderService.OrderLineCommand(coffee.productKey(), 2),
                new OrderService.OrderLineCommand(cake.productKey(), 1)));

        assertThat(order.totalAmount()).isEqualByComparingTo("15800");
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.items()).hasSize(2);
    }

    /**
     * open-in-view: false 환경에서 서비스가 엔티티를 반환하면 응답을 만드는 시점에 세션이 닫혀 있어
     * LazyInitializationException 이 난다. 쓰기 API 에서는 DB 는 바뀌고 응답만 500 이 나가 더 위험하다.
     * 응답 DTO 조립이 트랜잭션 안에 남아 있는지 지키는 회귀 테스트다.
     */
    @Test
    @DisplayName("단건 조회가 트랜잭션 밖에서도 지연 로딩 없이 완성된 응답을 준다")
    void readsOrderWithoutLazyLoading() {
        OrderResponse placed = orderService.place(store.storeKey(),
                List.of(new OrderService.OrderLineCommand(coffee.productKey(), 1)));

        OrderResponse found = orderService.getByKey(placed.orderKey());

        assertThat(found.orderKey()).isEqualTo(placed.orderKey());
        assertThat(found.storeKey()).isEqualTo(store.storeKey());
        assertThat(found.items()).hasSize(1);
        assertThat(found.items().get(0).productName()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("취소는 상태를 바꾸고 완성된 응답까지 돌려준다")
    void cancelsOrder() {
        OrderResponse placed = orderService.place(store.storeKey(),
                List.of(new OrderService.OrderLineCommand(cake.productKey(), 2)));

        OrderResponse canceled = orderService.cancel(placed.orderKey());

        assertThat(canceled.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.storeKey()).isEqualTo(store.storeKey());
        assertThat(canceled.items()).hasSize(1);
        assertThatCode(() -> orderService.getByKey(placed.orderKey())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("조건 검색은 지정한 조건만 적용한다")
    void searchesByCondition() {
        orderService.place(store.storeKey(),
                List.of(new OrderService.OrderLineCommand(coffee.productKey(), 1)));
        OrderResponse canceled = orderService.place(store.storeKey(),
                List.of(new OrderService.OrderLineCommand(cake.productKey(), 3)));
        orderService.cancel(canceled.orderKey());

        PageResponse<OrderResponse> all = orderService.search(
                new OrderSearchCondition(store.storeKey(), null, null, null, null, null),
                PageRequest.of(0, 20));
        assertThat(all.totalElements()).isEqualTo(2);

        PageResponse<OrderResponse> createdOnly = orderService.search(
                new OrderSearchCondition(store.storeKey(), OrderStatus.CREATED, null, null, null, null),
                PageRequest.of(0, 20));
        assertThat(createdOnly.totalElements()).isEqualTo(1);
        assertThat(createdOnly.content().get(0).totalAmount()).isEqualByComparingTo("4500");

        PageResponse<OrderResponse> expensive = orderService.search(
                new OrderSearchCondition(store.storeKey(), null, null, null, new BigDecimal("10000"), null),
                PageRequest.of(0, 20));
        assertThat(expensive.totalElements()).isEqualTo(1);
        assertThat(expensive.content().get(0).totalAmount()).isEqualByComparingTo("20400");
    }
}
