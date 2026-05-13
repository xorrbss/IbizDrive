package com.ibizdrive.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 사용자 storage quota 가드 + 사용량 증분 — quota mutation Phase 5 (`docs/04 §6.1`).
 *
 * <p>{@link com.ibizdrive.team.TeamArchiveGuard} 패턴 답습 — domain repository를 thin wrapping해
 * service에서 한 줄 호출로 의도를 명확히 표현.
 *
 * <p>책임:
 * <ul>
 *   <li>업로드 commit 시점 사용자 row pessimistic write lock ({@link UserRepository#lockActiveById})</li>
 *   <li>{@code storage_used + delta > storage_quota}이면 {@link QuotaExceededException} (HTTP 413)</li>
 *   <li>가드 통과 시 {@link User#consumeStorage} 호출 + 동일 트랜잭션 내 flush 위임 (호출자가 outer
 *       {@code @Transactional}을 갖고 있어 별도 save 불필요)</li>
 * </ul>
 *
 * <p>storage write 이전에 호출되어야 객체 orphan을 막을 수 있다 — `FileUploadService`는 본 enforcer를
 * conflict 검사 직후, storage write 직전에 1회 호출.
 *
 * <p>한도 < 사용량(over-quota — admin이 한도 축소) 상태에서는 신규 업로드만 차단되며 기존 파일은
 * 그대로 유지된다. 이는 admin Phase 3 결정(`AdminUserQuotaService` 운영 grace)과 동기.
 *
 * <p>delta=0은 no-op으로 처리(예: 빈 파일 업로드). lock은 여전히 획득해 동시성 의미를 통일.
 */
@Component
public class UserQuotaEnforcer {

    private static final Logger log = LoggerFactory.getLogger(UserQuotaEnforcer.class);

    private final UserRepository userRepository;

    public UserQuotaEnforcer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 한도 가드 + 사용량 증분 (atomic, outer @Transactional 안에서 호출).
     *
     * @param userId 업로더 사용자 id (인증된 활성 사용자)
     * @param delta  업로드 크기(bytes), 0 이상
     * @throws IllegalArgumentException     {@code userId == null} 또는 {@code delta < 0}
     * @throws QuotaExceededException       {@code storage_used + delta > storage_quota}
     * @throws IllegalStateException        userId 사용자가 soft-deleted 또는 미존재
     */
    public void consumeOrThrow(UUID userId, long delta) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (delta < 0) throw new IllegalArgumentException("delta must be >= 0, got: " + delta);

        User user = userRepository.lockActiveById(userId)
            .orElseThrow(() -> new IllegalStateException(
                "active user not found for quota enforcement: " + userId));

        long quota = user.getStorageQuota();
        long used = user.getStorageUsed();
        if (used + delta > quota) {
            throw new QuotaExceededException(userId, used, quota, delta);
        }

        if (delta > 0) {
            user.consumeStorage(delta);
            userRepository.save(user);
        }
    }

    /**
     * quota mutation Phase 6 — hard delete 시점 `storage_used` 감소 (atomic, outer @Transactional 안에서 호출).
     *
     * <p>consumeOrThrow와 비대칭:
     * <ul>
     *   <li>soft-deleted/미존재 user는 throw 대신 <b>no-op + warn 로그</b>.
     *       hard delete는 사용자 라이프사이클과 무관하게(예: 떠난 직원 파일 정리) 수행되어야 한다.</li>
     *   <li>{@code storage_used - delta < 0}는 {@link User#releaseStorage}에서 0으로 clamp.
     *       Phase 5 도입 이전에 업로드된 파일의 release 시 발생. clamp 발생 시 warn 로그.</li>
     * </ul>
     *
     * @param userId 파일 소유자 id (active 또는 soft-deleted 모두 허용)
     * @param delta  release 크기(bytes), 0 이상
     * @throws IllegalArgumentException {@code userId == null} 또는 {@code delta < 0}
     */
    public void release(UUID userId, long delta) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (delta < 0) throw new IllegalArgumentException("delta must be >= 0, got: " + delta);

        if (delta == 0) return;

        User user = userRepository.lockActiveById(userId).orElse(null);
        if (user == null) {
            log.warn("storage release skipped — user not active (soft-deleted or missing): userId={} delta={}",
                userId, delta);
            return;
        }

        boolean clamped = user.releaseStorage(delta);
        userRepository.save(user);

        if (clamped) {
            log.warn("storage release clamped to 0 — released more than tracked: userId={} requested_delta={}",
                userId, delta);
        }
    }
}
