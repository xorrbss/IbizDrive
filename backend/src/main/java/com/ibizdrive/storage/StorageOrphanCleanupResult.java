package com.ibizdrive.storage;

import java.util.UUID;

/**
 * Storage orphan cleanup 1회 run 결과 — {@link StorageOrphanCleanupService#runDailyCleanup}의 반환 +
 * audit {@code STORAGE_ORPHAN_CLEANED.after_state} 직렬화 입력.
 *
 * <p>{@link com.ibizdrive.purge.PurgeResult}와 동일한 summary-only 패턴 (per-row audit 미발행).
 *
 * @param runId      run 식별자 (UUID v4)
 * @param scanned    walk가 yield한 객체 총 수 (UUID 형식 매치 + grace 통과 후)
 * @param candidates liveSet 미일치 객체 수 (orphan candidate)
 * @param deleted    실제 삭제 성공 객체 수
 * @param failed     삭제 시도 후 IOException으로 실패한 객체 수 (per-row isolation)
 * @param truncated  {@code maxPerRun} 한도 도달로 일부 candidate가 다음 run으로 이월된 경우 {@code true}
 * @param durationMs run 총 소요 시간 (ms)
 */
public record StorageOrphanCleanupResult(
    UUID runId,
    int scanned,
    int candidates,
    int deleted,
    int failed,
    boolean truncated,
    long durationMs
) {
}
