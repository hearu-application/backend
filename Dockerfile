FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=build/libs/app.jar
COPY ${JAR_FILE} app.jar

RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -D appuser && \
    chown -R appuser:appgroup /app

USER appuser

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
                       -XX:MaxRAMPercentage=75.0 \
                       -XX:InitialRAMPercentage=50.0 \
                       -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
