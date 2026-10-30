package dev.hobin.storepay.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문 조건 검색 포트.
 *
 * <p>Spring Data 의 커스텀 프래그먼트(`XxxRepositoryImpl`)로 붙이지 않았다. 그 방식은 구현체가
 * 인터페이스와 같은 패키지에 있어야 탐색되기 때문에, QueryDSL 코드가 도메인 패키지로 끌려 들어온다.
 * 포트는 도메인에 두고 QueryDSL 구현은 infrastructure 에 두는 편이 의존 방향이 분명하다.
 */
public interface OrderQueryRepository {

    Page<Order> search(OrderSearchCondition condition, Pageable pageable);
}
