package com.example.hearu.ai.character.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.hearu.ai.character.domain.Companion;
import com.example.hearu.ai.character.domain.error.CompanionErrorCode;
import com.example.hearu.ai.character.dto.response.CompanionResponse;
import com.example.hearu.ai.character.infrastructure.CompanionRepository;
import com.example.hearu.common.util.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
public class CompanionServiceTest {

    @Mock
    CompanionRepository companionRepository;

    @InjectMocks
    CompanionService companionService;

    private Companion companion1;

    @BeforeEach
    void setUp() {
        companion1 = Companion.create("루나", "따뜻한 친구", "페르소나1", "규칙1", "예시1");
        ReflectionTestUtils.setField(companion1, "id", 1L);
    }

    @Nested
    @DisplayName("캐릭터 전체 목록 조회")
    class GetAllCompanions {

        private Companion companion2;

        @BeforeEach
        void setUp() {
            companion2 = Companion.create("솔", "활발한 친구", "페르소나2", "규칙2", "예시2");
            ReflectionTestUtils.setField(companion2, "id", 2L);
        }

        @Test
        @DisplayName("캐릭터가 없는 경우, 빈 리스트 반환")
        void empty_list() {
            given(companionRepository.findAll()).willReturn(List.of());

            List<CompanionResponse> result = companionService.getAllCompanions();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(companionRepository.findAll()).willReturn(List.of(companion1, companion2));

            List<CompanionResponse> result = companionService.getAllCompanions();

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().companionId()).isEqualTo(companion1.getId());
            assertThat(result.getFirst().name()).isEqualTo(companion1.getName());
            assertThat(result.getFirst().description()).isEqualTo(companion1.getDescription());
            assertThat(result.get(1).companionId()).isEqualTo(companion2.getId());
            assertThat(result.get(1).name()).isEqualTo(companion2.getName());
            assertThat(result.get(1).description()).isEqualTo(companion2.getDescription());
        }
    }

    @Nested
    @DisplayName("기본 캐릭터 조회")
    class GetDefaultOrThrow {

        @Test
        @DisplayName("기본 캐릭터가 없는 경우, 예외 처리")
        void companion_not_found() {
            given(companionRepository.findByIsDefaultTrue()).willReturn(Optional.empty());

            assertThatThrownBy(() -> companionService.getDefaultOrThrow())
                .isInstanceOf(BusinessException.class)
                .hasMessage(CompanionErrorCode.COMPANION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(companionRepository.findByIsDefaultTrue()).willReturn(Optional.of(companion1));

            Companion result = companionService.getDefaultOrThrow();

            assertThat(result).isSameAs(companion1);
        }
    }

    @Nested
    @DisplayName("ID로 캐릭터 조회")
    class GetOrThrow {

        @Test
        @DisplayName("캐릭터가 없는 경우, 예외 처리")
        void companion_not_found() {
            given(companionRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> companionService.getOrThrow(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CompanionErrorCode.COMPANION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(companionRepository.findById(1L)).willReturn(Optional.of(companion1));

            Companion result = companionService.getOrThrow(1L);

            assertThat(result).isSameAs(companion1);
        }
    }
}
