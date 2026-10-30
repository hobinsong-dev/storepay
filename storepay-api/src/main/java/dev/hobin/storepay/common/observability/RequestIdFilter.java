package dev.hobin.storepay.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 추적 ID.
 *
 * <p>응답 헤더로 <b>돌려준다</b>. 결제 장애 문의는 "몇 시쯤 결제가 안 됐어요"로 들어오는데,
 * 그 시각대의 모든 로그를 뒤지는 것과 ID 하나로 찍는 것은 대응 시간이 자릿수로 다르다.
 *
 * <p>클라이언트가 보낸 값이 있으면 이어받는다. POS → 중계 서버 → 결제 API 로 흐르는 구간을
 * 하나의 ID 로 묶어야 "단말에서는 성공으로 보였다"는 문의를 추적할 수 있다.
 * 다만 받은 값을 그대로 쓰지는 않는다. 이 값은 로그 한 줄과 응답 헤더에 그대로 들어가므로,
 * 개행이 섞이면 가짜 로그 줄을 심거나 응답 헤더를 쪼갤 수 있다. 영숫자와 {@code -_} 만,
 * 길이 제한 안에서 통과시키고 나머지는 새로 발급한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final int MAX_LENGTH = 64;
    private static final java.util.regex.Pattern SAFE =
            java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,%d}".formatted(MAX_LENGTH));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(RequestContext.REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 요청이 끝나면 MDC 를 통째로 비운다. 이 필터가 가장 바깥이라 여기서 지우지 않으면,
            // 처리 도중 깊은 곳에서 넣은 paymentKey 같은 값이 그대로 남아
            // 톰캣이 이 스레드를 재사용하는 <b>다음 요청의 로그에 찍힌다</b>.
            // 키를 하나씩 지우는 방식은 새 키가 생길 때마다 빠뜨리게 된다.
            MDC.clear();
        }
    }

    private String resolve(String inbound) {
        if (inbound == null || !SAFE.matcher(inbound).matches()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return inbound;
    }
}
