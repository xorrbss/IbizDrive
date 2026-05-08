package com.ibizdrive.audit;

import java.net.InetAddress;
import java.util.UUID;

/**
 * 감사 로그 export 1회를 표현하는 도메인 이벤트.
 *
 * <p>{@link AuditQueryController#export}가 응답 stream을 모두 작성한 뒤 publish하면
 * {@link AuditExportListener}가 {@code AUDIT_EXPORTED} ({@code audit.exported}) audit_log row를
 * REQUIRES_NEW 트랜잭션으로 기록한다 (ADR #24).
 *
 * <p><b>actor IP/User-Agent를 이벤트가 직접 들고 가는 이유</b>: {@link WebRequestContextHolder}는
 * ThreadLocal 기반인데, 응답 본문이 {@code StreamingResponseBody}로 비동기 처리될 가능성이 있어
 * publish 시점에 다른 스레드일 수 있다. controller에서 요청 스레드 값을 캡처해 이벤트에 동봉하면
 * 실행 스레드와 무관하게 IP/UA가 보존된다.
 *
 * @param actorId      호출자 user id (인증된 ADMIN/AUDITOR)
 * @param actorIp      호출자 IP — controller 캡처
 * @param userAgent    호출자 UA — controller 캡처
 * @param filtersJson  적용된 필터의 JSON 문자열 (audit metadata에 그대로 들어감)
 * @param rowCount     실제로 export된 행 수 (cap 적용 후)
 * @param truncated    cap 초과로 잘렸는지 여부
 * @param format       응답 직렬화 형식. controller가 wire 문자열을 {@link AuditExportFormat#from}으로
 *                     검증·변환한 값 — listener는 컴파일러가 보증하는 enum만 받는다.
 */
public record AuditExportEvent(
    UUID actorId,
    InetAddress actorIp,
    String userAgent,
    String filtersJson,
    int rowCount,
    boolean truncated,
    AuditExportFormat format
) {
}
