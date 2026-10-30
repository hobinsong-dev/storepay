package dev.hobin.storepay.store.domain;

import dev.hobin.storepay.common.entity.BaseTimeEntity;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.support.PublicKeyGenerator;
import dev.hobin.storepay.merchant.domain.Merchant;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "store")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_key", nullable = false, unique = true, length = 40)
    private String storeKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StoreStatus status;

    private Store(Merchant merchant, String name, String address) {
        this.storeKey = PublicKeyGenerator.generate("str");
        this.merchant = merchant;
        this.name = name;
        this.address = address;
        this.status = StoreStatus.OPEN;
    }

    public static Store open(Merchant merchant, String name, String address) {
        merchant.requireActive();
        return new Store(merchant, name, address);
    }

    public void close() {
        this.status = StoreStatus.CLOSED;
    }

    /** 주문을 받아도 되는 상태인지 확인한다. 매장과 소속 가맹점을 함께 본다. */
    public void requireOrderable() {
        this.merchant.requireActive();
        if (this.status != StoreStatus.OPEN) {
            throw new BusinessException(ErrorCode.STORE_NOT_ACTIVE);
        }
    }
}
