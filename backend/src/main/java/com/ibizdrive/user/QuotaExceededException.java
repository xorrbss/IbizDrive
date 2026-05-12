package com.ibizdrive.user;

import java.util.UUID;

/**
 * 사용자 quota 초과 시 업로드 차단 — quota mutation Phase 5 (`docs/04 §6.1`).
 *
 * <p>`GlobalExceptionHandler`가 받아 HTTP **413 PAYLOAD_TOO_LARGE** + envelope code
 * {@code QUOTA_EXCEEDED} (docs/02 §8)로 변환. 동일 envelope을 frontend `src/lib/errors.ts`에
 * mirror.
 *
 * <p>이 예외는 신규 업로드/버전 추가 진입점에서 service가 발생시키며, storage write 이전에 던져
 * 객체 orphan을 방지한다. 한도 < 현재 사용량(admin이 한도를 축소해 over-quota가 된 케이스)도
 * 신규 업로드만 차단되며 기존 파일은 그대로 유지된다.
 *
 * @param userId         업로더 사용자 id
 * @param currentUsed    현재 storage_used (bytes)
 * @param quota          허용 한도 storage_quota (bytes)
 * @param requestedDelta 요청 업로드 크기 (bytes)
 */
public class QuotaExceededException extends RuntimeException {

    private final UUID userId;
    private final long currentUsed;
    private final long quota;
    private final long requestedDelta;

    public QuotaExceededException(UUID userId, long currentUsed, long quota, long requestedDelta) {
        super(String.format(
            "quota exceeded for user=%s: used=%d quota=%d requested=%d",
            userId, currentUsed, quota, requestedDelta));
        this.userId = userId;
        this.currentUsed = currentUsed;
        this.quota = quota;
        this.requestedDelta = requestedDelta;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getCurrentUsed() {
        return currentUsed;
    }

    public long getQuota() {
        return quota;
    }

    public long getRequestedDelta() {
        return requestedDelta;
    }
}
