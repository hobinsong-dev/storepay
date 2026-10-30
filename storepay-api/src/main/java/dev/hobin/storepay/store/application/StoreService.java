package dev.hobin.storepay.store.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.store.domain.Store;
import dev.hobin.storepay.store.domain.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final MerchantService merchantService;

    @Transactional
    public Store open(String merchantKey, String name, String address) {
        return storeRepository.save(
                Store.open(merchantService.getByKey(merchantKey), name, address));
    }

    public Store getByKey(String storeKey) {
        return storeRepository.findByStoreKey(storeKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
