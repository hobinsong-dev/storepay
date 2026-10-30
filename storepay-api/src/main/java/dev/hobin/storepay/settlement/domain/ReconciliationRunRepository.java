package dev.hobin.storepay.settlement.domain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    Optional<ReconciliationRun> findByRunKey(String runKey);

    Optional<ReconciliationRun> findByBusinessDate(LocalDate businessDate);
}
