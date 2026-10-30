package dev.hobin.storepay.merchant.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByMerchantKey(String merchantKey);

    boolean existsByBusinessNumber(String businessNumber);
}
