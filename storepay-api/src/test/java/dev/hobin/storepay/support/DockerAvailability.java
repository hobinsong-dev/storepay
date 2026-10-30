package dev.hobin.storepay.support;

import org.testcontainers.DockerClientFactory;

/** Docker 가 없는 환경에서 통합 테스트를 실패가 아니라 건너뛰기로 처리하기 위한 판정. */
public final class DockerAvailability {

    private static final boolean AVAILABLE = check();

    private DockerAvailability() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    private static boolean check() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
