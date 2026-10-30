package dev.hobin.storepay.common;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hobin.storepay.common.observability.RequestContext;
import dev.hobin.storepay.common.observability.RequestIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("요청 추적 ID")
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private MockHttpServletResponse filterWith(String inboundHeader, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (inboundHeader != null) {
            request.addHeader(RequestIdFilter.HEADER, inboundHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("헤더가 없으면 새로 발급하고 응답으로 돌려준다")
    void generatesWhenAbsent() throws Exception {
        MockHttpServletResponse response = filterWith(null, (req, res) -> {
            assertThat(MDC.get(RequestContext.REQUEST_ID)).isNotBlank();
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
    }

    /** POS → 중계 서버 → 결제 API 구간을 하나의 ID 로 묶어야 단말 쪽 문의를 추적할 수 있다. */
    @Test
    @DisplayName("클라이언트가 보낸 ID 는 그대로 이어받는다")
    void reusesInboundId() throws Exception {
        MockHttpServletResponse response = filterWith("pos-terminal-0001", (req, res) ->
                assertThat(MDC.get(RequestContext.REQUEST_ID)).isEqualTo("pos-terminal-0001"));

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("pos-terminal-0001");
    }

    /**
     * 받은 값은 로그 한 줄과 응답 헤더에 그대로 들어간다.
     * 개행을 통과시키면 가짜 로그 줄을 심거나 응답 헤더를 쪼갤 수 있다.
     */
    @Test
    @DisplayName("개행이나 특수문자가 섞인 ID 는 버리고 새로 발급한다")
    void rejectsUnsafeId() throws Exception {
        String injected = "abc\r\nX-Injected: evil";

        MockHttpServletResponse response = filterWith(injected, (req, res) -> {
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isNotEqualTo(injected)
                .matches("[A-Za-z0-9]+");
    }

    @Test
    @DisplayName("64자를 넘는 ID 는 버린다")
    void rejectsOverlongId() throws Exception {
        String overlong = "a".repeat(65);

        MockHttpServletResponse response = filterWith(overlong, (req, res) -> {
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotEqualTo(overlong);
    }

    /**
     * 톰캣은 스레드를 재사용한다. 요청이 끝날 때 비우지 않으면
     * 처리 도중 깊은 곳에서 넣은 결제 키가 다음 요청의 로그에 찍힌다.
     */
    @Test
    @DisplayName("요청이 끝나면 MDC 를 통째로 비운다")
    void clearsMdcAfterRequest() throws Exception {
        filterWith(null, (req, res) -> MDC.put(RequestContext.PAYMENT_KEY, "pay_leak_0001"));

        assertThat(MDC.get(RequestContext.PAYMENT_KEY)).isNull();
        assertThat(MDC.get(RequestContext.REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("처리 중 예외가 나도 MDC 를 비운다")
    void clearsMdcOnException() {
        try {
            filterWith(null, (req, res) -> {
                MDC.put(RequestContext.PAYMENT_KEY, "pay_leak_0002");
                throw new IllegalStateException("boom");
            });
        } catch (Exception ignored) {
            // 예외 전파 자체는 이 테스트의 관심사가 아니다.
        }

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
