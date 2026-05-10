package com.ibizdrive.common.error;

import com.ibizdrive.department.DepartmentConflictException;
import com.ibizdrive.file.FileNameConflictException;
import com.ibizdrive.file.FileRestoreConflictException;
import com.ibizdrive.folder.CrossScopeMoveException;
import com.ibizdrive.folder.DestWorkspaceDeniedException;
import com.ibizdrive.folder.InvalidMoveDestinationException;
import com.ibizdrive.folder.FolderNameConflictException;
import com.ibizdrive.folder.FolderRestoreConflictException;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionConflictException;
import com.ibizdrive.permission.PermissionDenyContext;
import com.ibizdrive.team.LastOwnerRequiredException;
import com.ibizdrive.team.TeamArchivedException;
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
     * V5의 {@code idx_files_unique_name} 위반 → 동일 폴더 내 동일 normalized_name 활성 파일 — A4.8.
     *
     * <p>{@link FolderNameConflictException}과 동일 envelope code {@code RENAME_CONFLICT} —
     * frontend는 폴더/파일 구분 없이 동일 RenameDialog로 재요청 (계약, docs/02 §8).
     */
    @ExceptionHandler(FileNameConflictException.class)
    public ResponseEntity<ApiError> handleFileNameConflict(FileNameConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("RENAME_CONFLICT", "동일 이름의 파일이 이미 존재합니다", null));
    }

    /**
     * 폴더 복원 시 원위치(original_parent_id) 아래 동일 normalized_name 활성 폴더 충돌 — A6.3.
     *
     * <p>docs/02 §8 line 1221에 등록된 {@code RESTORE_CONFLICT} envelope code. {@code RENAME_CONFLICT}와
     * 분리한 이유: frontend가 "이름 변경 후 복원 제안" 흐름으로 분기 (rename은 RenameDialog 재요청).
     */
    @ExceptionHandler(FolderRestoreConflictException.class)
    public ResponseEntity<ApiError> handleFolderRestoreConflict(FolderRestoreConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("RESTORE_CONFLICT", "동일 위치에 같은 이름의 폴더가 존재해 복원할 수 없습니다", null));
    }

    /**
     * 파일 복원 시 원위치(original_folder_id) 아래 동일 normalized_name 활성 파일 충돌 — v1.x M9 후속.
     *
     * <p>{@link FolderRestoreConflictException} 와 동일 envelope code {@code RESTORE_CONFLICT} —
     * frontend 는 file/folder 구분 없이 RestoreConflictDialog 로 분기 (계약, docs/02 §8).
     * {@code FileNameConflictException} (envelope {@code RENAME_CONFLICT}) 와 분리한 이유:
     * RESTORE_CONFLICT 는 "원본 이름 그대로 복원"의 충돌, RENAME_CONFLICT 는
     * rename/move/restore-with-name 의 충돌 (frontend UX 가 다름).
     */
    @ExceptionHandler(FileRestoreConflictException.class)
    public ResponseEntity<ApiError> handleFileRestoreConflict(FileRestoreConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("RESTORE_CONFLICT", "동일 위치에 같은 이름의 파일이 존재해 복원할 수 없습니다", null));
    }

    /**
     * V9 partial unique({@code idx_departments_name_active}) 충돌 — admin-department-crud (Wave 2 T4).
     *
     * <p>{@link DepartmentConflictException}는 service의 사전 조회 또는 INSERT race로 발생.
     * envelope code {@code DEPARTMENT_CONFLICT} (docs/02 §8 계약).
     */
    @ExceptionHandler(DepartmentConflictException.class)
    public ResponseEntity<ApiError> handleDepartmentConflict(DepartmentConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("DEPARTMENT_CONFLICT", "동일 이름의 활성 부서가 이미 존재합니다", null));
    }

    /**
     * spec §2.2 last-OWNER 가드 — 팀에서 마지막 OWNER를 제거하거나 강등하려 할 때 발생.
     *
     * <p>HTTP 400 + envelope code {@code TEAM_OWNER_REQUIRED} (spec §5.4).
     * spec 문서에서는 {@code ERR_TEAM_OWNER_REQUIRED} 로 표기하나, wire format은 접두사 없이
     * {@code TEAM_OWNER_REQUIRED} — peer convention: {@code DEPARTMENT_CONFLICT}, {@code RENAME_CONFLICT}.
     */
    @ExceptionHandler(LastOwnerRequiredException.class)
    public ResponseEntity<ApiError> handleLastOwnerRequired(LastOwnerRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("TEAM_OWNER_REQUIRED", "팀에는 최소 한 명의 OWNER가 필요합니다", null));
    }

    /**
     * spec §2.2 archive 라이프사이클 — archived 팀 소속 콘텐츠에 write 시도 시 발생.
     *
     * <p>HTTP 423 (Locked) + envelope code {@code TEAM_ARCHIVED} (spec §5.4).
     * spec 표기 {@code ERR_TEAM_ARCHIVED} ↔ wire {@code TEAM_ARCHIVED} (peer convention).
     *
     * <p>details에 {@code teamId}를 포함해 frontend가 어떤 팀이 archived인지 식별 가능하게 한다.
     */
    @ExceptionHandler(TeamArchivedException.class)
    public ResponseEntity<ApiError> handleTeamArchived(TeamArchivedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
            .body(ApiError.of("TEAM_ARCHIVED", "archive된 팀의 콘텐츠는 수정할 수 없습니다",
                Map.of("teamId", ex.getTeamId().toString())));
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

    /**
     * Plan D — cross-workspace move 가드 위반 ({@link CrossScopeMoveException}).
     *
     * <p>spec §5.6: same-scope만 허용하는 default 경로에서 cross 시도는 409 + {@code ERR_CROSS_SCOPE_MOVE}.
     * 명시적 cross-workspace move ({@code allowCrossScope: true}) 분기는 본 envelope를 발생시키지 않는다 —
     * service가 아예 다른 진입점({@code CrossWorkspaceMoveService} — Plan D Task 11에서 도입).
     */
    @ExceptionHandler(CrossScopeMoveException.class)
    public ResponseEntity<ApiError> handleCrossScopeMove(CrossScopeMoveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("ERR_CROSS_SCOPE_MOVE",
                "다른 workspace로 이동하려면 컨텍스트 메뉴 '다른 workspace로 이동'을 사용하세요", null));
    }

    /**
     * Plan D — cross-workspace move 권한 부족. source {@code EDIT+SHARE} 또는 destination {@code UPLOAD} 부재.
     */
    @ExceptionHandler(DestWorkspaceDeniedException.class)
    public ResponseEntity<ApiError> handleDestWorkspaceDenied(DestWorkspaceDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("ERR_DEST_WORKSPACE_DENIED", "다른 workspace로 이동할 권한이 없습니다", null));
    }

    /**
     * Plan D — cross-workspace move destination 부적절 (null = root 직접, 자기 자신/후손 등).
     * 메시지는 caller가 구체화 (예: "destinationFolderId is required", "destination cannot be a descendant").
     */
    @ExceptionHandler(InvalidMoveDestinationException.class)
    public ResponseEntity<ApiError> handleInvalidMoveDestination(InvalidMoveDestinationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("ERR_INVALID_DESTINATION", ex.getMessage(), null));
    }

    private static String[] toWireArray(Set<Permission> have) {
        return have.stream().map(Permission::wire).sorted().toArray(String[]::new);
    }
}
