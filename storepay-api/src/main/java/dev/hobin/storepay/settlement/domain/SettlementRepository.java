package dev.hobin.storepay.settlement.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findBySettlementKey(String settlementKey);

    List<Settlement> findByBusinessDateOrderByIdAsc(LocalDate businessDate);

    boolean existsByBusinessDate(LocalDate businessDate);
}
