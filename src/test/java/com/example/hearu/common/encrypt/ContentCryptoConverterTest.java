package com.example.hearu.common.encrypt;

import static org.assertj.core.api.Assertions.*;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("본문 컬럼 암호화 컨버터")
public class ContentCryptoConverterTest {

    private static final String TEST_KEY_V1 =
        Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String TEST_KEY_V2 =
        Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private DiaryEncryptionProperties properties;
    private ContentCryptoConverter converter;

    @BeforeEach
    void setUp() {
        properties = new DiaryEncryptionProperties();
        properties.setActiveVersion(1);
        properties.setKeys(Map.of(1, TEST_KEY_V1, 2, TEST_KEY_V2));
        converter = new ContentCryptoConverter(properties);
    }

    @Nested
    @DisplayName("왕복(round trip)")
    class RoundTrip {

        @Test
        @DisplayName("암호화 후 복호화하면 원본 평문과 같다")
        void encrypt_then_decrypt_restores_plain() {
            String plain = "오늘은 우울했다. 아무것도 하기 싫었다.";

            String encrypted = converter.convertToDatabaseColumn(plain);
            String decrypted = converter.convertToEntityAttribute(encrypted);

            assertThat(decrypted).isEqualTo(plain);
        }

        @Test
        @DisplayName("null을 암호화하면 null을 그대로 반환한다")
        void encrypt_null_passes_through() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("null을 복호화하면 null을 그대로 반환한다")
        void decrypt_null_passes_through() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }
    }

    @Nested
    @DisplayName("저장값에 평문이 노출되지 않는다")
    class NoPlaintextLeak {

        @Test
        @DisplayName("암호화된 값은 원본 평문 문자열을 포함하지 않는다")
        void encrypted_value_does_not_contain_plaintext() {
            String plain = "민감한 일기 내용입니다";

            String encrypted = converter.convertToDatabaseColumn(plain);

            assertThat(encrypted).doesNotContain(plain);
        }

        @Test
        @DisplayName("암호화된 값은 평문과 다르다")
        void encrypted_value_differs_from_plaintext() {
            String plain = "abc";

            String encrypted = converter.convertToDatabaseColumn(plain);

            assertThat(encrypted).isNotEqualTo(plain);
        }
    }

    @Nested
    @DisplayName("IV 비결정성")
    class IvNonDeterminism {

        @Test
        @DisplayName("같은 평문을 두 번 암호화하면 서로 다른 암호문이 나온다")
        void same_plaintext_encrypts_differently_each_time() {
            String plain = "같은 내용을 반복 작성";

            String encryptedFirst = converter.convertToDatabaseColumn(plain);
            String encryptedSecond = converter.convertToDatabaseColumn(plain);

            assertThat(encryptedFirst).isNotEqualTo(encryptedSecond);
            assertThat(converter.convertToEntityAttribute(encryptedFirst)).isEqualTo(plain);
            assertThat(converter.convertToEntityAttribute(encryptedSecond)).isEqualTo(plain);
        }
    }

    @Nested
    @DisplayName("무결성(변조 탐지)")
    class TamperDetection {

        @Test
        @DisplayName("저장값 1비트가 변조되면 복호화가 실패한다")
        void single_bit_flip_fails_decryption() {
            String encrypted = converter.convertToDatabaseColumn("변조 테스트 대상 평문");
            byte[] raw = Base64.getDecoder().decode(encrypted);
            // version(1B) + iv(12B) 뒤, 암호문 영역의 첫 바이트를 변조한다.
            int ciphertextStartIndex = 1 + 12;
            raw[ciphertextStartIndex] ^= 0x01;
            String tampered = Base64.getEncoder().encodeToString(raw);

            assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(ContentCryptoException.class);
        }

        @Test
        @DisplayName("Base64 형식이 아닌 값을 복호화하면 예외가 발생한다")
        void invalid_base64_fails_decryption() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-valid-base64!!"))
                .isInstanceOf(ContentCryptoException.class);
        }
    }

    @Nested
    @DisplayName("키 관리")
    class KeyManagement {

        @Test
        @DisplayName("설정되지 않은 keyVersion으로 암호화된 값을 복호화하면 예외가 발생한다")
        void unknown_key_version_fails_decryption() {
            String encrypted = converter.convertToDatabaseColumn("테스트");
            byte[] raw = Base64.getDecoder().decode(encrypted);
            raw[0] = (byte) 99;
            String tampered = Base64.getEncoder().encodeToString(raw);

            assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(ContentCryptoException.class);
        }

        @Test
        @DisplayName("키 회전 후에도 이전 버전 키로 암호화된 값을 복호화할 수 있다")
        void old_key_version_still_decryptable_after_rotation() {
            String encryptedWithV1 = converter.convertToDatabaseColumn("회전 이전 작성된 일기");

            properties.setActiveVersion(2);

            assertThat(converter.convertToEntityAttribute(encryptedWithV1)).isEqualTo("회전 이전 작성된 일기");
        }
    }
}
