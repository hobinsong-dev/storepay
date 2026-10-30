package dev.hobin.storepay.order.domain;

import dev.hobin.storepay.catalog.domain.Product;
import dev.hobin.storepay.common.entity.BaseTimeEntity;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.support.PublicKeyGenerator;
import dev.hobin.storepay.store.domain.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 애그리게잇 루트.
 *
 * <p>주문 금액은 항목 합계로만 결정되고 외부에서 직접 설정할 수 없다. 클라이언트가 보낸 금액을
 * 믿으면 POS 단말을 조작해 1원 결제를 만들 수 있기 때문에, 서버가 상품 마스터를 보고 다시 계산한다.
 */
@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_key", nullable = false, unique = true, length = 40)
    private String orderKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> items = new ArrayList<>();

    /**
     * 낙관적 락. 같은 주문에 결제 승인과 취소가 동시에 들어오는 경우를 막는다.
     * POS는 단말이 여러 대라 중복 요청이 실제로 발생한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Order(Store store) {
        this.orderKey = PublicKeyGenerator.generate("ord");
        this.store = store;
        this.status = OrderStatus.CREATED;
        this.totalAmount = BigDecimal.ZERO;
    }

    public static Order place(Store store, List<OrderLine> lines) {
        store.requireOrderable();
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_EMPTY_ITEMS);
        }

        Order order = new Order(store);
        for (OrderLine line : lines) {
            order.addItem(line.product(), line.quantity());
        }
        return order;
    }

    private void addItem(Product product, int quantity) {
        if (!Objects.equals(product.getStore().getId(), this.store.getId())) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_STORE_MISMATCH,
                    "'%s' 상품은 이 매장의 상품이 아닙니다.".formatted(product.getName()));
        }
        OrderItem item = OrderItem.of(product, quantity);
        item.assignTo(this);
        this.items.add(item);
        this.totalAmount = this.totalAmount.add(item.getLineAmount());
    }

    public void markPaid() {
        if (this.status != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    "결제 가능한 상태가 아닙니다. 현재 상태: %s".formatted(this.status));
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (!this.status.isCancelable()) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    "취소 가능한 상태가 아닙니다. 현재 상태: %s".formatted(this.status));
        }
        this.status = OrderStatus.CANCELED;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    /** 주문 생성 입력. 상품 엔티티와 수량 한 쌍. */
    public record OrderLine(Product product, int quantity) {
        public OrderLine {
            if (quantity < 1) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "수량은 1 이상이어야 합니다.");
            }
        }
    }
}
