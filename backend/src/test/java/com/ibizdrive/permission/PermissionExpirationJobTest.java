package com.ibizdrive.permission;

import com.ibizdrive.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code permissions-expired-cron} 단위 테스트 — {@link com.ibizdrive.share.ShareExpirationJob} 동형.
 *
 * <p>Repo/Service 모두 mock — cron 트리거 정합성, batch-size 전달, per-row 실패 격리, scan 실패 swallow까지
 * 검증. Spring 컨텍스트 미사용 (KISS).
 */
class PermissionExpirationJobTest {

    private PermissionService permissionService;
    private PermissionRepository permissionRepository;
    private PermissionExpirationJob job;

    @BeforeEach
    void setUp() {
        permissionService = mock(PermissionService.class);
        permissionRepository = mock(PermissionRepository.class);
        // batchSize=200 (default); enabled/cron/zone은 빈 등록 게이트라 unit test에서는 무관.
        PermissionExpirationProperties props = new PermissionExpirationProperties(true, 200, null, null);
        job = new PermissionExpirationJob(permissionService, permissionRepository, props);
    }

    @Test
    void run_emptyCandidates_doesNotInvokeExpire() {
        when(permissionRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        job.run();

        verify(permissionRepository).findExpiredActiveIds(any(Instant.class), any(Pageable.class));
        verifyNoInteractions(permissionService);
    }

    @Test
    void run_invokesExpireForEachCandidate() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(permissionRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(id1, id2, id3));

        job.run();

        verify(permissionService).expirePermission(id1);
        verify(permissionService).expirePermission(id2);
        verify(permissionService).expirePermission(id3);
        verify(permissionService, times(3)).expirePermission(any(UUID.class));
    }

    @Test
    void run_continuesAfterPerRowFailure() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(permissionRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(id1, id2, id3));
        // 가운데 row만 실패 — 첫째/셋째는 정상 진행되어야 함.
        doThrow(new RuntimeException("simulated failure")).when(permissionService).expirePermission(id2);

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(permissionService).expirePermission(id1);
        verify(permissionService).expirePermission(id2);
        verify(permissionService).expirePermission(id3);
    }

    @Test
    void run_swallowsRaceWithUserRevoke() {
        UUID id = UUID.randomUUID();
        when(permissionRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(id));
        // ResourceNotFoundException — 다른 인스턴스/사용자가 동시에 revoke한 race case로 정상 처리.
        doThrow(new ResourceNotFoundException("permission not found: " + id))
            .when(permissionService).expirePermission(id);

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(permissionService).expirePermission(id);
    }

    @Test
    void run_swallowsScanFailure() {
        when(permissionRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenThrow(new RuntimeException("db down"));

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(permissionRepository).findExpiredActiveIds(any(Instant.class), any(Pageable.class));
        // scan 실패 시 expirePermission는 한 번도 호출되지 않아야 함.
        verify(permissionService, never()).expirePermission(any(UUID.class));
    }

    @Test
    void run_passesBatchSizeToRepository() {
        // batchSize=50으로 새 job 생성해 PageRequest 한도가 properties와 일치하는지 검증.
        PermissionExpirationProperties props = new PermissionExpirationProperties(true, 50, null, null);
        PermissionExpirationJob customJob =
            new PermissionExpirationJob(permissionService, permissionRepository, props);
        when(permissionRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        customJob.run();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(permissionRepository).findExpiredActiveIds(any(Instant.class), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageSize()).isEqualTo(50);
        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured).isEqualTo(PageRequest.of(0, 50));
    }
}
