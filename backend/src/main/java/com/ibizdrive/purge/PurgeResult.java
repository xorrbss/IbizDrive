package com.ibizdrive.purge;

import java.util.List;
import java.util.UUID;

/**
 * A7 hard purge 1회 run 결과 — {@link HardPurgeService#runDailyPurge}의 반환 + audit
 * {@code SYSTEM_PURGE_EXECUTED.after_state} 직렬화 입력.
 *
 * <p>{@code orphanStorageKeys}는 cap=1000으로 제한 (audit_log JSONB row 비대화 회피).
 * 초과 시 {@code orphanStorageKeysTruncated=true}로 마킹. 실 S3 객체 삭제는 ADR #31에 따라
 * storage 모듈 milestone에서 처리.
 *
 * <p>{@code truncated=true}는 {@code MAX_PURGE_PER_RUN} 한도 초과로 일부 만료 row가 다음 run으로
 * 이월된 경우. 잡 자체는 정상 완료.
 */
public record PurgeResult(
    UUID runId,
    int purgedFiles,
    int purgedFolders,
    List<UUID> orphanStorageKeys,
    boolean orphanStorageKeysTruncated,
    long durationMs,
    boolean truncated
) {
}
