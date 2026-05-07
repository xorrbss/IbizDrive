package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AdminStorageService} 단위 테스트 — admin-storage-overview.
 *
 * <p>Repository/JdbcTemplate은 mock으로 격리. RowMapper 자체의 매핑 로직은
 * {@link #rowMapper_extractsOccurredAtAndDeletedCount()}에서 직접 mock {@link ResultSet}로 검증.
 */
class AdminStorageServiceTest {

    private FileRepository fileRepository;
    private FileVersionRepository fileVersionRepository;
    private JdbcTemplate jdbc;
    private AdminStorageService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        fileVersionRepository = mock(FileVersionRepository.class);
        jdbc = mock(JdbcTemplate.class);
        service = new AdminStorageService(fileRepository, fileVersionRepository, jdbc, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private void stubOrphanLookup(List<AdminStorageOverviewResponse.OrphanCleanupSummary> rows) {
        when(jdbc.query(
            eq(AdminStorageService.LAST_ORPHAN_CLEANUP_SQL),
            any(RowMapper.class),
            eq(AdminStorageService.STORAGE_ORPHAN_CLEANED_EVENT)
        )).thenReturn((List) rows);
    }

    // -------- loadOverview --------

    @Test
    void loadOverview_emptyDb_returnsAllZerosAndNullOrphanCleanup() {
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileRepository.sumTrashedSizeBytes()).thenReturn(0L);
        when(fileVersionRepository.countAllVersions()).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);
        stubOrphanLookup(List.of());

        AdminStorageOverviewResponse.Overview result = service.loadOverview();

        assertThat(result.totalFiles()).isZero();
        assertThat(result.trashedFiles()).isZero();
        assertThat(result.totalVersions()).isZero();
        assertThat(result.totalBytes()).isZero();
        assertThat(result.trashedBytes()).isZero();
        assertThat(result.orphanCleanup()).isNull();
    }

    @Test
    void loadOverview_aggregatesAllFiveCountsAndOrphanCleanup() {
        when(fileRepository.countActiveFiles()).thenReturn(123L);
        when(fileRepository.countTrashedFiles()).thenReturn(5L);
        when(fileRepository.sumTrashedSizeBytes()).thenReturn(2_048L);
        when(fileVersionRepository.countAllVersions()).thenReturn(200L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(10_485_760L);
        Instant lastRun = Instant.parse("2026-05-06T14:30:00Z");
        stubOrphanLookup(List.of(
            new AdminStorageOverviewResponse.OrphanCleanupSummary(lastRun, 7)
        ));

        AdminStorageOverviewResponse.Overview result = service.loadOverview();

        assertThat(result.totalFiles()).isEqualTo(123L);
        assertThat(result.trashedFiles()).isEqualTo(5L);
        assertThat(result.totalVersions()).isEqualTo(200L);
        assertThat(result.totalBytes()).isEqualTo(10_485_760L);
        assertThat(result.trashedBytes()).isEqualTo(2_048L);
        assertThat(result.orphanCleanup()).isNotNull();
        assertThat(result.orphanCleanup().lastRunAt()).isEqualTo(lastRun);
        assertThat(result.orphanCleanup().lastDeletedCount()).isEqualTo(7);
    }

    @Test
    void loadOverview_orphanLookupReturnsEmpty_orphanCleanupIsNull() {
        when(fileRepository.countActiveFiles()).thenReturn(10L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileRepository.sumTrashedSizeBytes()).thenReturn(0L);
        when(fileVersionRepository.countAllVersions()).thenReturn(10L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(1024L);
        stubOrphanLookup(List.of());

        AdminStorageOverviewResponse.Overview result = service.loadOverview();

        assertThat(result.orphanCleanup()).isNull();
    }

    // -------- audit RowMapper 직접 검증 --------

    @Test
    @SuppressWarnings("unchecked")
    void rowMapper_extractsOccurredAtAndDeletedCount() throws Exception {
        // 캡처된 RowMapper에 mock ResultSet을 흘려 실제 매핑 동작을 검증.
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileRepository.sumTrashedSizeBytes()).thenReturn(0L);
        when(fileVersionRepository.countAllVersions()).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);

        org.mockito.ArgumentCaptor<RowMapper<?>> mapperCap =
            org.mockito.ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(
            eq(AdminStorageService.LAST_ORPHAN_CLEANUP_SQL),
            mapperCap.capture(),
            eq(AdminStorageService.STORAGE_ORPHAN_CLEANED_EVENT)
        )).thenReturn(List.of());

        service.loadOverview();
        RowMapper<AdminStorageOverviewResponse.OrphanCleanupSummary> mapper =
            (RowMapper<AdminStorageOverviewResponse.OrphanCleanupSummary>) mapperCap.getValue();

        // 캐싱된 RowMapper에 mock ResultSet으로 매핑 검증.
        ResultSet rs = mock(ResultSet.class);
        Instant ts = Instant.parse("2026-05-06T14:30:00Z");
        when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(ts));
        when(rs.getString("after_state")).thenReturn(
            "{\"runId\":\"abc\",\"scanned\":100,\"candidates\":5,\"deleted\":3," +
            "\"failed\":0,\"truncated\":false,\"durationMs\":1234}");

        AdminStorageOverviewResponse.OrphanCleanupSummary summary = mapper.mapRow(rs, 0);
        assertThat(summary.lastRunAt()).isEqualTo(ts);
        assertThat(summary.lastDeletedCount()).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rowMapper_missingDeletedField_returnsZero() throws Exception {
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileRepository.sumTrashedSizeBytes()).thenReturn(0L);
        when(fileVersionRepository.countAllVersions()).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);

        org.mockito.ArgumentCaptor<RowMapper<?>> mapperCap =
            org.mockito.ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(
            eq(AdminStorageService.LAST_ORPHAN_CLEANUP_SQL),
            mapperCap.capture(),
            eq(AdminStorageService.STORAGE_ORPHAN_CLEANED_EVENT)
        )).thenReturn(List.of());
        service.loadOverview();
        RowMapper<AdminStorageOverviewResponse.OrphanCleanupSummary> mapper =
            (RowMapper<AdminStorageOverviewResponse.OrphanCleanupSummary>) mapperCap.getValue();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.EPOCH));
        when(rs.getString("after_state")).thenReturn("{}");

        AdminStorageOverviewResponse.OrphanCleanupSummary summary = mapper.mapRow(rs, 0);
        assertThat(summary.lastDeletedCount()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rowMapper_malformedJson_returnsZeroAndDoesNotThrow() throws Exception {
        when(fileRepository.countActiveFiles()).thenReturn(0L);
        when(fileRepository.countTrashedFiles()).thenReturn(0L);
        when(fileRepository.sumTrashedSizeBytes()).thenReturn(0L);
        when(fileVersionRepository.countAllVersions()).thenReturn(0L);
        when(fileVersionRepository.sumAllVersionSizeBytes()).thenReturn(0L);

        org.mockito.ArgumentCaptor<RowMapper<?>> mapperCap =
            org.mockito.ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(
            eq(AdminStorageService.LAST_ORPHAN_CLEANUP_SQL),
            mapperCap.capture(),
            eq(AdminStorageService.STORAGE_ORPHAN_CLEANED_EVENT)
        )).thenReturn(List.of());
        service.loadOverview();
        RowMapper<AdminStorageOverviewResponse.OrphanCleanupSummary> mapper =
            (RowMapper<AdminStorageOverviewResponse.OrphanCleanupSummary>) mapperCap.getValue();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.EPOCH));
        when(rs.getString("after_state")).thenReturn("not json");

        AdminStorageOverviewResponse.OrphanCleanupSummary summary = mapper.mapRow(rs, 0);
        assertThat(summary.lastDeletedCount()).isZero();
    }
}
