package dev.hobin.storepay.merchant.domain;

import dev.hobin.storepay.common.entity.BaseTimeEntity;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.common.support.PublicKeyGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "merchant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_key", nullable = false, unique = true, length = 40)
    private String merchantKey;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "business_number", nullable = false, unique = true, length = 20)
    private String businessNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MerchantStatus status;

    private Merchant(String name, String businessNumber) {
        this.merchantKey = PublicKeyGenerator.generate("mch");
        this.name = name;
        this.businessNumber = businessNumber;
        this.status = MerchantStatus.PENDING;
    }

    public static Merchant register(String name, String businessNumber) {
        return new Merchant(name, businessNumber);
    }

    public void activate() {
        if (this.status == MerchantStatus.TERMINATED) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_ACTIVE, "해지된 가맹점은 다시 활성화할 수 없습니다.");
        }
        this.status = MerchantStatus.ACTIVE;
    }

    public void suspend() {
        this.status = MerchantStatus.SUSPENDED;
    }

    /** 결제가 일어나도 되는 상태인지 확인한다. 아니라면 예외를 던진다. */
    public void requireActive() {
        if (this.status != MerchantStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_ACTIVE,
                    "가맹점 상태가 %s 입니다.".formatted(this.status));
        }
    }
}
