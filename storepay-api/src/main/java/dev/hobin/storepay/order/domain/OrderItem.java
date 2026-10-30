package dev.hobin.storepay.order.domain;

import dev.hobin.storepay.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목.
 *
 * <p>상품명과 단가를 주문 시점 값으로 복사해 둔다. 상품 마스터를 조인해서 금액을 계산하면
 * 나중에 가격이 바뀌었을 때 과거 주문의 금액과 영수증이 같이 바뀌어 버린다.
 */
@Getter
@Entity
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineAmount;

    private OrderItem(Product product, int quantity) {
        this.product = product;
        this.productName = product.getName();
        this.unitPrice = product.getUnitPrice();
        this.quantity = quantity;
        this.lineAmount = product.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
    }

    static OrderItem of(Product product, int quantity) {
        product.requireSellable();
        return new OrderItem(product, quantity);
    }

    void assignTo(Order order) {
        this.order = order;
    }
}
