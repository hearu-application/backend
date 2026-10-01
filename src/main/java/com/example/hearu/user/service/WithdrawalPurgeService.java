package com.example.hearu.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.diary.service.DiaryService;
import com.example.hearu.user.domain.User;
import com.example.hearu.user.infrastructure.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 유예 기간이 지난 탈퇴 유저를 하드 삭제한다.
 * UserService에 두지 않는 이유: DiaryService가 이미 UserService를 주입받고 있어, UserService가
 * DiaryService를 부르면 순환 참조가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalPurgeService {

    // 한 번의 실행에서 처리할 최대 인원. 남은 대상은 다음 실행이 이어서 처리한다.
    private static final int PURGE_BATCH_SIZE = 500;

    private final UserRepository userRepository;
    private final DiaryService diaryService;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public List<Long> findPurgeTargetIds(LocalDateTime cutoff) {
        return userRepository.findPurgeTargetIds(cutoff, PageRequest.of(0, PURGE_BATCH_SIZE));
    }

    // 삭제했으면 true, 대상 조회 후 복구되어 빠졌으면 false.
    public boolean purge(Long userId, LocalDateTime cutoff) {
        // 1. 행 락을 잡고 대상인지 다시 확인 (조회 후 복구된 경우를 거른다)
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null || !user.isWithdrawnBefore(cutoff)) {
            log.debug("하드 삭제 대상에서 빠져 건너뜀. userId={}", userId);
            return false;
        }

        // 2. 일기·AI 응답·피드백 → refresh token → 유저 순으로 하드 삭제
        diaryService.hardDeleteAllByUserId(userId);
        refreshTokenService.deleteRefreshToken(userId);
        userRepository.delete(user);
        return true;
    }
}
