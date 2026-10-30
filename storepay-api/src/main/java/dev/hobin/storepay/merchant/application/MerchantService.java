package dev.hobin.storepay.merchant.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.domain.Merchant;
import dev.hobin.storepay.merchant.domain.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantService {

    private final MerchantRepository merchantRepository;

    @Transactional
    public Merchant register(String name, String businessNumber) {
        if (merchantRepository.existsByBusinessNumber(businessNumber)) {
            throw new BusinessException(ErrorCode.MERCHANT_BUSINESS_NUMBER_DUPLICATED);
        }
        return merchantRepository.save(Merchant.register(name, businessNumber));
    }

    @Transactional
    public Merchant activate(String merchantKey) {
        Merchant merchant = getByKey(merchantKey);
        merchant.activate();
        return merchant;
    }

    public Merchant getByKey(String merchantKey) {
        return merchantRepository.findByMerchantKey(merchantKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_NOT_FOUND));
    }
}
