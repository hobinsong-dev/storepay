# 빌드와 실행 이미지를 분리한다. 최종 이미지에 JDK와 Gradle 캐시를 남기지 않기 위함.
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY storepay-api/build.gradle.kts storepay-api/

# 의존성만 먼저 받아 레이어로 굳힌다. 소스만 바뀌면 이 레이어는 캐시된다.
RUN ./gradlew :storepay-api:dependencies --no-daemon > /dev/null 2>&1 || true

COPY storepay-api/src storepay-api/src
RUN ./gradlew :storepay-api:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /workspace/storepay-api/build/libs/storepay-api.jar app.jar

EXPOSE 8080

# 컨테이너 메모리 한도를 JVM이 인식하도록 맡긴다. -Xmx 고정은 한도 변경 시 같이 안 따라간다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
