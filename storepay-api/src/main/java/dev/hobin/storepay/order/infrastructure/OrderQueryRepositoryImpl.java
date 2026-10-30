package dev.hobin.storepay.order.infrastructure;

import static dev.hobin.storepay.catalog.domain.QProduct.product;
import static dev.hobin.storepay.order.domain.QOrder.order;
import static dev.hobin.storepay.order.domain.QOrderItem.orderItem;
import static dev.hobin.storepay.store.domain.QStore.store;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.hobin.storepay.order.domain.Order;
import dev.hobin.storepay.order.domain.OrderQueryRepository;
import dev.hobin.storepay.order.domain.OrderSearchCondition;
import dev.hobin.storepay.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepositoryImpl implements OrderQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 조건 검색 + 페이징.
     *
     * <p>컬렉션(order_item)을 한 번에 fetch join 하면 행이 뻥튀기되어 페이징이 깨진다.
     * 그래서 1) 조건에 맞는 주문 ID 페이지를 먼저 뽑고 2) 그 ID들로 항목을 fetch join 해서
     * 채우는 두 단계로 나눴다. 쿼리는 2번이지만 N+1도 없고 페이징도 정확하다.
     */
    @Override
    public Page<Order> search(OrderSearchCondition condition, Pageable pageable) {
        List<Long> orderIds = queryFactory
                .select(order.id)
                .from(order)
                .join(order.store, store)
                .where(buildPredicate(condition))
                .orderBy(order.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (orderIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Order> content = queryFactory
                .selectFrom(order)
                .join(order.store, store).fetchJoin()
                .leftJoin(order.items, orderItem).fetchJoin()
                .leftJoin(orderItem.product, product).fetchJoin()
                .where(order.id.in(orderIds))
                .orderBy(order.id.desc())
                .distinct()
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(order.count())
                .from(order)
                .join(order.store, store)
                .where(buildPredicate(condition));

        // 마지막 페이지이거나 첫 페이지가 다 안 찼으면 count 쿼리를 아예 날리지 않는다.
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanBuilder buildPredicate(OrderSearchCondition condition) {
        return new BooleanBuilder()
                .and(storeKeyEq(condition.storeKey()))
                .and(statusEq(condition.status()))
                .and(orderedBetween(condition.orderedFrom(), condition.orderedTo()))
                .and(amountGoe(condition.minAmount()))
                .and(amountLoe(condition.maxAmount()));
    }

    private BooleanExpression storeKeyEq(String storeKey) {
        return storeKey == null ? null : store.storeKey.eq(storeKey);
    }

    private BooleanExpression statusEq(OrderStatus status) {
        return status == null ? null : order.status.eq(status);
    }

    private BooleanExpression orderedBetween(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        if (from == null) {
            return order.createdAt.loe(to);
        }
        if (to == null) {
            return order.createdAt.goe(from);
        }
        return order.createdAt.between(from, to);
    }

    private BooleanExpression amountGoe(BigDecimal minAmount) {
        return minAmount == null ? null : order.totalAmount.goe(minAmount);
    }

    private BooleanExpression amountLoe(BigDecimal maxAmount) {
        return maxAmount == null ? null : order.totalAmount.loe(maxAmount);
    }
}
