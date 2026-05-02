package com.ibizdrive.auth;

/**
 * 회원가입 시 동일 email이 이미 활성 사용자로 존재 — ADR #41, docs/02 §7.4.
 *
 * <p>HTTP 409 + body {@code { code: "CONFLICT", reason: "DUPLICATE_EMAIL" }}로 변환.
 *
 * <p>DB 제약(UNIQUE on lower(email) WHERE deleted_at IS NULL, V1 partial index)이 진실의 출처.
 * 서비스 사전 조회는 빠른 경로이고 race condition 시 INSERT가 unique 위반으로 실패하면
 * 동일 예외로 매핑되어야 한다 (현재는 service 사전 조회만 — race window 매우 좁아 MVP 허용).
 */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("Duplicate email");
    }
}
