package com.ibizdrive.file;

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
 */
public class FileRestoreConflictException extends RuntimeException {

    public FileRestoreConflictException(String message) {
        super(message);
    }

    public FileRestoreConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
