package dev.hobin.storepay.common.observability;

/**
 * 로그 추적 키 이름.
 *
 * <p>MDC 는 <b>스레드 로컬</b>이다. 넣은 쪽이 지우지 않으면 그 스레드를 재사용하는
 * 다음 요청의 로그에 남의 값이 찍힌다. HTTP 요청은 {@link RequestIdFilter} 가 바깥에서
 * 통째로 비우고, 스케줄러처럼 필터를 타지 않는 경로는 각자 {@code try/finally} 로 되돌린다.
 *
 * <p>키 이름을 상수로 모아 두는 이유는 로그 패턴({@code application.yml})과
 * 코드가 같은 문자열을 보게 하기 위해서다. 한쪽만 바꾸면 추적 키가 조용히 비어 나온다.
 */
public final class RequestContext {

    public static final String REQUEST_ID = "requestId";
    public static final String PAYMENT_KEY = "paymentKey";
    public static final String ORDER_KEY = "orderKey";

    private RequestContext() {
    }
}
