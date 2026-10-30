package dev.hobin.storepay.store.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByStoreKey(String storeKey);
}
