package com.ibizdrive.common.error;

import com.ibizdrive.admin.AdminBadPatchException;
import com.ibizdrive.admin.AdminSelfProtectionException;
import com.ibizdrive.admin.AdminUserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Admin 도메인 예외 → HTTP 응답 매핑 — admin-user-mgmt.
 *
 * <p>{@link AuthExceptionHandler}와 분리 — admin scope의 예외는 도메인 분리상 별도 advice에
 * 둔다. 두 advice 모두 {@link RestControllerAdvice}이므로 Spring이 자동으로 모든 controller에
 * 적용한다.
 */
@RestControllerAdvice
public class AdminExceptionHandler {

    @ExceptionHandler(AdminUserNotFoundException.class)
    public ResponseEntity<ErrorResponse> userNotFound(AdminUserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", "USER_NOT_FOUND", null, null));
    }

    @ExceptionHandler(AdminSelfProtectionException.class)
    public ResponseEntity<ErrorResponse> selfProtection(AdminSelfProtectionException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("FORBIDDEN", "SELF_PROTECTION", null, null));
    }

    /**
     * PATCH body cross-field 위반 (빈 body / 미지원 isActive=true) → 400 VALIDATION_ERROR.
     *
     * <p>field={@code "body"}, rule=ex.message — 기존 Bean Validation 응답 스키마(details.field/rule)
     * 와 호환되는 형태로 노출. 단일 필드 위반이 아닌 cross-field/semantic 위반이지만 별도 envelope을
     * 만들지 않고 동일 응답 모양으로 통일 (frontend 단일 처리).
     */
    @ExceptionHandler(AdminBadPatchException.class)
    public ResponseEntity<ErrorResponse> badPatch(AdminBadPatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.validationError(Map.of(
                "field", "body",
                "rule", ex.getMessage()
            )));
    }
}
