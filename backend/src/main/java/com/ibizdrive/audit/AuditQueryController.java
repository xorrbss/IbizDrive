package com.ibizdrive.audit;

import com.ibizdrive.audit.dto.AuditLogPageDto;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 감사 로그 조회 endpoint — docs/02 §7.12 (rename: {@code /api/admin/audit-logs} → {@code /api/admin/audit}).
 *
 * <p>인증 가드: {@code SecurityConfig.anyRequest().authenticated()} 적용 — 익명 호출은 401.
 * 추가 ROLE 가드는 service에서 처리 (트랙 결정 #4): ADMIN/AUDITOR 전체, MEMBER {@code actor_id=self}.
 *
 * <p>응답: {@link AuditLogPageDto} — frontend {@code AuditLogPage}와 wire 동치.
 *
 * <p>빈 문자열 query param은 null로 정규화 — 프론트 {@code AuditLogFilters.eventType?: '...' | ''}
 * 호환 (빈 = 전체).
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditQueryController {

    private final AuditQueryService queryService;

    public AuditQueryController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public AuditLogPageDto list(
        @RequestParam(name = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(name = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(name = "actorQuery", required = false) String actorQuery,
        @RequestParam(name = "eventType", required = false) String eventType,
        @RequestParam(name = "page", required = false, defaultValue = "1") int page,
        @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        AuditQueryFilters filters = new AuditQueryFilters(
            fromDate,
            toDate,
            blankToNull(actorQuery),
            blankToNull(eventType)
        );
        return queryService.search(
            filters,
            page,
            pageSize,
            principal.getUser().getId(),
            principal.getUser().getRole()
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
