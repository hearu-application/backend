package com.example.hearu.user.infrastructure;

import com.example.hearu.auth.domain.ProviderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.hearu.user.domain.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType provider, String sub);

    Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);

    // 유예 중인 탈퇴 계정 중 가장 최근 것을 행 락과 함께 조회한다. 락은 정리 스케줄러의 하드 삭제와
    // 복구가 같은 행에서 엇갈리지 않게 직렬화한다.
    // LIKE 이스케이프 문자는 '!'를 쓴다. '\'는 MySQL 문자열 리터럴에서도 이스케이프로 해석돼 꼬일 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u from User u
            where u.provider = :provider
              and u.providerUserId like :pattern escape '!'
              and u.deletedAt >= :cutoff
            order by u.deletedAt desc
            """)
    List<User> findRestorableForUpdate(
            @Param("provider") ProviderType provider,
            @Param("pattern") String pattern,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    default Optional<User> findLatestRestorableForUpdate(ProviderType provider, String sub, LocalDateTime cutoff) {
        String pattern = escapeLike(User.withdrawnProviderUserIdPrefix(sub)) + "%";
        return findRestorableForUpdate(provider, pattern, cutoff, PageRequest.of(0, 1)).stream().findFirst();
    }

    // 유예가 끝난 탈퇴 계정 id. 오래된 순으로 잘라 한 번의 실행이 무한정 길어지지 않게 한다.
    @Query("select u.userId from User u where u.deletedAt < :cutoff order by u.deletedAt")
    List<Long> findPurgeTargetIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
