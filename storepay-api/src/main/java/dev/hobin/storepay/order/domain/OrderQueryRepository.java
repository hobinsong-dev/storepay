package dev.hobin.storepay.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderQueryRepository {

    Page<Order> search(OrderSearchCondition condition, Pageable pageable);
}
