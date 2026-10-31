# FINTRIS Main 서버 이미지
# 1단계(build)에서 Gradle로 jar를 만들고, 2단계(run)에는 실행에 필요한 JRE와 jar만 남긴다.

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 목록이 바뀌지 않으면 이 층을 캐시로 재사용해서 빌드가 빨라진다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 테스트는 DB가 필요해서 이미지 빌드에서는 건너뛴다.
RUN ./gradlew bootJar -x test --no-daemon

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul

# root가 아닌 전용 사용자로 실행한다.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /app/build/libs/*.jar app.jar
USER app

EXPOSE 8080
# 컨테이너 메모리 한도의 75%까지만 힙으로 쓴다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
