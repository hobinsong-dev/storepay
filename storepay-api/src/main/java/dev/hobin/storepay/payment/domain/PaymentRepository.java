package dev.hobin.storepay.payment.domain;

import dev.hobin.storepay.order.domain.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);

    boolean existsByOrder(Order order);
}
