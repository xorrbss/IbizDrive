package com.ibizdrive.admin;

import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * admin-dashboard — `/admin` 진입 KPI 8종 + delta 8종 집계 서비스.
 *
 * <p>Read-only 단일 메서드 {@link #getSummary()}. audit emit ❌ (관찰 KPI는 audit 대상 아님).
 *
 * <p><b>호출 분포</b>:
 * <ul>
 *   <li>JPA derived count × 6: User(2) + Department(1) + Folder(1) + File(2)</li>
 *   <li>JPQL `@Query` × 1: {@link FileVersionRepository#sumAllVersionSizeBytes}</li>
 *   <li>JdbcTemplate native × 1: {@code audit_log} count since now-24h
 *       ({@code audit_log}는 JdbcTemplate 채널 — {@link com.ibizdrive.audit.AuditQueryService} 일관성).</li>
 *   <li><b>delta (P4)</b>: 위 8 query의 "asOf" 변형 × 8 + audit prev-24h × 1 = 9 추가 query.</li>
 * </ul>
 *
 * <p><b>delta 정책 (P4 — admin-dashboard-kpi-delta-backend)</b>:
 * <ul>
 *   <li>비교 윈도우: stock 30d ({@link #STOCK_DELTA_WINDOW}), audit 자연 윈도우 24h.</li>
 *   <li>delta = (current - asOf) / asOf, 분모 0 또는 음수 시 {@code null} 반환.</li>
 *   <li>asOf snapshot 정의는 각 Repository {@code countAliveAsOf} / {@code countTrashedFilesAsOf}
 *       Javadoc 참고. {@code users.activeDelta}는 현재 {@code is_active}를 historical에 적용한 근사.</li>
 * </ul>
 *
 * <p><b>에러 정책</b>: 카운트 중 일부 실패 → 예외 전파 (500). 부분 응답 ❌ — envelope 오염 방지.
 *
 * <p><b>테스트 가능성</b>: {@link Clock} 주입으로 audit `since = now - 24h` 결정적 검증.
 * 운영 환경에서는 {@link com.ibizdrive.config} 어디에도 Clock 빈이 없으므로 `Clock.systemUTC()`
 * default 빈을 본 트랙에서 등록한다 ({@link com.ibizdrive.config.ClockConfig} 또는 동등).
 */
@Service
public class AdminDashboardService {

    private static final Duration AUDIT_WINDOW = Duration.ofHours(24);

    /**
     * stock KPI delta 비교 윈도우 — 프론트 KPI 카드 aria-label "전월 대비"와 정합 (30일).
     * audit 지표는 자연 24h 윈도우라 본 상수와 무관.
     */
    private static final Duration STOCK_DELTA_WINDOW = Duration.ofDays(30);

    private static final String COUNT_AUDIT_BETWEEN_SQL =
        "SELECT COUNT(*) FROM audit_log WHERE occurred_at >= ? AND occurred_at < ?";

    private static final String COUNT_AUDIT_SINCE_SQL =
        "SELECT COUNT(*) FROM audit_log WHERE occurred_at >= ?";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminDashboardService(UserRepository userRepository,
                                 DepartmentRepository departmentRepository,
                                 FolderRepository folderRepository,
                                 FileRepository fileRepository,
                                 FileVersionRepository fileVersionRepository,
                                 JdbcTemplate jdbc,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse.SummaryData getSummary() {
        Instant now = Instant.now(clock);
        Instant stockAsOf = now.minus(STOCK_DELTA_WINDOW);
        // User/Department entity 시간 컬럼이 OffsetDateTime — UTC 변환 (clock 타임존과 무관하게 결정적).
        OffsetDateTime stockAsOfOdt = OffsetDateTime.ofInstant(stockAsOf, ZoneOffset.UTC);

        long usersTotal = userRepository.countByDeletedAtIsNull();
        long usersActive = userRepository.countByDeletedAtIsNullAndIsActiveTrue();
        long deptCount = departmentRepository.countByDeletedAtIsNull();
        long foldersActive = folderRepository.countByDeletedAtIsNull();
        long filesActive = fileRepository.countActiveFiles();
        long filesTrashed = fileRepository.countTrashedFiles();
        long storageUsed = fileVersionRepository.sumAllVersionSizeBytes();

        Instant auditWindowStart = now.minus(AUDIT_WINDOW);
        Instant auditPrevWindowStart = now.minus(AUDIT_WINDOW.multipliedBy(2));
        long auditLast24h = countAuditSince(auditWindowStart);
        long auditPrev24h = countAuditBetween(auditPrevWindowStart, auditWindowStart);

        long usersTotalAtAsOf = userRepository.countAliveAsOf(stockAsOfOdt);
        long usersActiveAtAsOf = userRepository.countActiveAsOf(stockAsOfOdt);
        long deptCountAtAsOf = departmentRepository.countAliveAsOf(stockAsOfOdt);
        long foldersActiveAtAsOf = folderRepository.countAliveAsOf(stockAsOf);
        long filesActiveAtAsOf = fileRepository.countActiveFilesAsOf(stockAsOf);
        long filesTrashedAtAsOf = fileRepository.countTrashedFilesAsOf(stockAsOf);
        long storageUsedAtAsOf = fileVersionRepository.sumVersionSizeBytesAsOf(stockAsOf);

        return new AdminDashboardSummaryResponse.SummaryData(
            new AdminDashboardSummaryResponse.Users(
                usersTotal, usersActive,
                computeDelta(usersTotal, usersTotalAtAsOf),
                computeDelta(usersActive, usersActiveAtAsOf)
            ),
            // Department는 is_active 컬럼 없음 — total === active (envelope 형태 일관성).
            new AdminDashboardSummaryResponse.Departments(
                deptCount, deptCount,
                computeDelta(deptCount, deptCountAtAsOf)
            ),
            new AdminDashboardSummaryResponse.Folders(
                foldersActive,
                computeDelta(foldersActive, foldersActiveAtAsOf)
            ),
            new AdminDashboardSummaryResponse.Files(
                filesActive, filesTrashed,
                computeDelta(filesActive, filesActiveAtAsOf),
                computeDelta(filesTrashed, filesTrashedAtAsOf)
            ),
            new AdminDashboardSummaryResponse.Audit(
                auditLast24h,
                computeDelta(auditLast24h, auditPrev24h)
            ),
            new AdminDashboardSummaryResponse.Storage(
                storageUsed,
                computeDelta(storageUsed, storageUsedAtAsOf)
            )
        );
    }

    /**
     * 변화율 = (current - baseline) / baseline. baseline ≤ 0 이면 {@code null} 반환 — 분모가 0이거나
     * 비교 시점에 데이터가 없어 비율 의미가 성립하지 않는 케이스. 프론트는 {@code delta == null}을
     * "변화 정보 없음"으로 표시 (DashboardKpiCard.tsx).
     */
    static Double computeDelta(long current, long baseline) {
        if (baseline <= 0L) {
            return null;
        }
        return ((double) (current - baseline)) / (double) baseline;
    }

    private long countAuditSince(Instant since) {
        Long boxed = jdbc.queryForObject(
            COUNT_AUDIT_SINCE_SQL, Long.class, Timestamp.from(since));
        return boxed == null ? 0L : boxed;
    }

    private long countAuditBetween(Instant since, Instant until) {
        Long boxed = jdbc.queryForObject(
            COUNT_AUDIT_BETWEEN_SQL, Long.class,
            Timestamp.from(since), Timestamp.from(until));
        return boxed == null ? 0L : boxed;
    }
}
