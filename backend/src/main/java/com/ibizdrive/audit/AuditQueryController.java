package com.ibizdrive.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.dto.AuditLogPageDto;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 감사 로그 조회 + 내보내기 endpoint — docs/02 §7.12.
 *
 * <p>두 진입점:
 * <ul>
 *   <li>{@code GET /api/admin/audit} — JSON 페이지네이션 조회 ({@link #list}). 가드:
 *       {@code SecurityConfig.anyRequest().authenticated()} + service 단계 ROLE 분기 (ADMIN/AUDITOR
 *       전체, MEMBER {@code actor_id=self}, RP-2 file 활동 타임라인 우회).</li>
 *   <li>{@code GET /api/admin/audit/export} — CSV 또는 JSON 다운로드 ({@link #export}). 가드:
 *       {@code @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")} — MEMBER 차단 (403). 응답
 *       완료 후 {@link AuditExportEvent} publish → {@link AuditExportListener}가
 *       {@code audit.exported} audit_log 1건 기록 (REQUIRES_NEW).</li>
 * </ul>
 *
 * <p>두 진입점은 동일 필터 집합을 공유 — service의 {@code appendFilterClauses}가 단일 정의.
 *
 * <p>빈 문자열 query param은 null로 정규화 — 프론트 {@code AuditLogFilters.eventType?: '...' | ''}
 * 호환 (빈 = 전체).
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditQueryController {

    private static final DateTimeFormatter FILENAME_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** export 응답 헤더 — cap 초과 시 true. 클라이언트가 사용자 안내에 활용 가능. */
    static final String HEADER_TRUNCATED = "X-Audit-Export-Truncated";

    private final AuditQueryService queryService;
    private final AuditCsvWriter csvWriter;
    private final AuditJsonWriter jsonWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AuditQueryController(AuditQueryService queryService,
                                AuditCsvWriter csvWriter,
                                AuditJsonWriter jsonWriter,
                                ApplicationEventPublisher eventPublisher,
                                ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.csvWriter = csvWriter;
        this.jsonWriter = jsonWriter;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public AuditLogPageDto list(
        @RequestParam(name = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(name = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(name = "actorQuery", required = false) String actorQuery,
        @RequestParam(name = "eventType", required = false) String eventType,
        @RequestParam(name = "targetType", required = false) String targetType,
        @RequestParam(name = "targetId", required = false) UUID targetId,
        @RequestParam(name = "page", required = false, defaultValue = "1") int page,
        @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        AuditQueryFilters filters = new AuditQueryFilters(
            fromDate,
            toDate,
            blankToNull(actorQuery),
            blankToNull(eventType),
            blankToNull(targetType),
            targetId
        );
        return queryService.search(
            filters,
            page,
            pageSize,
            principal.getUser().getId(),
            principal.getUser().getRole()
        );
    }

    /**
     * 감사 로그 다운로드 — CSV(default) 또는 JSON 배열.
     *
     * <p>{@code format} 쿼리 파라미터가 미지정이거나 {@code "csv"}이면 BOM + RFC 4180 CSV,
     * {@code "json"}이면 plain JSON 배열을 반환한다. 그 외 값은
     * {@link IllegalArgumentException}을 던져 {@code GlobalExceptionHandler}가 400
     * {@code BAD_REQUEST}로 변환한다.
     *
     * <p>응답 본문은 {@link StreamingResponseBody}로 작성하지만, 본 v1 구현은 service에서
     * 결과를 한 번에 페치(cap 적용)하여 메모리에 보관 후 stream에 기록한다 — 진정한 SQL streaming은
     * v1.x deferred (KISS).
     *
     * <p>응답이 클라이언트로 commit되기 전에 IP/UA/필터를 캡처해 {@link AuditExportEvent}에 동봉,
     * 본문 작성 후 publish한다. listener가 별도 스레드/트랜잭션에서 audit_log INSERT — audit
     * append-only 원칙(CLAUDE.md §3 원칙 8) 위반 없음 (export 자체는 SELECT only, AUDIT_EXPORTED
     * INSERT는 REQUIRES_NEW 별도 트랜잭션).
     */
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
        @RequestParam(name = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(name = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(name = "actorQuery", required = false) String actorQuery,
        @RequestParam(name = "eventType", required = false) String eventType,
        @RequestParam(name = "targetType", required = false) String targetType,
        @RequestParam(name = "targetId", required = false) UUID targetId,
        @RequestParam(name = "format", required = false, defaultValue = "csv") String format,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        // wire 문자열 검증 → enum. 잘못된 값은 IAE → 글로벌 핸들러 400.
        AuditExportFormat parsedFormat = AuditExportFormat.from(format);
        AuditQueryFilters filters = new AuditQueryFilters(
            fromDate,
            toDate,
            blankToNull(actorQuery),
            blankToNull(eventType),
            blankToNull(targetType),
            targetId
        );
        UUID actorId = principal.getUser().getId();
        AuditQueryService.AuditExportResult result =
            queryService.exportAll(filters, actorId, principal.getUser().getRole());

        // 요청 스레드 컨텍스트(IP/UA)를 즉시 캡처 — StreamingResponseBody 실행 시점에 다른 스레드일 수 있음.
        java.net.InetAddress actorIp = WebRequestContextHolder.currentIp();
        String userAgent = WebRequestContextHolder.currentUserAgent();
        String filtersJson = serializeFilters(filters);
        boolean isJson = parsedFormat == AuditExportFormat.JSON;

        StreamingResponseBody body = out -> {
            try {
                if (isJson) {
                    jsonWriter.write(out, result.entries());
                } else {
                    csvWriter.write(out, result.entries());
                }
            } catch (IOException e) {
                // 클라이언트 disconnect 등 — 응답은 깨졌지만 서버 측 상태 변경 없음. 로그만.
                throw new RuntimeException(e);
            }
            // 본문 작성 성공 후에만 audit emit — 실패한 export는 기록하지 않는다.
            eventPublisher.publishEvent(new AuditExportEvent(
                actorId, actorIp, userAgent, filtersJson,
                result.entries().size(), result.truncated(), parsedFormat
            ));
        };

        HttpHeaders headers = new HttpHeaders();
        if (isJson) {
            headers.setContentType(new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8));
        } else {
            headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        }
        String extension = parsedFormat.wire();
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
            .filename("audit_logs_" + LocalDate.now().format(FILENAME_DATE) + "." + extension)
            .build());
        if (result.truncated()) {
            headers.add(HEADER_TRUNCATED, "true");
        }
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * 적용된 필터를 audit metadata용 JSON으로 직렬화. null 필드는 제외 (LinkedHashMap insertion order로
     * 결정적 출력 보장).
     */
    private String serializeFilters(AuditQueryFilters filters) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (filters.fromDate() != null) map.put("fromDate", filters.fromDate().toString());
        if (filters.toDate() != null) map.put("toDate", filters.toDate().toString());
        if (filters.actorQuery() != null) map.put("actorQuery", filters.actorQuery());
        if (filters.eventType() != null) map.put("eventType", filters.eventType());
        if (filters.targetType() != null) map.put("targetType", filters.targetType());
        if (filters.targetId() != null) map.put("targetId", filters.targetId().toString());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // 필터 직렬화 실패는 audit metadata 일관성을 위해 빈 객체로 대체 — 응답 본문은 영향 없음.
            return "{}";
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
