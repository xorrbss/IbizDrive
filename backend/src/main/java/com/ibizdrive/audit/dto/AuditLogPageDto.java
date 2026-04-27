package com.ibizdrive.audit.dto;

import java.util.List;

/**
 * 감사 로그 페이지 응답 — {@code frontend/src/types/audit.ts}의 {@code AuditLogPage}와 wire 동치.
 *
 * <p>{@code page}는 1-indexed (frontend api.ts {@code getAuditLogs(filters, page=1, pageSize=20)} 일치).
 */
public record AuditLogPageDto(
    List<AuditLogEntryDto> entries,
    long total,
    int page,
    int pageSize
) {
}
