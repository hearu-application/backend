FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m appuser && \
    chown -R appuser:appgroup /app

USER appuser

# -Xlog는 진단용이다. JVM이 멈춘 시간(safepoint)과 GC 정지를 직접 재기 위한 것으로,
# 원인이 확정되면 제거한다.
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
                       -XX:MaxRAMPercentage=75.0 \
                       -XX:InitialRAMPercentage=50.0 \
                       -Duser.timezone=Asia/Seoul \
                       -Djava.security.egd=file:/dev/./urandom \
                       -Xlog:gc,safepoint:stdout:time,level,tags"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
