package com.ibizdrive.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.dto.AuditLogEntryDto;
import com.ibizdrive.audit.dto.AuditLogPageDto;
import com.ibizdrive.user.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 감사 로그 read API 서비스 (ADR #24, 트랙 결정 #4).
 *
 * <p><b>권한 매트릭스</b>:
 * <ul>
 *   <li>{@link Role#ADMIN}, {@link Role#AUDITOR}: 전체 audit_log 조회</li>
 *   <li>{@link Role#MEMBER}: {@code actor_id = viewer.id} 강제 (자기 활동만)</li>
 * </ul>
 *
 * <p><b>JdbcTemplate 채택 이유</b>: write 경로({@link AuditService})가 이미 JdbcTemplate +
 * {@code ?::jsonb}/{@code ?::inet} 캐스트를 사용하므로 read 경로도 동일 스택으로 통일.
 * JPA Specification은 6개 필터에 비해 빌드/테스트 비용이 과대 — KISS (CLAUDE.md §3 원칙 1).
 *
 * <p><b>날짜 변환</b>: {@code AuditQueryFilters}의 {@code LocalDate}는 UTC 자정 기준으로
 * {@code OffsetDateTime} 경계로 확장 (docs/03 §4.2 UTC 저장 정책).
 */
@Service
public class AuditQueryService {

    /** SELECT 컬럼 순서 — RowMapper와 일치 강제. */
    private static final String SELECT_COLUMNS =
        "a.id, a.occurred_at, a.event_type, a.actor_id, " +
        "u.display_name AS actor_name, " +
        "a.target_type, a.target_id, " +
        "host(a.actor_ip) AS actor_ip_str, " +
        "a.metadata::text AS metadata_json";

    private static final String FROM_JOIN =
        "FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * @param filters     검색 필터 (모두 nullable). null이면 전체.
     * @param page        1-indexed. {@code <= 0}은 1로 보정.
     * @param pageSize    {@code 1..200} 범위로 클램프 (남용 방지).
     * @param viewerId    호출자 user id. MEMBER scope 강제에 사용.
     * @param viewerRole  호출자 ROLE. ADMIN/AUDITOR는 전체, MEMBER는 self.
     */
    public AuditLogPageDto search(AuditQueryFilters filters,
                                   int page,
                                   int pageSize,
                                   UUID viewerId,
                                   Role viewerRole) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        int offset = (safePage - 1) * safeSize;

        // WHERE 절 동적 빌드 — 각 필터 존재 시에만 추가하여 인덱스 활용도 최대화.
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");

        // MEMBER scope 강제 — ADR #1 트랙결정 #4. 권한 분기는 service 단일 진입점에서.
        if (viewerRole == Role.MEMBER) {
            where.append(" AND a.actor_id = ?");
            args.add(viewerId);
        }

        if (filters.eventType() != null && !filters.eventType().isBlank()) {
            where.append(" AND a.event_type = ?");
            args.add(filters.eventType());
        }
        if (filters.actorQuery() != null && !filters.actorQuery().isBlank()) {
            where.append(" AND LOWER(u.display_name) LIKE ?");
            args.add("%" + filters.actorQuery().toLowerCase() + "%");
        }
        if (filters.fromDate() != null) {
            where.append(" AND a.occurred_at >= ?");
            args.add(OffsetDateTime.of(filters.fromDate().atStartOfDay(), ZoneOffset.UTC));
        }
        if (filters.toDate() != null) {
            where.append(" AND a.occurred_at < ?");
            // inclusive 끝 — 다음 날 00:00 UTC exclusive.
            args.add(OffsetDateTime.of(filters.toDate().plusDays(1).atStartOfDay(), ZoneOffset.UTC));
        }

        // total: 별도 COUNT — pagination 메타와 entries 분리.
        Long totalBoxed = jdbc.queryForObject(
            "SELECT COUNT(*) " + FROM_JOIN + " " + where,
            Long.class,
            args.toArray()
        );
        long total = totalBoxed == null ? 0L : totalBoxed;

        // entries: occurred_at DESC, id DESC tiebreaker (동일 ts 내 결정적 정렬).
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(offset);
        List<AuditLogEntryDto> entries = jdbc.query(
            "SELECT " + SELECT_COLUMNS + " " + FROM_JOIN + " " + where +
            " ORDER BY a.occurred_at DESC, a.id DESC LIMIT ? OFFSET ?",
            this::mapRow,
            pageArgs.toArray()
        );

        return new AuditLogPageDto(entries, total, safePage, safeSize);
    }

    private AuditLogEntryDto mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        UUID actorId = (UUID) rs.getObject("actor_id");
        UUID targetId = (UUID) rs.getObject("target_id");
        String metadataJson = rs.getString("metadata_json");
        Map<String, Object> metadata = parseMetadata(metadataJson);

        OffsetDateTime occurredAt = rs.getObject("occurred_at", OffsetDateTime.class);

        return new AuditLogEntryDto(
            Long.toString(rs.getLong("id")),
            occurredAt,
            rs.getString("event_type"),
            actorId,
            rs.getString("actor_name"),
            rs.getString("target_type"),
            targetId,
            null,                                 // resourceName: not stored — v1.x
            rs.getString("actor_ip_str"),
            metadata
        );
    }

    /**
     * JSONB 텍스트를 {@code Map<String, Object>}로 역직렬화.
     * 빈 jsonb({@code "{}"}) → 빈 맵, null → null. 파싱 실패는 빈 맵으로 폴백 + 로그 (audit read는
     * 정확성보다 가용성 우선 — 한 row의 metadata 손상이 전체 페이지 응답을 막지 않도록).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            // 손상된 JSON은 빈 맵으로 폴백. 운영 alert는 v1.x.
            return Collections.emptyMap();
        }
    }
}
