package dev.hobin.storepay.catalog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductKey(String productKey);

    List<Product> findAllByProductKeyIn(List<String> productKeys);
}
