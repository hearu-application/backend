package com.example.hearu.common.encrypt;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

/**
 * 일기·AI 응답 본문 컬럼 암호화 컨버터. {@code Diary.content}와 {@code AiResponse.content}가
 * 이 컨버터를 공유한다(재사용 근거는 docs/plan/diary-content-encryption.md 참고).
 * 아직 어느 엔티티에도 {@code @Convert}로 붙어 있지 않다 — 붙이는 시점(백필 이후)에 별도로 진행한다.
 *
 * <p>알고리즘은 AES-256-GCM. IV는 암호화마다 새로 난수 생성하며, 저장 포맷은
 * {@code Base64( keyVersion(1B) || iv(12B) || ciphertext || tag(16B) )}다. keyVersion을
 * 앞에 남겨 여러 키 버전이 공존해도(회전 중) 복호화 시 어떤 키를 쓸지 판별할 수 있게 한다.
 *
 * <p>{@code null}은 그대로 통과시킨다 — {@code ai_response.content}가 PENDING/FAILED 상태에서
 * null이기 때문이다({@code diary.content}는 NOT NULL이라 이 분기를 타지 않는다).
 */
@Converter
@Component
@RequiredArgsConstructor
public class ContentCryptoConverter implements AttributeConverter<String, String> {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int VERSION_LENGTH_BYTES = 1;
    private static final int MIN_PAYLOAD_LENGTH_BYTES =
        VERSION_LENGTH_BYTES + GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8);

    private final DiaryEncryptionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) {
            return null;
        }

        int version = properties.getActiveVersion();
        SecretKey key = resolveKey(version);

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        byte[] ciphertext = doCipher(Cipher.ENCRYPT_MODE, key, iv, plain.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buffer = ByteBuffer.allocate(VERSION_LENGTH_BYTES + iv.length + ciphertext.length);
        buffer.put((byte) version);
        buffer.put(iv);
        buffer.put(ciphertext);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }

        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(stored);
        } catch (IllegalArgumentException e) {
            throw new ContentCryptoException("저장된 값이 올바른 Base64 형식이 아닙니다.", e);
        }

        if (raw.length < MIN_PAYLOAD_LENGTH_BYTES) {
            throw new ContentCryptoException("저장된 값의 길이가 암호화 포맷 최소 길이보다 짧습니다.");
        }

        int version = raw[0] & 0xFF;
        SecretKey key = resolveKey(version);

        byte[] iv = Arrays.copyOfRange(raw, VERSION_LENGTH_BYTES, VERSION_LENGTH_BYTES + GCM_IV_LENGTH_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(raw, VERSION_LENGTH_BYTES + GCM_IV_LENGTH_BYTES, raw.length);

        byte[] plain = doCipher(Cipher.DECRYPT_MODE, key, iv, ciphertext);

        return new String(plain, StandardCharsets.UTF_8);
    }

    private byte[] doCipher(int mode, SecretKey key, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            // GCM은 인증 태그가 있어 저장값이 변조되면 AEADBadTagException으로 여기서 걸린다.
            throw new ContentCryptoException("본문 암복호화에 실패했습니다.", e);
        }
    }

    private SecretKey resolveKey(int version) {
        String base64Key = properties.getKeys().get(version);
        if (base64Key == null || base64Key.isBlank()) {
            throw new ContentCryptoException("keyVersion=" + version + "에 해당하는 암호화 키가 설정되어 있지 않습니다.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new ContentCryptoException("keyVersion=" + version + " 키가 올바른 Base64 형식이 아닙니다.", e);
        }

        if (keyBytes.length != 32) {
            throw new ContentCryptoException("keyVersion=" + version + " 키 길이가 256bit(32byte)가 아닙니다.");
        }

        return new SecretKeySpec(keyBytes, "AES");
    }
}
