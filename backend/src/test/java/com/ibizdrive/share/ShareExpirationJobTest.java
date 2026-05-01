package com.ibizdrive.share;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * SHARE_EXPIRED cron 단위 테스트 (ADR #34 backlog closure).
 *
 * <p>Repo/Service 모두 mock — cron 트리거 정합성, batch-size 전달, per-row 실패 격리, scan 실패 swallow까지
 * 검증. Spring 컨텍스트 미사용 (KISS).
 */
class ShareExpirationJobTest {

    private ShareCommandService shareCommandService;
    private ShareRepository shareRepository;
    private ShareExpirationJob job;

    @BeforeEach
    void setUp() {
        shareCommandService = mock(ShareCommandService.class);
        shareRepository = mock(ShareRepository.class);
        // batchSize=200 (default); enabled/cron/zone은 빈 등록 게이트라 unit test에서는 무관.
        ShareExpirationProperties props = new ShareExpirationProperties(true, 200, null, null);
        job = new ShareExpirationJob(shareCommandService, shareRepository, props);
    }

    @Test
    void run_emptyCandidates_doesNotInvokeExpire() {
        when(shareRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        job.run();

        verify(shareRepository).findExpiredActiveIds(any(Instant.class), any(Pageable.class));
        verifyNoInteractions(shareCommandService);
    }

    @Test
    void run_invokesExpireForEachCandidate() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(shareRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(id1, id2, id3));

        job.run();

        verify(shareCommandService).expireShare(id1);
        verify(shareCommandService).expireShare(id2);
        verify(shareCommandService).expireShare(id3);
        verify(shareCommandService, times(3)).expireShare(any(UUID.class));
    }

    @Test
    void run_continuesAfterPerRowFailure() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(shareRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(id1, id2, id3));
        // 가운데 row만 실패 — 첫째/셋째는 정상 진행되어야 함.
        doThrow(new RuntimeException("simulated failure")).when(shareCommandService).expireShare(id2);

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(shareCommandService).expireShare(id1);
        verify(shareCommandService).expireShare(id2);
        verify(shareCommandService).expireShare(id3);
    }

    @Test
    void run_swallowsRaceWithUserRevoke() {
        UUID id = UUID.randomUUID();
        when(shareRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(id));
        // ResourceNotFoundException — 사용자가 동시에 revoke한 race case로 정상 처리.
        doThrow(new ResourceNotFoundException("share not found: " + id))
            .when(shareCommandService).expireShare(id);

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(shareCommandService).expireShare(id);
    }

    @Test
    void run_swallowsScanFailure() {
        when(shareRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenThrow(new RuntimeException("db down"));

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(shareRepository).findExpiredActiveIds(any(Instant.class), any(Pageable.class));
        // scan 실패 시 expireShare는 한 번도 호출되지 않아야 함.
        verify(shareCommandService, never()).expireShare(any(UUID.class));
    }

    @Test
    void run_passesBatchSizeToRepository() {
        // batchSize=50으로 새 job 생성해 PageRequest 한도가 properties와 일치하는지 검증.
        ShareExpirationProperties props = new ShareExpirationProperties(true, 50, null, null);
        ShareExpirationJob customJob = new ShareExpirationJob(shareCommandService, shareRepository, props);
        when(shareRepository.findExpiredActiveIds(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        customJob.run();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(shareRepository).findExpiredActiveIds(any(Instant.class), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageSize()).isEqualTo(50);
        assertThat(captured.getPageNumber()).isEqualTo(0);
        // PageRequest.of(0, 50)와 정확히 일치하는지도 한 번 더 확인 (regression guard).
        assertThat(captured).isEqualTo(PageRequest.of(0, 50));
    }
}
