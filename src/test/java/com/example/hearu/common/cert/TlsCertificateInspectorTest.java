package com.example.hearu.common.cert;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// TLS 소켓 경로는 실서버 의존이라 얇게: 연결 실패가 CertInspectionException으로 감싸지는지만 확인한다.
class TlsCertificateInspectorTest {

    private final TlsCertificateInspector inspector = new TlsCertificateInspector();

    @Test
    @DisplayName("연결할 수 없는 대상이면 CertInspectionException으로 감싼다")
    void wrapsConnectionFailure() {
        assertThatThrownBy(() ->
            inspector.readNotAfter("127.0.0.1", 1, "example.com", Duration.ofMillis(500))
        ).isInstanceOf(CertInspectionException.class);
    }
}
