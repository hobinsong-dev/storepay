package dev.hobin.storepay.order.presentation;

import dev.hobin.storepay.common.response.ApiResponse;
import dev.hobin.storepay.order.application.OrderService;
import dev.hobin.storepay.order.domain.OrderSearchCondition;
import dev.hobin.storepay.order.domain.OrderStatus;
import dev.hobin.storepay.order.presentation.dto.OrderRequest;
import dev.hobin.storepay.order.presentation.dto.OrderResponse;
import dev.hobin.storepay.order.presentation.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문", description = "POS 주문 생성 / 조회 / 취소")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    @Operation(
            summary = "주문 생성",
            description = "총액은 서버가 상품 마스터 단가로 다시 계산한다. 클라이언트가 보낸 금액은 받지 않는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> place(@Valid @RequestBody OrderRequest.Place request) {
        return ApiResponse.ok(OrderResponse.from(orderService.place(
                request.storeKey(),
                request.items().stream()
                        .map(item -> new OrderService.OrderLineCommand(item.productKey(), item.quantity()))
                        .toList())));
    }

    @Operation(summary = "주문 단건 조회")
    @GetMapping("/{orderKey}")
    public ApiResponse<OrderResponse> get(@PathVariable String orderKey) {
        return ApiResponse.ok(OrderResponse.from(orderService.getByKey(orderKey)));
    }

    @Operation(summary = "주문 취소")
    @PostMapping("/{orderKey}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable String orderKey) {
        return ApiResponse.ok(OrderResponse.from(orderService.cancel(orderKey)));
    }

    @Operation(summary = "주문 검색", description = "지정한 조건만 WHERE 절에 적용된다.")
    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> search(
            @Parameter(description = "매장 키") @RequestParam(required = false) String storeKey,
            @Parameter(description = "주문 상태") @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "주문 시작 일시 (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant orderedFrom,
            @Parameter(description = "주문 종료 일시 (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant orderedTo,
            @Parameter(description = "최소 주문 금액") @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "최대 주문 금액") @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        OrderSearchCondition condition = new OrderSearchCondition(
                storeKey, status, orderedFrom, orderedTo, minAmount, maxAmount);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        return ApiResponse.ok(
                PageResponse.of(orderService.search(condition, pageRequest), OrderResponse::from));
    }
}
