package com.ibizdrive.folder;

/**
 * 동일 부모 + 동일 normalized_name의 활성 폴더가 이미 존재할 때 — 409 매핑 (A4.7 endpoint 도입 시).
 *
 * <p>V5 {@code idx_folders_unique_name} 위반을 service 단에서 변환. {@link PermissionConflictException}
 * 패턴과 동일 구조 — endpoint 도입(A4.7) 시 {@code GlobalExceptionHandler}에 409 envelope 매핑이 추가될 예정.
 * 본 세션은 service contract 단계 — endpoint 매핑은 out of scope.
 *
 * <p>두 가지 발생 경로 (이중 가드 — CLAUDE.md §3 원칙 6):
 * <ol>
 *   <li>service의 사전 conflict 검사 ({@code existsActiveByParentAndNormalizedName}) 적중</li>
 *   <li>사전 검사 통과 후 INSERT 시점 race로 V5 unique index 위반 →
 *       {@code DataIntegrityViolationException} catch 후 변환</li>
 * </ol>
 */
public class FolderNameConflictException extends RuntimeException {

    public FolderNameConflictException(String message) {
        super(message);
    }

    public FolderNameConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
