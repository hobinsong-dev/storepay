package dev.hobin.storepay.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hobin.storepay.catalog.domain.Product;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.domain.Merchant;
import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderStatus;
import dev.hobin.storepay.payment.domain.Payment;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.payment.domain.PaymentStatus;
import dev.hobin.storepay.store.domain.Store;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("결제 애그리게잇")
class PaymentTest {

    private Order order(String unitPrice, int quantity) {
        Merchant merchant = Merchant.register("호빈상사", "123-45-67890");
        merchant.activate();
        ReflectionTestUtils.setField(merchant, "id", 1L);

        Store store = Store.open(merchant, "구로점", "서울");
        ReflectionTestUtils.setField(store, "id", 10L);

        Product product = Product.create(store, "아메리카노", new BigDecimal(unitPrice));
        ReflectionTestUtils.setField(product, "id", 100L);

        return Order.place(store, List.of(new Order.OrderLine(product, quantity)));
    }

    private Payment approvedPayment(String unitPrice, int quantity) {
        Payment payment = Payment.ready(order(unitPrice, quantity), PaymentMethod.CARD);
        payment.approve("pgtx_abc", Instant.parse("2026-10-30T00:00:00Z"));
        return payment;
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("결제 금액은 주문 총액에서 가져오고 승인 시 주문도 결제 완료가 된다")
        void takesAmountFromOrder() {
            Order order = order("4500", 2);
            Payment payment = Payment.ready(order, PaymentMethod.CARD);

            assertThat(payment.getTotalAmount()).isEqualByComparingTo("9000");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(payment.getPaymentKey()).startsWith("pay_");

            payment.approve("pgtx_abc", Instant.parse("2026-10-30T00:00:00Z"));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getPgTransactionId()).isEqualTo("pgtx_abc");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("이미 승인된 건은 다시 승인할 수 없다")
        void cannotApproveTwice() {
            Payment payment = approvedPayment("4500", 1);

            assertThatThrownBy(() -> payment.approve("pgtx_def", Instant.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
        }

        @Test
        @DisplayName("실패한 건은 승인으로 되돌릴 수 없다")
        void failedCannotBeApproved() {
            Payment payment = Payment.ready(order("4500", 1), PaymentMethod.CARD);
            payment.fail("한도 초과");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("한도 초과");
            assertThatThrownBy(() -> payment.approve("pgtx_abc", Instant.now()))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @Test
        @DisplayName("부분 취소를 누적하면 잔액이 줄고 상태가 PARTIAL_CANCELED 가 된다")
        void partialCancelAccumulates() {
            Payment payment = approvedPayment("10000", 1);

            payment.cancel(new BigDecimal("3000"), "일부 환불", "pgcl_1", Instant.now());

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
            assertThat(payment.getCanceledAmount()).isEqualByComparingTo("3000");
            assertThat(payment.cancelableAmount()).isEqualByComparingTo("7000");

            payment.cancel(new BigDecimal("2000"), "추가 환불", "pgcl_2", Instant.now());

            assertThat(payment.getCanceledAmount()).isEqualByComparingTo("5000");
            assertThat(payment.cancelableAmount()).isEqualByComparingTo("5000");
            assertThat(payment.getCancels()).hasSize(2);
        }

        @Test
        @DisplayName("잔액을 모두 취소하면 CANCELED 가 되고 주문도 취소된다")
        void fullCancelCancelsOrder() {
            Order order = order("10000", 1);
            Payment payment = Payment.ready(order, PaymentMethod.CARD);
            payment.approve("pgtx_abc", Instant.now());

            payment.cancel(new BigDecimal("4000"), null, "pgcl_1", Instant.now());
            payment.cancel(new BigDecimal("6000"), null, "pgcl_2", Instant.now());

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(payment.cancelableAmount()).isEqualByComparingTo("0");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        @DisplayName("취소 가능 잔액을 넘는 요청은 거부한다")
        void rejectsOverCancel() {
            Payment payment = approvedPayment("10000", 1);
            payment.cancel(new BigDecimal("7000"), null, "pgcl_1", Instant.now());

            assertThatThrownBy(() ->
                    payment.cancel(new BigDecimal("4000"), null, "pgcl_2", Instant.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT);

            assertThat(payment.getCanceledAmount()).isEqualByComparingTo("7000");
        }

        @Test
        @DisplayName("scale 이 달라도 같은 금액이면 전액 취소로 인식한다")
        void comparesAmountByValueNotScale() {
            Payment payment = approvedPayment("10000", 1);

            // BigDecimal.equals 를 썼다면 10000 != 10000.00 이라 PARTIAL_CANCELED 로 남는다.
            payment.cancel(new BigDecimal("10000.00"), null, "pgcl_1", Instant.now());

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        }

        @Test
        @DisplayName("0 이하 금액은 거부한다")
        void rejectsNonPositiveAmount() {
            Payment payment = approvedPayment("10000", 1);

            assertThatThrownBy(() -> payment.cancel(BigDecimal.ZERO, null, "pgcl_1", Instant.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT);
        }

        @Test
        @DisplayName("승인 전 결제는 취소할 수 없다")
        void cannotCancelBeforeApproval() {
            Payment payment = Payment.ready(order("10000", 1), PaymentMethod.CARD);

            assertThatThrownBy(() ->
                    payment.cancel(new BigDecimal("1000"), null, "pgcl_1", Instant.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
        }
    }
}
