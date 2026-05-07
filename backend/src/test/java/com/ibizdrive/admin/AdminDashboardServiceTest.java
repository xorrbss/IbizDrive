package com.ibizdrive.admin;

import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * admin-dashboard P1 — {@link AdminDashboardService} 단위 테스트.
 *
 * <p>repositories + JdbcTemplate(audit) mock. {@link AdminDepartmentServiceTest} mockito 패턴 1:1 답습.
 * Repository derived/native count 메서드 + {@code FileVersionRepository.sumAllVersionSizeBytes} + JdbcTemplate
 * audit count는 통합 검증을 별도 IT(skip-without-docker)에서 다룬다.
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

    @Test
    void getSummary_aggregatesAllCounts() {
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
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(0L);
        when(departmentRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(folderRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);
        org.mockito.ArgumentCaptor<Timestamp> tsCaptor = org.mockito.ArgumentCaptor.forClass(Timestamp.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), tsCaptor.capture())).thenReturn(0L);

        service.getSummary();

        Instant expectedSince = FIXED_NOW.minusSeconds(24 * 60 * 60);
        assertThat(tsCaptor.getValue().toInstant()).isEqualTo(expectedSince);
    }

    @Test
    void getSummary_storage_returnsZero_whenNoVersions() {
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(0L);
        when(departmentRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(folderRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(0L);

        AdminDashboardSummaryResponse.SummaryData summary = service.getSummary();

        assertThat(summary.storage().usedBytes()).isZero();
    }

    @Test
    void getSummary_auditCount_returnsZero_whenJdbcReturnsNull() {
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(0L);
        when(departmentRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(folderRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(null);

        AdminDashboardSummaryResponse.SummaryData summary = service.getSummary();

        assertThat(summary.audit().last24h()).isZero();
    }

    @Test
    void getSummary_departments_totalEqualsActive() {
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(0L);
        when(departmentRepository.countByDeletedAtIsNull()).thenReturn(15L);
        when(folderRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(0L);

        AdminDashboardSummaryResponse.SummaryData summary = service.getSummary();

        // Department lacks `is_active` column — total === active by definition (envelope shape consistency).
        assertThat(summary.departments().total()).isEqualTo(15L);
        assertThat(summary.departments().active()).isEqualTo(15L);
    }
}
