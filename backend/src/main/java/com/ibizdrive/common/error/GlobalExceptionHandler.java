package com.ibizdrive.common.error;

import com.ibizdrive.folder.FolderNameConflictException;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionConflictException;
import com.ibizdrive.permission.PermissionDenyContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 일반 endpoint 예외 → docs/02 §7.2 envelope 매핑.
 *
 * <p>{@link com.ibizdrive.permission.IbizDrivePermissionEvaluator}가 deny 판정 후 Spring Security가
 * {@link AccessDeniedException} (또는 6.x의 {@code AuthorizationDeniedException})을 throw하면 본
 * advice가 받아 docs/03 §3.6 형식의 {@code 403 PERMISSION_DENIED} 본문을 구성한다.
 *
 * <p>{@code required}/{@code have}는 {@link PermissionDenyContext}에서 1회 consume.
 * 컨텍스트 부재 시(=evaluator 외 경로의 deny) {@code details: null}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        PermissionDenyContext.DenyInfo info = PermissionDenyContext.consume();
        Map<String, Object> details = null;
        if (info != null) {
            details = new LinkedHashMap<>();
            details.put("required", new String[]{info.required().wire()});
            details.put("have", toWireArray(info.have()));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("PERMISSION_DENIED", "권한이 없습니다", details));
    }

    /**
     * V5 의 {@code idx_permissions_unique} 위반 → 동일 (resource, subject) 중복 grant — A4.4.
     */
    @ExceptionHandler(PermissionConflictException.class)
    public ResponseEntity<ApiError> handlePermissionConflict(PermissionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("PERMISSION_CONFLICT", "이미 존재하는 권한입니다", null));
    }

    /**
     * V5의 {@code idx_folders_unique_name} 위반 → 동일 부모 내 동일 normalized_name 활성 폴더 — A4.7.
     *
     * <p>{@code FolderMutationService}의 사전 conflict 검사 또는 INSERT 시점 race로 잡힌
     * {@code DataIntegrityViolationException}을 service가 변환한 결과. envelope code는 docs/02 §8 계약
     * {@code RENAME_CONFLICT} (rename뿐 아니라 create/move의 충돌도 동일 분류 — frontend는 동일 RenameDialog로 재요청).
     */
    @ExceptionHandler(FolderNameConflictException.class)
    public ResponseEntity<ApiError> handleFolderNameConflict(FolderNameConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("RENAME_CONFLICT", "동일 이름의 폴더가 이미 존재합니다", null));
    }

    /**
     * 폴더/파일/grant row 등 리소스 미존재 → 404. controller 또는 service 단의 lookup 부재가 일관되게 envelope 매핑.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("NOT_FOUND", "리소스를 찾을 수 없습니다", null));
    }

    /**
     * service 입력 검증 실패 (subject_id ↔ everyone, expiresAt past, preset 미지정 등) → 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("BAD_REQUEST", ex.getMessage(), null));
    }

    private static String[] toWireArray(Set<Permission> have) {
        return have.stream().map(Permission::wire).sorted().toArray(String[]::new);
    }
}
