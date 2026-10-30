package dev.hobin.storepay.settlement.domain;

import dev.hobin.storepay.common.entity.BaseTimeEntity;
import dev.hobin.storepay.common.support.PublicKeyGenerator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루치 대사 실행.
 *
 * <p>대사는 틀린 것을 <b>고치는</b> 일이 아니라 <b>찾아내는</b> 일이다. 금액이 어긋났을 때
 * 어느 쪽이 옳은지는 결제사 원장, 매입사 통보, 고객 문의를 함께 봐야 정해진다.
 * 그래서 이 애그리게잇은 불일치를 기록만 하고 결제 상태를 건드리지 않는다.
 */
@Getter
@Entity
@Table(name = "reconciliation_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_key", nullable = false, unique = true, length = 40)
    private String runKey;

    @Column(name = "business_date", nullable = false, unique = true)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "internal_count", nullable = false)
    private int internalCount;

    @Column(name = "pg_count", nullable = false)
    private int pgCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "mismatch_count", nullable = false)
    private int mismatchCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ReconciliationMismatch> mismatches = new ArrayList<>();

    private ReconciliationRun(LocalDate businessDate, Instant startedAt) {
        this.runKey = PublicKeyGenerator.generate("rec");
        this.businessDate = businessDate;
        this.status = ReconciliationStatus.RUNNING;
        this.startedAt = startedAt;
    }

    public static ReconciliationRun start(LocalDate businessDate, Instant startedAt) {
        return new ReconciliationRun(businessDate, startedAt);
    }

    public void addMismatch(ReconciliationMismatch mismatch) {
        this.mismatches.add(mismatch);
    }

    public void complete(int internalCount, int pgCount, int matchedCount, Instant finishedAt) {
        this.internalCount = internalCount;
        this.pgCount = pgCount;
        this.matchedCount = matchedCount;
        this.mismatchCount = this.mismatches.size();
        this.status = ReconciliationStatus.COMPLETED;
        this.finishedAt = finishedAt;
    }

    public void fail(String reason, Instant finishedAt) {
        this.status = ReconciliationStatus.FAILED;
        this.failureReason = reason;
        this.finishedAt = finishedAt;
    }

    /** 불일치가 하나라도 있으면 정산을 막는다. 어긋난 숫자로 돈을 내보내면 되돌리기 어렵다. */
    public boolean isClean() {
        return this.status.isCompleted() && this.mismatchCount == 0;
    }

    public List<ReconciliationMismatch> getMismatches() {
        return Collections.unmodifiableList(this.mismatches);
    }
}
