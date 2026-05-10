package com.ibizdrive.common.error;

import com.ibizdrive.file.FileRestoreConflictException;
import com.ibizdrive.folder.FolderRestoreConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GlobalExceptionHandler} — FolderRestoreConflictException / FileRestoreConflictException
 * 핸들러 검증 (Spring 컨텍스트 없이 핸들러 메서드 직접 호출).
 *
 * <p>검증 대상:
 * <ul>
 *   <li>1-arg 생성자(backward-compat) → body.reason = "name_conflict"</li>
 *   <li>SCOPE_MISMATCH reason → body.reason = "scope_mismatch" + resourceId 포함</li>
 *   <li>details map → body.details에 병합</li>
 * </ul>
 */
class GlobalExceptionHandlerRestoreConflictTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ───────────── FolderRestoreConflictException ─────────────

    @Test
    void folderRestore_legacyConstructor_returns409WithNameConflictReason() {
        FolderRestoreConflictException ex = new FolderRestoreConflictException("name conflict");

        ResponseEntity<ApiError> response = handler.handleFolderRestoreConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiError.Body body = response.getBody().error();
        assertThat(body.code()).isEqualTo("RESTORE_CONFLICT");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.details();
        assertThat(details).containsEntry("reason", "name_conflict");
        assertThat(details).doesNotContainKey("resourceId");
    }

    @Test
    void folderRestore_scopeMismatch_returns409WithScopeMismatchReasonAndResourceId() {
        UUID folderId = UUID.randomUUID();
        FolderRestoreConflictException ex = new FolderRestoreConflictException(
                FolderRestoreConflictException.Reason.SCOPE_MISMATCH,
                folderId,
                "scope mismatch");

        ResponseEntity<ApiError> response = handler.handleFolderRestoreConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiError.Body body = response.getBody().error();
        assertThat(body.code()).isEqualTo("RESTORE_CONFLICT");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.details();
        assertThat(details).containsEntry("reason", "scope_mismatch");
        assertThat(details).containsEntry("resourceId", folderId);
    }

    @Test
    void folderRestore_scopeMismatch_withExtraDetails_mergesIntoBody() {
        UUID folderId = UUID.randomUUID();
        FolderRestoreConflictException ex = new FolderRestoreConflictException(
                FolderRestoreConflictException.Reason.SCOPE_MISMATCH,
                folderId,
                "scope mismatch",
                Map.of("originalScope", "dept", "targetScope", "team"));

        ResponseEntity<ApiError> response = handler.handleFolderRestoreConflict(ex);

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) response.getBody().error().details();
        assertThat(details).containsEntry("reason", "scope_mismatch");
        assertThat(details).containsEntry("resourceId", folderId);
        assertThat(details).containsEntry("originalScope", "dept");
        assertThat(details).containsEntry("targetScope", "team");
    }

    // ───────────── FileRestoreConflictException ─────────────

    @Test
    void fileRestore_legacyConstructor_returns409WithNameConflictReason() {
        FileRestoreConflictException ex = new FileRestoreConflictException("name conflict");

        ResponseEntity<ApiError> response = handler.handleFileRestoreConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiError.Body body = response.getBody().error();
        assertThat(body.code()).isEqualTo("RESTORE_CONFLICT");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.details();
        assertThat(details).containsEntry("reason", "name_conflict");
        assertThat(details).doesNotContainKey("resourceId");
    }

    @Test
    void fileRestore_scopeMismatch_returns409WithScopeMismatchReasonAndResourceId() {
        UUID fileId = UUID.randomUUID();
        FileRestoreConflictException ex = new FileRestoreConflictException(
                FileRestoreConflictException.Reason.SCOPE_MISMATCH,
                fileId,
                "scope mismatch");

        ResponseEntity<ApiError> response = handler.handleFileRestoreConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiError.Body body = response.getBody().error();
        assertThat(body.code()).isEqualTo("RESTORE_CONFLICT");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.details();
        assertThat(details).containsEntry("reason", "scope_mismatch");
        assertThat(details).containsEntry("resourceId", fileId);
    }

    @Test
    void fileRestore_scopeMismatch_withExtraDetails_mergesIntoBody() {
        UUID fileId = UUID.randomUUID();
        FileRestoreConflictException ex = new FileRestoreConflictException(
                FileRestoreConflictException.Reason.SCOPE_MISMATCH,
                fileId,
                "scope mismatch",
                Map.of("originalScope", "team", "targetScope", "dept"));

        ResponseEntity<ApiError> response = handler.handleFileRestoreConflict(ex);

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) response.getBody().error().details();
        assertThat(details).containsEntry("reason", "scope_mismatch");
        assertThat(details).containsEntry("resourceId", fileId);
        assertThat(details).containsEntry("originalScope", "team");
        assertThat(details).containsEntry("targetScope", "dept");
    }
}
