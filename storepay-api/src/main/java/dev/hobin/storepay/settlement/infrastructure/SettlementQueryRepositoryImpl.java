package dev.hobin.storepay.settlement.infrastructure;

import static dev.hobin.storepay.order.domain.QOrder.order;
import static dev.hobin.storepay.payment.domain.QPayment.payment;
import static dev.hobin.storepay.payment.domain.QPaymentCancel.paymentCancel;
import static dev.hobin.storepay.store.domain.QStore.store;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.hobin.storepay.payment.domain.PaymentStatus;
import dev.hobin.storepay.settlement.domain.SettlementQueryRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementQueryRepositoryImpl implements SettlementQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 승인 시각이 영업일 창 안에 드는 결제를 가맹점까지 조인해서 가져온다.
     *
     * <p>FAILED 와 READY 는 제외한다. 승인된 적이 없으므로 결제사 내역에도 없고 정산 대상도 아니다.
     * 반대로 CANCELED 는 <b>포함</b>한다 — 승인은 분명히 일어났고, 결제사 내역에도 남아 있어
     * 빼 버리면 전부 MISSING_IN_INTERNAL 로 잡힌다.
     */
    @Override
    public List<ApprovedPayment> findApprovedBetween(Instant from, Instant to) {
        return queryFactory
                .select(Projections.constructor(ApprovedPayment.class,
                        store.merchant.id,
                        payment.paymentKey,
                        payment.pgTransactionId,
                        payment.method,
                        payment.status,
                        payment.totalAmount,
                        payment.canceledAmount))
                .from(payment)
                .join(payment.order, order)
                .join(order.store, store)
                .where(payment.approvedAt.goe(from),
                        payment.approvedAt.lt(to),
                        payment.status.ne(PaymentStatus.READY),
                        payment.status.ne(PaymentStatus.FAILED))
                .orderBy(payment.id.asc())
                .fetch();
    }

    /**
     * 취소는 취소된 날짜에 잡는다. 승인일로 잡으면 이미 지급이 끝난 날짜의 정산을 다시 열어야 한다.
     * 그래서 어제 승인 · 오늘 취소 건은 오늘 정산에서 차감되고, 당일 지급액이 음수가 될 수 있다.
     */
    @Override
    public List<CanceledAmount> findCanceledBetween(Instant from, Instant to) {
        return queryFactory
                .select(Projections.constructor(CanceledAmount.class,
                        store.merchant.id,
                        payment.method,
                        paymentCancel.amount))
                .from(paymentCancel)
                .join(paymentCancel.payment, payment)
                .join(payment.order, order)
                .join(order.store, store)
                .where(paymentCancel.canceledAt.goe(from),
                        paymentCancel.canceledAt.lt(to))
                .orderBy(paymentCancel.id.asc())
                .fetch();
    }
}
