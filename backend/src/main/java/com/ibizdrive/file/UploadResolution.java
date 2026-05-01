package com.ibizdrive.file;

/**
 * 업로드 시점 동일 폴더 + 동일 normalized_name 충돌 해결 정책 (A15, docs/02 §6.1).
 *
 * <p>Frontend M5 인터페이스(`ConflictDialog`)와 1:1로 매핑되는 enum:
 * <ul>
 *   <li>{@link #NEW_VERSION} — 충돌 대상 파일의 새 version으로 추가. {@code files} row는 보존,
 *       {@code file_versions} row append + {@code current_version_id} 갱신. audit
 *       {@link com.ibizdrive.audit.AuditEventType#VERSION_CREATED}.</li>
 *   <li>{@link #RENAME} — 새 파일을 생성하되 표시 이름에 {@code (1)} 접미사를 부여 (자동 충돌 회피).
 *       audit {@link com.ibizdrive.audit.AuditEventType#FILE_UPLOADED}.</li>
 * </ul>
 *
 * <p>resolution 값이 {@code null}이고 충돌이 발생하면 service는
 * {@link FileNameConflictException}를 던져 frontend가 dialog로 재요청하도록 한다 (계약).
 */
public enum UploadResolution {
    NEW_VERSION,
    RENAME
}
