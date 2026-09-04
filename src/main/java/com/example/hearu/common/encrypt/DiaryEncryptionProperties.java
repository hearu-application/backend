package com.example.hearu.common.encrypt;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 컬럼 암호화 키 설정. 키는 KMS가 아니라 환경변수로 직접 주입되는 마스터 키다
 * (단일 VM 배포라 별도 시크릿 저장소가 없음 — docs/plan/diary-content-encryption.md 참고).
 *
 * <p>{@code keys}는 keyVersion(정수) → Base64(32byte) 키. 회전 시 새 keyVersion을 추가하고
 * activeVersion만 옮기면, 기존 버전으로 암호화된 값도 계속 복호화할 수 있다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "diary.encryption")
public class DiaryEncryptionProperties {

    private int activeVersion;
    private Map<Integer, String> keys = new HashMap<>();
}
