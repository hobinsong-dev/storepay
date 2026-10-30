package dev.hobin.mockpg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 결제사 역할을 대신하는 모의 서버.
 *
 * <p>storepay 가 "진짜 HTTP 로 외부와 통신하는" 경로를 끝까지 돌려보기 위한 것이다.
 * 승인·취소 응답을 주고, 비동기로 서명된 웹훅을 되쏜다.
 * 실패·지연 경로도 재현할 수 있어야 연동 코드의 타임아웃과 재시도를 실제로 검증할 수 있다.
 */
@SpringBootApplication
public class MockPgApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockPgApplication.class, args);
    }
}
