package dev.hobin.storepay.settlement.domain;

public enum ReconciliationStatus {
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
