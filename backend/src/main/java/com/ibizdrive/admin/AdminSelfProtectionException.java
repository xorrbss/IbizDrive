package com.ibizdrive.admin;

/**
 * Admin이 자기 자신에 대해 차단된 작업을 시도 — admin-user-mgmt.
 *
 * <p>차단 대상:
 * <ul>
 *   <li>본인 ROLE을 ADMIN 외로 강등 — 마지막 ADMIN 0명 사태 방지.</li>
 *   <li>본인 비활성화 — 즉시 로그아웃 + 재로그인 불가능 → 운영 잠금 회피.</li>
 * </ul>
 *
 * <p>HTTP 403 + body {@code { code: "FORBIDDEN", reason: "SELF_PROTECTION" }}로 매핑된다.
 * 본인이 마지막 ADMIN인지 여부와 무관하게 일관 차단 — 검증 단순화 + admin도 본인은
 * 다른 ADMIN을 통해 강등/비활성화하도록 강제.
 */
public class AdminSelfProtectionException extends RuntimeException {
    public AdminSelfProtectionException(String reason) {
        super(reason);
    }
}
