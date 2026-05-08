package com.ibizdrive.admin;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Cron 정책 토글 도메인 이벤트 (admin-cron-policy-toggle).
 *
 * <p>{@link AdminSystemService#toggleCron}이 트랜잭션 내에서 publish하면
 * {@link AdminCronToggledListener}가 {@code AFTER_COMMIT}에 audit_log row를 기록한다.
 *
 * <p>actor IP/User-Agent를 이벤트에 직접 담는 이유는 {@link AdminDepartmentService} 패턴과
 * 동일 — listener가 다른 스레드에서 동작할 가능성에 대비.
 *
 * @param actorId      토글한 ADMIN user id
 * @param actorIp      호출자 IP (controller 캡처)
 * @param userAgent    호출자 UA (controller 캡처)
 * @param jobKey       cron 식별자 ({@code purge.expired} 등)
 * @param fromEnabled  토글 직전 enabled 값
 * @param toEnabled    토글 직후 enabled 값
 */
public record AdminCronToggledEvent(
    UUID actorId,
    InetAddress actorIp,
    String userAgent,
    String jobKey,
    boolean fromEnabled,
    boolean toEnabled
) {
}
