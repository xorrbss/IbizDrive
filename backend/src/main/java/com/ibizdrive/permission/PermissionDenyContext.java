package com.ibizdrive.permission;

import com.ibizdrive.user.Role;

import java.util.Set;

/**
 * 권한 거부 시 {@code 403 PERMISSION_DENIED} 응답 본문에 노출할 {@code required}/{@code have}
 * 컨텍스트를 evaluator → handler 사이에 전달하는 ThreadLocal (docs/03 §3.6).
 *
 * <p>{@link IbizDrivePermissionEvaluator}가 deny 판정 시 {@link #record}로 저장 →
 * {@code GlobalExceptionHandler}가 {@code AccessDeniedException} 처리 중 {@link #consume}로 읽고
 * 동일 메서드가 ThreadLocal을 정리한다. consume 후 재호출 시 {@code null} 반환.
 *
 * <p>ThreadLocal 사용 이유: Spring Security {@link org.springframework.security.access.PermissionEvaluator}
 * 시그니처는 {@code boolean}만 반환할 수 있고, 실제 예외는 Spring Security 내부에서
 * {@code AuthorizationDeniedException}으로 throw된다 — 컨텍스트 전달 채널이 별도로 필요.
 *
 * <p>비동기 작업 경계(@Async)는 Spring Security context propagation과 별도로 본 ThreadLocal을
 * 전파하지 않는다. MVP는 sync 요청 흐름만 전제 — 비동기 도입 시 채널 재설계 필요.
 */
public final class PermissionDenyContext {

    private static final ThreadLocal<DenyInfo> CURRENT = new ThreadLocal<>();

    private PermissionDenyContext() {}

    /** evaluator가 deny 결정 시 호출. 같은 요청 내 마지막 deny가 우선. */
    public static void record(Permission required, Role actorRole, Set<Permission> have) {
        CURRENT.set(new DenyInfo(required, actorRole, have));
    }

    /** handler가 1회 읽고 정리. 미존재 시 {@code null}. */
    public static DenyInfo consume() {
        DenyInfo info = CURRENT.get();
        CURRENT.remove();
        return info;
    }

    /** 테스트/필터 종료 시 안전망. */
    public static void clear() {
        CURRENT.remove();
    }

    public record DenyInfo(Permission required, Role actorRole, Set<Permission> have) {}
}
