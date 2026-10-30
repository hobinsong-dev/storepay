package dev.hobin.storepay.catalog.application;

import dev.hobin.storepay.catalog.domain.Product;
import dev.hobin.storepay.catalog.domain.ProductRepository;
import dev.hobin.storepay.catalog.domain.ProductStatus;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.store.application.StoreService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreService storeService;

    @Transactional
    public Product create(String storeKey, String name, BigDecimal unitPrice) {
        return productRepository.save(
                Product.create(storeService.getByKey(storeKey), name, unitPrice));
    }

    @Transactional
    public Product changeStatus(String productKey, ProductStatus status) {
        Product product = getByKey(productKey);
        product.changeStatus(status);
        return product;
    }

    public Product getByKey(String productKey) {
        return productRepository.findByProductKey(productKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * 여러 상품 키를 한 번에 조회한다. 주문 생성 시 항목마다 단건 조회하면 N+1이 되므로
     * IN 절로 한 번에 읽고, 요청에 있었지만 존재하지 않는 키가 있으면 즉시 실패시킨다.
     */
    public Map<String, Product> getAllByKeys(List<String> productKeys) {
        Map<String, Product> found = productRepository.findAllByProductKeyIn(productKeys).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getProductKey, Function.identity()));

        List<String> missing = productKeys.stream().filter(key -> !found.containsKey(key)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                    "존재하지 않는 상품: %s".formatted(String.join(", ", missing)));
        }
        return found;
    }
}
