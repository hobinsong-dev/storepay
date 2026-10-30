package dev.hobin.storepay.catalog.domain;

import dev.hobin.storepay.common.entity.BaseTimeEntity;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.support.PublicKeyGenerator;
import dev.hobin.storepay.store.domain.Store;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_key", nullable = false, unique = true, length = 40)
    private String productKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    private Product(Store store, String name, BigDecimal unitPrice) {
        this.productKey = PublicKeyGenerator.generate("prd");
        this.store = store;
        this.name = name;
        this.unitPrice = unitPrice;
        this.status = ProductStatus.ON_SALE;
    }

    public static Product create(Store store, String name, BigDecimal unitPrice) {
        return new Product(store, name, unitPrice);
    }

    public void changePrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void changeStatus(ProductStatus status) {
        this.status = status;
    }

    public void requireSellable() {
        if (this.status != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_SELLABLE,
                    "'%s' 상품은 현재 %s 상태입니다.".formatted(this.name, this.status));
        }
    }
}
