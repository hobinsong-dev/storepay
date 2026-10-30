package dev.hobin.storepay.order.application;

import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.catalog.domain.Product;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderQueryRepository;
import dev.hobin.storepay.order.domain.OrderRepository;
import dev.hobin.storepay.order.domain.OrderSearchCondition;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.store.domain.Store;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    public Order place(String storeKey, List<OrderLineCommand> lineCommands) {
        if (lineCommands == null || lineCommands.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_EMPTY_ITEMS);
        }

        Store store = storeService.getByKey(storeKey);
        Map<String, Product> products = productService.getAllByKeys(
                lineCommands.stream().map(OrderLineCommand::productKey).distinct().toList());

        List<Order.OrderLine> lines = lineCommands.stream()
                .map(command -> new Order.OrderLine(
                        products.get(command.productKey()), command.quantity()))
                .toList();

        return orderRepository.save(Order.place(store, lines));
    }

    @Transactional
    public Order cancel(String orderKey) {
        Order order = getByKey(orderKey);
        order.cancel();
        return order;
    }

    public Order getByKey(String orderKey) {
        return orderRepository.findByOrderKey(orderKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    public Page<Order> search(OrderSearchCondition condition, Pageable pageable) {
        return orderQueryRepository.search(condition, pageable);
    }

    /** 주문 생성 입력 한 줄. 프레젠테이션 DTO를 서비스까지 끌고 내려오지 않기 위한 커맨드 타입. */
    public record OrderLineCommand(String productKey, int quantity) {
    }
}
