package com.ibizdrive.folder;

import java.util.Map;
import java.util.UUID;

/**
 * 휴지통 복원 시점에 원위치(original parent)에 같은 normalized_name의 활성 폴더가 이미 존재할 때 — A6.2.
 *
 * <p>{@link FolderNameConflictException}과 분리한 이유: docs/02 §8 line 1221에서 envelope code가
 * 다르다 ({@code RESTORE_CONFLICT} vs {@code RENAME_CONFLICT}). frontend는 이 두 코드를 다른 UX
 * (이름 변경 후 복원 제안 vs RenameDialog 재표시)로 분기하므로 service 단에서도 별도 예외로 구분.
 *
 * <p>두 가지 발생 경로 (이중 가드 — CLAUDE.md §3 원칙 6):
 * <ol>
 *   <li>service의 사전 conflict 검사 ({@code existsActiveByParentAndNormalizedName}) 적중</li>
 *   <li>사전 검사 통과 후 UPDATE(deleted_at NULL 클리어) 시점 race로 V5 partial unique index 위반 →
 *       {@code DataIntegrityViolationException} catch 후 변환</li>
 * </ol>
 *
 * <p>{@link Reason} — Plan E T4에서 archive guard / cross-scope mismatch 케이스 추가.
 */
public class FolderRestoreConflictException extends RuntimeException {

    /** 복원 충돌 사유 — {@code GlobalExceptionHandler}에서 wire body의 {@code reason} 필드로 노출. */
    public enum Reason {
        /** 원위치에 동일 normalized_name 활성 폴더 존재 (기존 동작). */
        NAME_CONFLICT,
        /** 복원 대상 폴더의 원본 workspace(dept/team)가 요청 workspace와 다를 때 (Plan E T4). */
        SCOPE_MISMATCH
    }

    private final Reason reason;
    private final UUID resourceId;
    private final Map<String, Object> details;

    /**
     * Backward-compatible 1-arg 생성자 — {@link Reason#NAME_CONFLICT} 기본값.
     *
     * <p>기존 호출자({@link FolderMutationService} 등)는 변경 없이 그대로 동작.
     */
    public FolderRestoreConflictException(String message) {
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
    public FolderRestoreConflictException(String message, Throwable cause) {
        super(message, cause);
        this.reason = Reason.NAME_CONFLICT;
        this.resourceId = null;
        this.details = Map.of();
    }

    /**
     * 전체 reason + resourceId + 메시지 생성자 (Plan E T4/T5에서 SCOPE_MISMATCH 등 신규 reason 사용).
     */
    public FolderRestoreConflictException(Reason reason, UUID resourceId, String message) {
        super(message);
        this.reason = reason;
        this.resourceId = resourceId;
        this.details = Map.of();
    }

    /**
     * 전체 reason + resourceId + 메시지 + 추가 details 생성자.
     */
    public FolderRestoreConflictException(Reason reason, UUID resourceId, String message,
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
