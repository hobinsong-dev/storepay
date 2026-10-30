# ---------- 1단계: 애플리케이션 빌드 ----------
# Temurin 의 alpine 태그는 amd64 전용이라 Apple Silicon 에서 빌드가 깨진다.
# Corretto alpine 은 amd64/arm64 를 모두 제공한다.
FROM amazoncorretto:17-alpine AS builder
WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY storepay-mock-pg/build.gradle.kts storepay-mock-pg/

# 의존성만 먼저 받아 레이어로 굳힌다. 소스만 바뀌면 이 레이어는 캐시된다.
RUN ./gradlew :storepay-mock-pg:dependencies --no-daemon > /dev/null 2>&1 || true

COPY storepay-mock-pg/src storepay-mock-pg/src
RUN ./gradlew :storepay-mock-pg:bootJar --no-daemon -x test

# ---------- 2단계: 애플리케이션에 필요한 모듈만 담은 런타임 생성 ----------
# 풀 JDK 를 그대로 실행 이미지로 쓰면 576MB 가 된다. 쓰지도 않는 컴파일러와 도구가 대부분이다.
# jlink 로 실제 필요한 모듈만 골라 런타임을 새로 만든다.
FROM amazoncorretto:17-alpine AS jre-builder
RUN apk add --no-cache binutils
RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.ec,jdk.jfr,jdk.management,jdk.unsupported \
    --strip-debug --no-man-pages --no-header-files --compress=2 \
    --output /javaruntime

# ---------- 3단계: 실행 ----------
FROM alpine:3.20
WORKDIR /app

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-builder /javaruntime ${JAVA_HOME}

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /workspace/storepay-mock-pg/build/libs/storepay-mock-pg.jar app.jar

EXPOSE 8090

# 컨테이너 메모리 한도를 JVM 이 인식하도록 맡긴다. -Xmx 고정은 한도 변경 시 같이 안 따라간다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"


ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
