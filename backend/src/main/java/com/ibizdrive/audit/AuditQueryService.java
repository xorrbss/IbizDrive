package com.ibizdrive.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.dto.AuditLogEntryDto;
import com.ibizdrive.audit.dto.AuditLogPageDto;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
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
 *   <li><b>RP-2 (M-RP.4)</b>: {@code targetType="file"} + {@code targetId} 지정 + 호출자가 해당
 *       파일에 {@link Permission#READ} 보유 → MEMBER도 actor 제한을 우회하고 모든 actor의 이벤트
 *       조회. 활동 타임라인 UI("이 파일의 history")의 본질적 의미. 그 외에는 기존 정책 유지
 *       (ADR #40 closure).</li>
 * </ul>
 *
 * <p><b>JdbcTemplate 채택 이유</b>: write 경로({@link AuditService})가 이미 JdbcTemplate +
 * {@code ?::jsonb}/{@code ?::inet} 캐스트를 사용하므로 read 경로도 동일 스택으로 통일.
 * JPA Specification은 8개 필터에 비해 빌드/테스트 비용이 과대 — KISS (CLAUDE.md §3 원칙 1).
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

    /** RP-2 권한 분기 대상 리소스 타입. file 외에는 적용 안 함 (KISS — folder activity는 v1.x). */
    private static final String RP2_RESOURCE_TYPE_FILE = "file";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PermissionResolver permissionResolver;

    public AuditQueryService(JdbcTemplate jdbc,
                             ObjectMapper objectMapper,
                             PermissionResolver permissionResolver) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.permissionResolver = permissionResolver;
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

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");
        appendFilterClauses(where, args, filters, viewerId, viewerRole);

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

    /**
     * 전체 결과 export — {@code GET /api/admin/audit/export} 전용.
     *
     * <p>{@link #search}와 동일한 필터·권한 정책을 재사용하지만 페이징 없이 전체 결과를 반환한다.
     * 메모리·DoS 방어로 hard cap {@value #EXPORT_ROW_CAP}를 적용 — {@code LIMIT cap+1}로 페치하여
     * cap 초과 여부를 판별하고, 초과 시 처음 cap개만 반환 + {@link AuditExportResult#truncated()} = true.
     *
     * <p>호출 측({@link AuditQueryController#exportCsv})은 {@code @PreAuthorize}로 ADMIN/AUDITOR만
     * 진입을 허용하지만, 본 메서드는 권한 가드를 service 진입점에서도 한 번 더 방어한다 — 우회 호출
     * (예: 내부 콜러)에서 MEMBER가 들어와도 export 자체는 허용하지 않는다.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AuditExportResult exportAll(AuditQueryFilters filters, UUID viewerId, Role viewerRole) {
        if (viewerRole != Role.ADMIN && viewerRole != Role.AUDITOR) {
            // 방어적 가드 — controller `@PreAuthorize`가 1차 방어이므로 정상 흐름에선 도달하지 않는다.
            throw new IllegalStateException("audit export requires ADMIN or AUDITOR role");
        }

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");
        appendFilterClauses(where, args, filters, viewerId, viewerRole);

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(EXPORT_ROW_CAP + 1);   // cap 초과 감지를 위해 1건 더 페치
        List<AuditLogEntryDto> rows = jdbc.query(
            "SELECT " + SELECT_COLUMNS + " " + FROM_JOIN + " " + where +
            " ORDER BY a.occurred_at DESC, a.id DESC LIMIT ?",
            this::mapRow,
            pageArgs.toArray()
        );

        boolean truncated = rows.size() > EXPORT_ROW_CAP;
        List<AuditLogEntryDto> capped = truncated ? rows.subList(0, EXPORT_ROW_CAP) : rows;
        return new AuditExportResult(List.copyOf(capped), truncated);
    }

    /** export 결과 — 행 목록 + cap 초과 여부. {@link AuditQueryController}가 헤더/audit metadata에 사용. */
    public record AuditExportResult(List<AuditLogEntryDto> entries, boolean truncated) {}

    /** export 행 hard cap (DoS·OOM 방어). 초과 export는 v1.x 배치 작업으로 분리 (docs/04 §13). */
    public static final int EXPORT_ROW_CAP = 10_000;

    /**
     * WHERE 절 + bind args 빌더 — {@link #search}와 {@link #exportAll}이 공유.
     *
     * <p>분리된 이유: 두 진입점이 동일한 필터·권한 의미를 가져야 하므로 한 곳에서 관리. 한쪽만
     * 수정해 양 결과가 어긋나는 회귀를 차단.
     */
    private void appendFilterClauses(StringBuilder where,
                                      List<Object> args,
                                      AuditQueryFilters filters,
                                      UUID viewerId,
                                      Role viewerRole) {
        // MEMBER scope 강제 — ADR #1 트랙결정 #4. 권한 분기는 service 단일 진입점에서.
        // RP-2 (ADR #40): targetType=file + targetId + 호출자 READ 보유 시 actor 제한을 건너뛴다.
        // export 경로는 ADMIN/AUDITOR만 도달하므로 이 분기는 사실상 search 경로용.
        if (viewerRole == Role.MEMBER && !rp2BypassesActorScope(filters, viewerId)) {
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
        if (filters.targetType() != null && !filters.targetType().isBlank()) {
            where.append(" AND a.target_type = ?");
            args.add(filters.targetType());
        }
        if (filters.targetId() != null) {
            where.append(" AND a.target_id = ?");
            args.add(filters.targetId());
        }
    }

    /**
     * RP-2 (ADR #40): MEMBER 호출자가 file 활동 타임라인을 조회할 때 actor 제한을 우회할지 결정.
     *
     * <p>적용 조건 모두 만족 시 true:
     * <ul>
     *   <li>{@code targetType="file"} + {@code targetId != null} (특정 파일 활동 타임라인)</li>
     *   <li>호출자가 해당 파일에 {@link Permission#READ} 보유 (resource-level grant — 재귀 상속 포함)</li>
     * </ul>
     *
     * <p>READ 미보유 시 false → 기존 actor=self 정책 유지(빈 결과 또는 자기 액션만 노출). 명시적 403은
     * 던지지 않는다 — 비공개 파일에 대한 존재 자체를 노출하지 않기 위한 보수적 처리.
     */
    private boolean rp2BypassesActorScope(AuditQueryFilters filters, UUID viewerId) {
        if (!RP2_RESOURCE_TYPE_FILE.equals(filters.targetType())) return false;
        if (filters.targetId() == null) return false;
        return permissionResolver.isGranted(
            viewerId, RP2_RESOURCE_TYPE_FILE, filters.targetId(), Permission.READ
        );
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
