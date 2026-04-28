package com.ibizdrive.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 일반 API 에러 envelope (docs/02 §7.2).
 *
 * <pre>{@code
 * { "error": { "code": "PERMISSION_DENIED", "message": "...", "details": {...} } }
 * }</pre>
 *
 * <p>인증 endpoint(docs/02 §7.4)는 별도 flat shape을 사용 ({@link ErrorResponse}). 본 envelope는
 * 권한/검증/충돌 등 일반 endpoint 응답에 사용.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, Object details) {}

    public static ApiError of(String code, String message, Object details) {
        return new ApiError(new Body(code, message, details));
    }
}
