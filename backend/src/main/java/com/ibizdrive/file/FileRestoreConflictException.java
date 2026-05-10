package com.ibizdrive.file;

import java.util.Map;
import java.util.UUID;

/**
 * 휴지통 복원 시점에 원위치(original folder)에 같은 normalized_name 의 활성 파일이 이미 존재하고,
 * 사용자가 새 이름을 지정하지 않은 경우 — v1.x RestoreConflictDialog 트랙 (M9 후속).
 *
 * <p>{@link FileNameConflictException} 과 분리한 이유: docs/02 §8 envelope code 가 다르다
 * ({@code RESTORE_CONFLICT} vs {@code RENAME_CONFLICT}). frontend 는 RESTORE_CONFLICT 를
 * "다른 이름으로 복원 제안" UX (RestoreConflictDialog) 로 분기하고, RENAME_CONFLICT 는 다이얼로그의
 * inline alert 로 분기.
 *
 * <p>발생 경로 (이중 가드 — CLAUDE.md §3 원칙 6):
 * <ol>
 *   <li>service 의 사전 conflict 검사 ({@code existsActiveByFolderAndNormalizedNameExcludingId}) 적중</li>
 *   <li>UPDATE(deleted_at NULL 클리어) 시점 race 로 V5 partial unique index 위반 →
 *       {@code DataIntegrityViolationException} catch 후 변환</li>
 * </ol>
 *
 * <p>{@link com.ibizdrive.folder.FolderRestoreConflictException} 와 동일 패턴 — 두 envelope 모두
 * 통일된 {@code RESTORE_CONFLICT} 로 매핑된다 (file/folder 구분 없음).
 *
 * <p>{@link Reason} — Plan E T5에서 archive guard / cross-scope mismatch 케이스 추가.
 */
public class FileRestoreConflictException extends RuntimeException {

    /** 복원 충돌 사유 — {@code GlobalExceptionHandler}에서 wire body의 {@code reason} 필드로 노출. */
    public enum Reason {
        /** 원위치에 동일 normalized_name 활성 파일 존재 (기존 동작). */
        NAME_CONFLICT,
        /** 복원 대상 파일의 원본 workspace(dept/team)가 요청 workspace와 다를 때 (Plan E T5). */
        SCOPE_MISMATCH
    }

    private final Reason reason;
    private final UUID resourceId;
    private final Map<String, Object> details;

    /**
     * Backward-compatible 1-arg 생성자 — {@link Reason#NAME_CONFLICT} 기본값.
     *
     * <p>기존 호출자({@link FileMutationService} 등)는 변경 없이 그대로 동작.
     */
    public FileRestoreConflictException(String message) {
        super(message);
        this.reason = Reason.NAME_CONFLICT;
        this.resourceId = null;
        this.details = Map.of();
    }

    /**
     * Backward-compatible 2-arg 생성자 — {@link Reason#NAME_CONFLICT} 기본값.
     *
     * <p>DataIntegrityViolationException catch 후 변환 경로에서 사용.
     */
    public FileRestoreConflictException(String message, Throwable cause) {
        super(message, cause);
        this.reason = Reason.NAME_CONFLICT;
        this.resourceId = null;
        this.details = Map.of();
    }

    /**
     * 전체 reason + resourceId + 메시지 생성자 (Plan E T4/T5에서 SCOPE_MISMATCH 등 신규 reason 사용).
     */
    public FileRestoreConflictException(Reason reason, UUID resourceId, String message) {
        super(message);
        this.reason = reason;
        this.resourceId = resourceId;
        this.details = Map.of();
    }

    /**
     * 전체 reason + resourceId + 메시지 + 추가 details 생성자.
     */
    public FileRestoreConflictException(Reason reason, UUID resourceId, String message,
                                        Map<String, Object> details) {
        super(message);
        this.reason = reason;
        this.resourceId = resourceId;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public Reason getReason() { return reason; }

    public UUID getResourceId() { return resourceId; }

    public Map<String, Object> getDetails() { return details; }
}
