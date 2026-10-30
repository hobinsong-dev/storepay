package dev.hobin.mockpg;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 결제사 원장.
 *
 * <p>대사의 상대편 장부 역할만 한다. 메모리에 두므로 프로세스를 내리면 사라진다 —
 * 모의 결제사의 목적은 "내역을 영구 보관하는 것"이 아니라
 * "우리 쪽 대사 로직이 상대 장부와 맞춰지는지"를 확인하는 것이다.
 */
@Component
public class PgLedger {

    private final Map<String, Entry> byPaymentKey = new ConcurrentHashMap<>();
    private final Map<String, String> paymentKeyByTransactionId = new ConcurrentHashMap<>();

    public void recordApproval(String paymentKey, String transactionId, BigDecimal amount, Instant approvedAt) {
        byPaymentKey.put(paymentKey, new Entry(paymentKey, transactionId, amount, approvedAt));
        paymentKeyByTransactionId.put(transactionId, paymentKey);
    }

    /** 취소 요청에는 결제사 거래번호만 실려 오므로 역인덱스로 원장을 찾는다. */
    public void recordCancel(String transactionId, BigDecimal amount) {
        String paymentKey = paymentKeyByTransactionId.get(transactionId);
        if (paymentKey == null) {
            return;
        }
        Entry entry = byPaymentKey.get(paymentKey);
        if (entry != null) {
            entry.cancel(amount);
        }
    }

    /** 승인일이 그 영업일에 드는 거래만 내려준다. 취소분은 잔여 금액에 이미 반영돼 있다. */
    public List<Transaction> transactionsOn(LocalDate businessDate, ZoneId zone) {
        List<Transaction> transactions = new ArrayList<>();
        for (Entry entry : byPaymentKey.values()) {
            if (entry.approvedAt.atZone(zone).toLocalDate().equals(businessDate)) {
                transactions.add(entry.toTransaction());
            }
        }
        transactions.sort(Comparator.comparing(Transaction::paymentKey));
        return transactions;
    }

    public record Transaction(String paymentKey, String pgTransactionId, BigDecimal amount, String status) {
    }

    private static final class Entry {
        private final String paymentKey;
        private final String transactionId;
        private final BigDecimal amount;
        private final Instant approvedAt;
        private BigDecimal canceled = BigDecimal.ZERO;

        private Entry(String paymentKey, String transactionId, BigDecimal amount, Instant approvedAt) {
            this.paymentKey = paymentKey;
            this.transactionId = transactionId;
            this.amount = amount;
            this.approvedAt = approvedAt;
        }

        private synchronized void cancel(BigDecimal cancelAmount) {
            this.canceled = this.canceled.add(cancelAmount);
        }

        private synchronized Transaction toTransaction() {
            BigDecimal remaining = amount.subtract(canceled);
            String status;
            if (canceled.compareTo(BigDecimal.ZERO) == 0) {
                status = "APPROVED";
            } else if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                status = "CANCELED";
            } else {
                status = "PARTIAL_CANCELED";
            }
            return new Transaction(paymentKey, transactionId, remaining, status);
        }
    }
}
