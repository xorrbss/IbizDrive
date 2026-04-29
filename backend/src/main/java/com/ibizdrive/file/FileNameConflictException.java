package com.ibizdrive.file;

/**
 * 동일 폴더 + 동일 normalized_name의 활성 파일이 이미 존재할 때 — 409 매핑.
 *
 * <p>{@code FolderNameConflictException}과 평행 구조. {@code GlobalExceptionHandler}가
 * 별도로 받아 동일 envelope code {@code RENAME_CONFLICT}로 매핑 — frontend는 폴더/파일 구분 없이
 * 동일 RenameDialog로 재요청 처리 (계약).
 *
 * <p>두 가지 발생 경로 (이중 가드 — CLAUDE.md §3 원칙 6):
 * <ol>
 *   <li>service의 사전 conflict 검사 ({@code existsActiveByFolderAndNormalizedName(...)}) 적중</li>
 *   <li>사전 검사 통과 후 UPDATE 시점 race로 V5 {@code idx_files_unique_name} 위반 →
 *       {@code DataIntegrityViolationException} catch 후 변환</li>
 * </ol>
 */
public class FileNameConflictException extends RuntimeException {

    public FileNameConflictException(String message) {
        super(message);
    }

    public FileNameConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
