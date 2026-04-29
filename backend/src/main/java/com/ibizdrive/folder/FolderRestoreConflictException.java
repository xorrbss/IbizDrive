package com.ibizdrive.folder;

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
 */
public class FolderRestoreConflictException extends RuntimeException {

    public FolderRestoreConflictException(String message) {
        super(message);
    }

    public FolderRestoreConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
