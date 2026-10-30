package dev.hobin.storepay.order.application;

import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.catalog.domain.Product;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.response.PageResponse;
import dev.hobin.storepay.order.application.dto.OrderResponse;
import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderQueryRepository;
import dev.hobin.storepay.order.domain.OrderRepository;
import dev.hobin.storepay.order.domain.OrderSearchCondition;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.store.domain.Store;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final StoreService storeService;
    private final ProductService productService;

    @Transactional
    public OrderResponse place(String storeKey, List<OrderLineCommand> lineCommands) {
        if (lineCommands == null || lineCommands.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_EMPTY_ITEMS);
        }

        Store store = storeService.getEntityByKey(storeKey);
        Map<String, Product> products = productService.getAllEntitiesByKeys(
                lineCommands.stream().map(OrderLineCommand::productKey).distinct().toList());

        List<Order.OrderLine> lines = lineCommands.stream()
                .map(command -> new Order.OrderLine(products.get(command.productKey()), command.quantity()))
                .toList();

        return OrderResponse.from(orderRepository.save(Order.place(store, lines)));
    }

    @Transactional
    public OrderResponse cancel(String orderKey) {
        Order order = getEntityByKey(orderKey);
        order.cancel();
        return OrderResponse.from(order);
    }

    public OrderResponse getByKey(String orderKey) {
        return OrderResponse.from(getEntityByKey(orderKey));
    }

    public PageResponse<OrderResponse> search(OrderSearchCondition condition, Pageable pageable) {
        return PageResponse.of(orderQueryRepository.search(condition, pageable), OrderResponse::from);
    }

    /** 결제 등 다른 애그리게잇이 주문을 참조할 때 쓴다. 호출 측 트랜잭션 안에서만 유효하다. */
    public Order getEntityByKey(String orderKey) {
        return orderRepository.findByOrderKey(orderKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    /** 주문 생성 입력 한 줄. 프레젠테이션 DTO 를 서비스까지 끌고 내려오지 않기 위한 커맨드 타입. */
    public record OrderLineCommand(String productKey, int quantity) {
    }
}
