package dev.hobin.storepay.store.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.store.application.dto.StoreResponse;
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
    public StoreResponse open(String merchantKey, String name, String address) {
        Store store = storeRepository.save(
                Store.open(merchantService.getEntityByKey(merchantKey), name, address));
        return StoreResponse.from(store);
    }

    public StoreResponse getByKey(String storeKey) {
        return StoreResponse.from(getEntityByKey(storeKey));
    }

    public Store getEntityByKey(String storeKey) {
        return storeRepository.findByStoreKey(storeKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
