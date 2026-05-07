package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash listing controller (spec §5.1, docs/02 §7.11).
 *
 * <p>{@code GET /api/admin/trash} 단일 endpoint. mutation은 본 트랙에서 신규 endpoint 0:
 * 기존 {@code POST /api/files|folders/{id}/restore} + {@code DELETE /api/trash/{type}/{id}}
 * 재사용 (ADMIN ROLE이 SpEL 가드 통과 — spec §3 표).
 *
 * <p>{@code IllegalArgumentException}은 글로벌 핸들러에서 400으로 매핑된다고 가정 — 본 트랙
 * 신규 핸들러 추가 없음 (T1/T4 패턴).
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
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        TrashItemType parsedType = type != null ? TrashItemType.from(type) : null;
        UUID parsedOwner = ownerId != null ? UUID.fromString(ownerId) : null;

        AdminTrashFilters filters = new AdminTrashFilters(q, parsedType, parsedOwner);
        return ResponseEntity.ok(service.list(filters, cursor, limit));
    }
}
