# -------- Build --------
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Gradle 캐시 최적화
COPY gradlew settings.gradle build.gradle gradle.properties* ./
COPY gradle ./gradle
RUN chmod +x gradlew

# 소스 복사
COPY . .
# 의존성 미리 다운 (테스트 제외)
RUN ./gradlew clean bootJar -x test --no-daemon

# bootJar만 생성하면 불필요한 작업이 줄어듦
RUN ./gradlew clean bootJar -x test --no-daemon

# -------- Run --------
FROM openjdk:17-jdk-slim
WORKDIR /app

# 보안상 비루트 유저 사용
RUN useradd -ms /bin/bash appuser
USER appuser

# JAR 고정 이름으로 복사
COPY --from=build /app/build/libs/app.jar app.jar

# Spring Boot 실행
CMD ["java", "-jar", "app.jar"]
