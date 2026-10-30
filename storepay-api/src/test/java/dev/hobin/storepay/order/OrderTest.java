package dev.hobin.storepay.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hobin.storepay.catalog.domain.Product;
import dev.hobin.storepay.catalog.domain.ProductStatus;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.domain.Merchant;
import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderStatus;
import dev.hobin.storepay.store.domain.Store;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("주문 애그리게잇")
class OrderTest {

    private Store activeStore(long storeId) {
        Merchant merchant = Merchant.register("호빈상사", "123-45-67890");
        merchant.activate();
        ReflectionTestUtils.setField(merchant, "id", 1L);

        Store store = Store.open(merchant, "구로점", "서울 구로구");
        ReflectionTestUtils.setField(store, "id", storeId);
        return store;
    }

    private Product product(Store store, String name, String price, long productId) {
        Product product = Product.create(store, name, new BigDecimal(price));
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }

    @Nested
    @DisplayName("주문 생성")
    class Place {

        @Test
        @DisplayName("총액은 항목별 (단가 × 수량) 합계로 계산된다")
        void calculatesTotalFromItems() {
            Store store = activeStore(10L);
            Order order = Order.place(store, List.of(
                    new Order.OrderLine(product(store, "아메리카노", "4500", 1L), 2),
                    new Order.OrderLine(product(store, "치즈케이크", "6800", 2L), 1)));

            assertThat(order.getTotalAmount()).isEqualByComparingTo("15800");
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getOrderKey()).startsWith("ord_");
        }

        @Test
        @DisplayName("주문 후 상품 단가가 바뀌어도 이미 만든 주문의 금액은 그대로다")
        void snapshotsUnitPriceAtOrderTime() {
            Store store = activeStore(10L);
            Product coffee = product(store, "아메리카노", "4500", 1L);
            Order order = Order.place(store, List.of(new Order.OrderLine(coffee, 2)));

            coffee.changePrice(new BigDecimal("9000"));

            assertThat(order.getTotalAmount()).isEqualByComparingTo("9000");
            assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("4500");
        }

        @Test
        @DisplayName("다른 매장 상품이 섞이면 거부한다")
        void rejectsProductFromAnotherStore() {
            Store store = activeStore(10L);
            Store otherStore = activeStore(99L);

            assertThatThrownBy(() -> Order.place(store, List.of(
                    new Order.OrderLine(product(otherStore, "남의 상품", "1000", 3L), 1))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ORDER_ITEM_STORE_MISMATCH);
        }

        @Test
        @DisplayName("판매 중이 아닌 상품은 담을 수 없다")
        void rejectsNotSellableProduct() {
            Store store = activeStore(10L);
            Product soldOut = product(store, "품절빵", "3000", 4L);
            soldOut.changeStatus(ProductStatus.SOLD_OUT);

            assertThatThrownBy(() -> Order.place(store, List.of(new Order.OrderLine(soldOut, 1))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PRODUCT_NOT_SELLABLE);
        }

        @Test
        @DisplayName("항목이 없으면 거부한다")
        void rejectsEmptyItems() {
            assertThatThrownBy(() -> Order.place(activeStore(10L), List.of()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ORDER_EMPTY_ITEMS);
        }

        @Test
        @DisplayName("수량이 0 이하면 거부한다")
        void rejectsNonPositiveQuantity() {
            Store store = activeStore(10L);
            Product coffee = product(store, "아메리카노", "4500", 1L);

            assertThatThrownBy(() -> new Order.OrderLine(coffee, 0))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("CREATED 에서만 결제 완료로 갈 수 있다")
        void onlyCreatedCanBePaid() {
            Store store = activeStore(10L);
            Order order = Order.place(store, List.of(
                    new Order.OrderLine(product(store, "아메리카노", "4500", 1L), 1)));

            order.markPaid();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

            assertThatThrownBy(order::markPaid)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
        }

        @Test
        @DisplayName("이미 취소된 주문은 다시 취소할 수 없다")
        void cannotCancelTwice() {
            Store store = activeStore(10L);
            Order order = Order.place(store, List.of(
                    new Order.OrderLine(product(store, "아메리카노", "4500", 1L), 1)));

            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
        }
    }
}
