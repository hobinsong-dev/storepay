package dev.hobin.storepay.merchant.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.application.dto.MerchantResponse;
import dev.hobin.storepay.merchant.domain.Merchant;
import dev.hobin.storepay.merchant.domain.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 응답 DTO 는 이 레이어에서 트랜잭션 안에 있을 때 만든다.
 *
 * <p>엔티티를 그대로 반환하면 컨트롤러가 매핑하는 시점에는 이미 세션이 닫혀 있어
 * 지연 로딩 필드에 접근하는 순간 LazyInitializationException 이 난다(open-in-view: false).
 * 더 나쁜 건 쓰기 API 에서다. DB 변경은 커밋됐는데 응답만 500 으로 나가 클라이언트와 상태가 어긋난다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantService {

    private final MerchantRepository merchantRepository;

    @Transactional
    public MerchantResponse register(String name, String businessNumber) {
        if (merchantRepository.existsByBusinessNumber(businessNumber)) {
            throw new BusinessException(ErrorCode.MERCHANT_BUSINESS_NUMBER_DUPLICATED);
        }
        return MerchantResponse.from(merchantRepository.save(Merchant.register(name, businessNumber)));
    }

    @Transactional
    public MerchantResponse activate(String merchantKey) {
        Merchant merchant = getEntityByKey(merchantKey);
        merchant.activate();
        return MerchantResponse.from(merchant);
    }

    public MerchantResponse getByKey(String merchantKey) {
        return MerchantResponse.from(getEntityByKey(merchantKey));
    }

    /** 다른 서비스가 연관관계를 맺을 때 쓰는 엔티티 조회. 호출 측 트랜잭션 안에서만 쓴다. */
    public Merchant getEntityByKey(String merchantKey) {
        return merchantRepository.findByMerchantKey(merchantKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_NOT_FOUND));
    }
}
