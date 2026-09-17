package com.example.hearu.common.cert;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.stereotype.Component;

/**
 * 실제로 서빙 중인 TLS 인증서의 만료일을 읽는다. 디스크의 PEM 파일이 아니라 핸드셰이크로 받은
 * 인증서를 보므로 "갱신은 됐지만 nginx 리로드가 안 된" 경우까지 잡는다. 목적이 신뢰 체인 검증이
 * 아니라 만료일 확인이라 trust-all + 호스트네임 검증 off로 핸드셰이크만 성립시킨다
 * (docs/plan/cert-expiry-monitoring.md 참고).
 */
@Component
public class TlsCertificateInspector {

    private static final TrustManager[] TRUST_ALL = {
        new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }
    };

    public Instant readNotAfter(String host, int port, String sniHost, Duration timeout) {
        int timeoutMillis = (int) timeout.toMillis();

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, TRUST_ALL, new SecureRandom());

            try (SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMillis);
                socket.setSoTimeout(timeoutMillis);

                SSLParameters sslParameters = socket.getSSLParameters();
                sslParameters.setServerNames(List.of(new SNIHostName(sniHost)));
                sslParameters.setEndpointIdentificationAlgorithm(null);
                socket.setSSLParameters(sslParameters);

                socket.startHandshake();

                X509Certificate leaf = (X509Certificate) socket.getSession().getPeerCertificates()[0];
                return leaf.getNotAfter().toInstant();
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new CertInspectionException(
                "TLS 인증서 조회 실패. host=%s, port=%d, sni=%s".formatted(host, port, sniHost),
                e
            );
        }
    }
}
