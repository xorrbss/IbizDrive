package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash listing controller (spec §5.1, docs/02 §7.11).
 *
 * <p>{@code GET /api/admin/trash} 단일 endpoint. mutation은 본 트랙에서 신규 endpoint 0:
 * 기존 {@code POST /api/files|folders/{id}/restore} + {@code DELETE /api/trash/{type}/{id}}
 * 재사용 (ADMIN ROLE이 SpEL 가드 통과 — spec §3 표).
 *
 * <p>{@code IllegalArgumentException}은 글로벌 핸들러에서 400으로 매핑된다고 가정 — 본 트랙
 * 신규 핸들러 추가 없음 (T1/T4 패턴). {@code DateTimeParseException}은 IAE 비하위이므로
 * 명시적으로 catch 후 IAE로 변환.
 *
 * <p>날짜 범위 필터(Wave 2 T9 follow-up): {@code deletedFrom}/{@code deletedTo}는
 * {@code YYYY-MM-DD} (date-only)로 받는다. UTC 경계로 변환:
 * 하한은 해당일 00:00:00Z(inclusive), 상한은 다음 날 00:00:00Z(exclusive — 입력일 종일 포함).
 */
@RestController
@RequestMapping("/api/admin/trash")
public class AdminTrashController {

    private final AdminTrashService service;

    public AdminTrashController(AdminTrashService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashPage> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String ownerId,
        @RequestParam(required = false) String deletedFrom,
        @RequestParam(required = false) String deletedTo,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        TrashItemType parsedType = type != null ? TrashItemType.from(type) : null;
        UUID parsedOwner = ownerId != null ? UUID.fromString(ownerId) : null;
        Instant deletedFromMin = parseDateBoundary(deletedFrom, "deletedFrom", false);
        Instant deletedToMax = parseDateBoundary(deletedTo, "deletedTo", true);

        AdminTrashFilters filters = new AdminTrashFilters(
            q, parsedType, parsedOwner, deletedFromMin, deletedToMax);
        return ResponseEntity.ok(service.list(filters, cursor, limit));
    }

    /**
     * Bulk restore/purge — Wave 2 T9 follow-up (spec §3).
     *
     * <p>응답 status는 부분 실패에도 항상 200. cap/action 검증 실패만 400 (글로벌 핸들러).
     * 단건 endpoint(`POST /api/files|folders/{id}/restore`, `DELETE /api/trash/{type}/{id}`)는
     * 무변경 — 본 endpoint는 그 service layer를 fan-out 호출만 한다.
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashBulkResponseDto> bulk(
        @RequestBody AdminTrashBulkRequestDto req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        UUID actorId = principal.getUser().getId();
        AdminTrashBulkResponseDto result = service.bulk(
            req == null ? null : req.action(),
            req == null ? null : req.items(),
            actorId
        );
        return ResponseEntity.ok(result);
    }

    /**
     * {@code YYYY-MM-DD} → UTC instant 경계.
     *
     * @param exclusiveUpperBound true면 다음 날 00:00:00Z(상한 exclusive),
     *                            false면 당일 00:00:00Z(하한 inclusive).
     */
    private static Instant parseDateBoundary(String raw, String fieldName, boolean exclusiveUpperBound) {
        if (raw == null || raw.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(raw);
            LocalDate boundary = exclusiveUpperBound ? d.plusDays(1) : d;
            return boundary.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                "invalid " + fieldName + " (expected YYYY-MM-DD)", ex);
        }
    }
}
