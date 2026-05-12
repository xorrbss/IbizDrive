package com.ibizdrive.admin;

import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * admin-dashboard P1 + P4 — {@link AdminDashboardService} 단위 테스트.
 *
 * <p>repositories + JdbcTemplate(audit) mock. {@link AdminDepartmentServiceTest} mockito 패턴 1:1 답습.
 * Repository derived/native count 메서드 + {@code FileVersionRepository.sumAllVersionSizeBytes} + JdbcTemplate
 * audit count는 통합 검증을 별도 IT(skip-without-docker)에서 다룬다.
 *
 * <p><b>P4 — delta scenario coverage</b>:
 * <ul>
 *   <li>delta 계산 정확도 (양/음/0)</li>
 *   <li>분모 0 → null</li>
 *   <li>30d asOf 파라미터 전달 정확성</li>
 *   <li>audit prev 24h window separation</li>
 * </ul>
 */
class AdminDashboardServiceTest {

    private UserRepository userRepository;
    private DepartmentRepository departmentRepository;
    private FolderRepository folderRepository;
    private FileRepository fileRepository;
    private FileVersionRepository fileVersionRepository;
    private JdbcTemplate jdbc;
    private Clock clock;

    private AdminDashboardService service;

    /** 고정 시각 — audit count `since` 검증을 결정적으로 만들기 위함. */
    private static final Instant FIXED_NOW = Instant.parse("2026-05-07T12:00:00Z");

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        folderRepository = mock(FolderRepository.class);
        fileRepository = mock(FileRepository.class);
        fileVersionRepository = mock(FileVersionRepository.class);
        jdbc = mock(JdbcTemplate.class);
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

        service = new AdminDashboardService(
            userRepository, departmentRepository, folderRepository,
            fileRepository, fileVersionRepository, jdbc, clock);
    }

    /** 모든 count/sum/audit가 0을 반환하도록 default stub 적용 (테스트별 override 가능). */
    private void stubAllZero() {
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(0L);
        when(userRepository.countAliveAsOf(any(OffsetDateTime.class))).thenReturn(0L);
        when(userRepository.countActiveAsOf(any(OffsetDateTime.class))).thenReturn(0L);
        when(departmentRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(departmentRepository.countAliveAsOf(any(OffsetDateTime.class))).thenReturn(0L);
        when(folderRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(folderRepository.countAliveAsOf(any(Instant.class))).thenReturn(0L);
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileRepository.countActiveFilesAsOf(any(Instant.class))).thenReturn(0L);
        when(fileRepository.countTrashedFilesAsOf(any(Instant.class))).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);
        when(fileVersionRepository.sumVersionSizeBytesAsOf(any(Instant.class))).thenReturn(0L);
        // audit 1-arg (since) + 2-arg (between) 둘 다 default 0.
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class), any(Timestamp.class)))
            .thenReturn(0L);
    }

    @Test
    void getSummary_aggregatesAllCounts() {
        stubAllZero();
        when(userRepository.countByDeletedAtIsNull()).thenReturn(42L);
        when(userRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(38L);
        when(departmentRepository.countByDeletedAtIsNull()).thenReturn(7L);
        when(folderRepository.countByDeletedAtIsNull()).thenReturn(1234L);
        when(fileRepository.countActiveFiles()).thenReturn(9876L);
        when(fileRepository.countTrashedFiles()).thenReturn(123L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(1234567890L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(456L);

        AdminDashboardSummaryResponse.SummaryData summary = service.getSummary();

        assertThat(summary.users().total()).isEqualTo(42L);
        assertThat(summary.users().active()).isEqualTo(38L);
        assertThat(summary.departments().total()).isEqualTo(7L);
        assertThat(summary.departments().active()).isEqualTo(7L);
        assertThat(summary.folders().active()).isEqualTo(1234L);
        assertThat(summary.files().active()).isEqualTo(9876L);
        assertThat(summary.files().trashed()).isEqualTo(123L);
        assertThat(summary.audit().last24h()).isEqualTo(456L);
        assertThat(summary.storage().usedBytes()).isEqualTo(1234567890L);
    }

    @Test
    void getSummary_auditCount_usesNowMinus24hAsCutoff() {
        stubAllZero();
        ArgumentCaptor<Timestamp> tsCaptor = ArgumentCaptor.forClass(Timestamp.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), tsCaptor.capture())).thenReturn(0L);

        service.getSummary();

        Instant expectedSince = FIXED_NOW.minusSeconds(24 * 60 * 60);
        assertThat(tsCaptor.getValue().toInstant()).isEqualTo(expectedSince);
    }

    @Test
    void getSummary_storage_returnsZero_whenNoVersions() {
        stubAllZero();

        AdminDashboardSummaryResponse.SummaryData summary = service.getSummary();

        assertThat(summary.storage().usedBytes()).isZero();
    }

    @Test
    void getSummary_auditCount_returnsZero_whenJdbcReturnsNull() {
        stubAllZero();
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(null);

        AdminDashboardSummaryResponse.SummaryData summary = service.getSummary();

        assertThat(summary.audit().last24h()).isZero();
    }

    @Test
    void getSummary_departments_totalEqualsActive() {
        stubAllZero();
        when(departmentRepository.countByDeletedAtIsNull()).thenReturn(15L);

        AdminDashboardSummaryResponse.SummaryData summary = service.getSummary();

        // Department lacks `is_active` column — total === active by definition (envelope shape consistency).
        assertThat(summary.departments().total()).isEqualTo(15L);
        assertThat(summary.departments().active()).isEqualTo(15L);
    }

    // ============================================================
    // P4 — delta tests
    // ============================================================

    @Test
    void getSummary_delta_computesPositiveAndNegative() {
        stubAllZero();
        // users: 80 -> 100 (+25%)
        when(userRepository.countByDeletedAtIsNull()).thenReturn(100L);
        when(userRepository.countAliveAsOf(any(OffsetDateTime.class))).thenReturn(80L);
        // active: 60 -> 50 (-16.67%)
        when(userRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(50L);
        when(userRepository.countActiveAsOf(any(OffsetDateTime.class))).thenReturn(60L);
        // departments: 10 -> 10 (0%)
        when(departmentRepository.countByDeletedAtIsNull()).thenReturn(10L);
        when(departmentRepository.countAliveAsOf(any(OffsetDateTime.class))).thenReturn(10L);
        // folders: 1000 -> 1500 (+50%)
        when(folderRepository.countByDeletedAtIsNull()).thenReturn(1500L);
        when(folderRepository.countAliveAsOf(any(Instant.class))).thenReturn(1000L);
        // files active: 5000 -> 4000 (-20%), trashed: 100 -> 200 (+100%)
        when(fileRepository.countActiveFiles()).thenReturn(4000L);
        when(fileRepository.countActiveFilesAsOf(any(Instant.class))).thenReturn(5000L);
        when(fileRepository.countTrashedFiles()).thenReturn(200L);
        when(fileRepository.countTrashedFilesAsOf(any(Instant.class))).thenReturn(100L);
        // storage: 1_000_000 -> 1_500_000 (+50%)
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(1_500_000L);
        when(fileVersionRepository.sumVersionSizeBytesAsOf(any(Instant.class))).thenReturn(1_000_000L);

        AdminDashboardSummaryResponse.SummaryData s = service.getSummary();

        assertThat(s.users().totalDelta()).isCloseTo(0.25d, within(1e-9));
        assertThat(s.users().activeDelta()).isCloseTo(-1.0 / 6.0, within(1e-9));
        assertThat(s.departments().totalDelta()).isCloseTo(0.0d, within(1e-9));
        assertThat(s.folders().activeDelta()).isCloseTo(0.5d, within(1e-9));
        assertThat(s.files().activeDelta()).isCloseTo(-0.2d, within(1e-9));
        assertThat(s.files().trashedDelta()).isCloseTo(1.0d, within(1e-9));
        assertThat(s.storage().usedBytesDelta()).isCloseTo(0.5d, within(1e-9));
    }

    @Test
    void getSummary_delta_returnsNull_whenBaselineIsZero() {
        stubAllZero();
        // current 100, baseline 0 → null (분모 0)
        when(userRepository.countByDeletedAtIsNull()).thenReturn(100L);
        when(userRepository.countAliveAsOf(any(OffsetDateTime.class))).thenReturn(0L);

        AdminDashboardSummaryResponse.SummaryData s = service.getSummary();

        assertThat(s.users().totalDelta()).isNull();
    }

    @Test
    void getSummary_audit_deltaUsesPrevious24hWindow() {
        stubAllZero();
        // last 24h = 100, prev 24h = 80 → +25%
        ArgumentCaptor<Timestamp> sinceOnlyCaptor = ArgumentCaptor.forClass(Timestamp.class);
        ArgumentCaptor<Timestamp> betweenStartCaptor = ArgumentCaptor.forClass(Timestamp.class);
        ArgumentCaptor<Timestamp> betweenEndCaptor = ArgumentCaptor.forClass(Timestamp.class);

        when(jdbc.queryForObject(anyString(), eq(Long.class), sinceOnlyCaptor.capture()))
            .thenReturn(100L);
        when(jdbc.queryForObject(anyString(), eq(Long.class),
            betweenStartCaptor.capture(), betweenEndCaptor.capture())).thenReturn(80L);

        AdminDashboardSummaryResponse.SummaryData s = service.getSummary();

        assertThat(s.audit().last24h()).isEqualTo(100L);
        assertThat(s.audit().last24hDelta()).isCloseTo(0.25d, within(1e-9));
        // last 24h since = now - 24h
        assertThat(sinceOnlyCaptor.getValue().toInstant())
            .isEqualTo(FIXED_NOW.minusSeconds(24 * 60 * 60));
        // prev 24h window = [now - 48h, now - 24h)
        assertThat(betweenStartCaptor.getValue().toInstant())
            .isEqualTo(FIXED_NOW.minusSeconds(48 * 60 * 60));
        assertThat(betweenEndCaptor.getValue().toInstant())
            .isEqualTo(FIXED_NOW.minusSeconds(24 * 60 * 60));
    }

    @Test
    void getSummary_stockDelta_passesAsOfNowMinus30d() {
        stubAllZero();
        ArgumentCaptor<OffsetDateTime> userAsOf = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<Instant> folderAsOf = ArgumentCaptor.forClass(Instant.class);

        when(userRepository.countAliveAsOf(userAsOf.capture())).thenReturn(0L);
        when(folderRepository.countAliveAsOf(folderAsOf.capture())).thenReturn(0L);

        service.getSummary();

        Instant expected = FIXED_NOW.minusSeconds(30L * 24 * 60 * 60);
        assertThat(userAsOf.getValue().toInstant()).isEqualTo(expected);
        assertThat(folderAsOf.getValue()).isEqualTo(expected);
    }

    @Test
    void computeDelta_helperBehavior() {
        // Pure helper sanity — boundary cases.
        assertThat(AdminDashboardService.computeDelta(100L, 80L)).isCloseTo(0.25d, within(1e-9));
        assertThat(AdminDashboardService.computeDelta(0L, 100L)).isCloseTo(-1.0d, within(1e-9));
        assertThat(AdminDashboardService.computeDelta(50L, 0L)).isNull();
        // Negative baseline (방어적) — null.
        assertThat(AdminDashboardService.computeDelta(50L, -1L)).isNull();
    }
}
