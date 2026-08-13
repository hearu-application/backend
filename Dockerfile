FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m appuser && \
    chown -R appuser:appgroup /app

USER appuser

# 힙은 절대값으로 고정한다. 퍼센트 방식은 컨테이너에 메모리 제한이 없으면 호스트 RAM을
# 기준으로 계산하는데, 이 서버(961Mi)에서는 그것이 초기 480Mi 커밋 / 최대 721Mi가 된다.
# 실사용과 무관하게 잡아둔 힙이 스왑으로 밀려나, 유휴 후 첫 요청이 이를 되읽는 비용을 냈다.
# 자세한 측정은 docs/plan/prod-request-latency.md 참고.
#
# GC 로그는 상시 유지한다. GC 문제는 사후 재구성이 불가능하다.
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
                       -Xms128m \
                       -Xmx320m \
                       -Duser.timezone=Asia/Seoul \
                       -Djava.security.egd=file:/dev/./urandom \
                       -Xlog:gc:stdout:time,level,tags"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
