package com.ibizdrive.approval;

import com.ibizdrive.admin.CronPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ADMIN_APPROVAL_EXPIRED cron 단위 테스트 (ADR #47 Phase 3d).
 *
 * <p>Service mock — cron 트리거 정합성, batch-size 전달, per-row 실패 격리, scan 실패 swallow,
 * cron_policy disabled 조기 종료, race-safe AlreadyDecided silent skip 검증. Spring 컨텍스트 미사용.
 */
class PendingAdminApprovalExpirationJobTest {

    private PendingApprovalService approvalService;
    private CronPolicyRepository cronPolicyRepository;
    private PendingAdminApprovalExpirationJob job;

    @BeforeEach
    void setUp() {
        approvalService = mock(PendingApprovalService.class);
        cronPolicyRepository = mock(CronPolicyRepository.class);
        when(cronPolicyRepository.isEnabled("admin.approval.expire")).thenReturn(true);
        PendingAdminApprovalExpirationProperties props =
            new PendingAdminApprovalExpirationProperties(200, null, null);
        job = new PendingAdminApprovalExpirationJob(approvalService, props, cronPolicyRepository);
    }

    private PendingAdminApproval row(UUID id) {
        PendingAdminApproval row = new PendingAdminApproval();
        row.setId(id);
        return row;
    }

    @Test
    void run_disabled_doesNotScan() {
        when(cronPolicyRepository.isEnabled("admin.approval.expire")).thenReturn(false);

        job.run();

        verifyNoInteractions(approvalService);
    }

    @Test
    void run_emptyCandidates_doesNotInvokeExpire() {
        when(approvalService.findExpired(200)).thenReturn(List.of());

        job.run();

        verify(approvalService).findExpired(200);
        verify(approvalService, never()).expire(any(UUID.class));
    }

    @Test
    void run_invokesExpireForEachCandidate() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(approvalService.findExpired(200)).thenReturn(List.of(row(id1), row(id2), row(id3)));

        job.run();

        verify(approvalService).expire(id1);
        verify(approvalService).expire(id2);
        verify(approvalService).expire(id3);
        verify(approvalService, times(3)).expire(any(UUID.class));
    }

    @Test
    void run_continuesAfterPerRowFailure() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(approvalService.findExpired(200)).thenReturn(List.of(row(id1), row(id2), row(id3)));
        doThrow(new RuntimeException("simulated failure")).when(approvalService).expire(id2);

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(approvalService).expire(id1);
        verify(approvalService).expire(id2);
        verify(approvalService).expire(id3);
    }

    @Test
    void run_swallowsRaceWithSecondaryDecision() {
        // secondary admin이 cron tick과 동시에 approve/reject한 경우 — AlreadyDecidedException은
        // 정상 race로 처리해 배치 종료 안 함.
        UUID id = UUID.randomUUID();
        when(approvalService.findExpired(200)).thenReturn(List.of(row(id)));
        doThrow(new AlreadyDecidedException(
            "approval already decided: " + id + " status=APPROVED",
            PendingApprovalStatus.APPROVED))
            .when(approvalService).expire(id);

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(approvalService).expire(id);
    }

    @Test
    void run_swallowsScanFailure() {
        when(approvalService.findExpired(200)).thenThrow(new RuntimeException("db down"));

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(approvalService).findExpired(200);
        verify(approvalService, never()).expire(any(UUID.class));
    }

    @Test
    void run_passesBatchSizeFromProperties() {
        // batchSize=50으로 새 job 생성해 findExpired 한도가 properties와 일치하는지 검증.
        PendingAdminApprovalExpirationProperties props =
            new PendingAdminApprovalExpirationProperties(50, null, null);
        PendingAdminApprovalExpirationJob customJob =
            new PendingAdminApprovalExpirationJob(approvalService, props, cronPolicyRepository);
        when(approvalService.findExpired(50)).thenReturn(List.of());

        customJob.run();

        verify(approvalService).findExpired(eq(50));
    }
}
