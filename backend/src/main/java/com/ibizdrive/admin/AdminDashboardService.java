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

/**
 * admin-dashboard — `/admin` 진입 KPI 6종 집계 서비스.
 *
 * <p>Read-only 단일 메서드 {@link #getSummary()}. audit emit ❌ (관찰 KPI는 audit 대상 아님).
 *
 * <p><b>호출 분포</b>:
 * <ul>
 *   <li>JPA derived count × 6: User(2) + Department(1) + Folder(1) + File(2)</li>
 *   <li>JPQL `@Query` × 1: {@link FileVersionRepository#sumAllVersionSizeBytes}</li>
 *   <li>JdbcTemplate native × 1: {@code audit_log} count since now-24h
 *       ({@code audit_log}는 JdbcTemplate 채널 — {@link com.ibizdrive.audit.AuditQueryService} 일관성).</li>
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
        long usersTotal = userRepository.countByDeletedAtIsNull();
        long usersActive = userRepository.countByDeletedAtIsNullAndIsActiveTrue();
        long deptCount = departmentRepository.countByDeletedAtIsNull();
        long foldersActive = folderRepository.countByDeletedAtIsNull();
        long filesActive = fileRepository.countActiveFiles();
        long filesTrashed = fileRepository.countTrashedFiles();
        long storageUsed = fileVersionRepository.sumAllVersionSizeBytes();
        long auditLast24h = countAuditSince(Instant.now(clock).minus(AUDIT_WINDOW));

        return new AdminDashboardSummaryResponse.SummaryData(
            new AdminDashboardSummaryResponse.Users(usersTotal, usersActive),
            // Department는 is_active 컬럼 없음 — total === active (envelope 형태 일관성).
            new AdminDashboardSummaryResponse.Departments(deptCount, deptCount),
            new AdminDashboardSummaryResponse.Folders(foldersActive),
            new AdminDashboardSummaryResponse.Files(filesActive, filesTrashed),
            new AdminDashboardSummaryResponse.Audit(auditLast24h),
            new AdminDashboardSummaryResponse.Storage(storageUsed)
        );
    }

    private long countAuditSince(Instant since) {
        Long boxed = jdbc.queryForObject(
            COUNT_AUDIT_SINCE_SQL, Long.class, Timestamp.from(since));
        return boxed == null ? 0L : boxed;
    }
}
