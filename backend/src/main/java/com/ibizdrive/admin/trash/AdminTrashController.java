package com.ibizdrive.admin.trash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.approval.ApprovalRequiredException;
import com.ibizdrive.approval.PendingAdminApproval;
import com.ibizdrive.approval.PendingApprovalService;
import com.ibizdrive.approval.handlers.TrashPurgeApprovalHandler;
import com.ibizdrive.approval.handlers.TrashPurgePayload;
import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
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
 * {@code YYYY-MM-DD} (date-only)로 받는다. KST({@code Asia/Seoul}) 경계로 변환:
 * 하한은 해당일 KST 00:00:00 (= UTC 전날 15:00:00Z, inclusive), 상한은 다음 날 KST 00:00:00
 * (= 입력일 KST 24시, exclusive — 입력일 KST 종일 포함).
 *
 * <p>운영자(한국 사내) 의도와 wall-clock 일치 — 운영자가 {@code 2026-05-08} 입력 시 KST 5/8
 * 0~24시에 deleted된 row가 검색된다. PR #83 follow-up: 기존 UTC 변환은 9시간 시프트되어
 * KST 9시 ~ 익일 9시를 검색하던 버그 수정.
 *
 * <p>ADR #47 Phase 3c — bulk action='purge' dual-approval 게이트:
 * {@code app.dual-approval.trash-purge.enabled=true}이면 controller가 framework {@code submit}
 * 후 {@link ApprovalRequiredException} throw → 202 ACCEPTED + envelope {@code APPROVAL_REQUIRED}.
 * action='restore'는 게이트 무관(복원은 비파괴). default OFF.
 */
@RestController
@RequestMapping("/api/admin/trash")
public class AdminTrashController {

    /** dual-approval framework default TTL (ADR #47 default 정합). */
    private static final int APPROVAL_TTL_DAYS = 7;

    private final AdminTrashService service;
    private final PendingApprovalService approvalService;
    private final ObjectMapper objectMapper;
    private final boolean dualApprovalPurgeEnabled;

    public AdminTrashController(
        AdminTrashService service,
        PendingApprovalService approvalService,
        ObjectMapper objectMapper,
        @Value("${app.dual-approval.trash-purge.enabled:false}") boolean dualApprovalPurgeEnabled
    ) {
        this.service = service;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
        this.dualApprovalPurgeEnabled = dualApprovalPurgeEnabled;
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
     * Bulk restore/purge — Wave 2 T9 follow-up (spec §3), ADR #47 Phase 3c 게이트.
     *
     * <p>응답 status는 부분 실패에도 항상 200. cap/action 검증 실패만 400 (글로벌 핸들러).
     * 단건 endpoint(`POST /api/files|folders/{id}/restore`, `DELETE /api/trash/{type}/{id}`)는
     * 무변경 — 본 endpoint는 그 service layer를 fan-out 호출만 한다.
     *
     * <p>게이트 분기: {@code app.dual-approval.trash-purge.enabled=true} + action='purge' →
     * {@link PendingApprovalService#submit} 후 {@link ApprovalRequiredException} → 202.
     * action='restore' 또는 invalid action은 게이트 무관 (복원은 비파괴, invalid는 service IAE).
     * items 1..200 cap은 framework submit 전 한 번 더 검사 — 빈 approval row 생성 차단.
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminTrashBulkResponseDto> bulk(
        @RequestBody AdminTrashBulkRequestDto req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        UUID actorId = principal.getUser().getId();
        if (dualApprovalPurgeEnabled && req != null && "purge".equals(req.action())) {
            if (req.items() == null || req.items().isEmpty()
                || req.items().size() > AdminTrashService.BULK_MAX_ITEMS) {
                throw new IllegalArgumentException("items must be 1..200");
            }
            String payloadJson = serializePayload(req.items(), /*reason*/ null);
            PendingAdminApproval pending = approvalService.submit(
                TrashPurgeApprovalHandler.ACTION_TYPE,
                payloadJson,
                actorId,
                APPROVAL_TTL_DAYS);
            throw new ApprovalRequiredException(pending.getId(), pending.getExpiresAt());
        }
        AdminTrashBulkResponseDto result = service.bulk(
            req == null ? null : req.action(),
            req == null ? null : req.items(),
            actorId
        );
        return ResponseEntity.ok(result);
    }

    private String serializePayload(List<AdminTrashBulkRequestDto.Item> items, String reason) {
        try {
            List<TrashPurgePayload.Item> mapped = items.stream()
                .map(i -> new TrashPurgePayload.Item(i.type(), i.id()))
                .toList();
            return objectMapper.writeValueAsString(new TrashPurgePayload(mapped, reason));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("trash_purge payload serialize failed", e);
        }
    }

    /** 사내 단일 지역 운영 — 다중 리전 진입 시 application.yml 주입으로 일원화(YAGNI). */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * {@code YYYY-MM-DD} → KST 경계 instant.
     *
     * @param exclusiveUpperBound true면 다음 날 KST 00:00(상한 exclusive),
     *                            false면 당일 KST 00:00(하한 inclusive).
     */
    private static Instant parseDateBoundary(String raw, String fieldName, boolean exclusiveUpperBound) {
        if (raw == null || raw.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(raw);
            LocalDate boundary = exclusiveUpperBound ? d.plusDays(1) : d;
            return boundary.atStartOfDay(KST).toInstant();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                "invalid " + fieldName + " (expected YYYY-MM-DD)", ex);
        }
    }
}
