package com.ibizdrive.common.error;

import com.ibizdrive.permission.Permission;
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

    private static String[] toWireArray(Set<Permission> have) {
        return have.stream().map(Permission::wire).sorted().toArray(String[]::new);
    }
}
