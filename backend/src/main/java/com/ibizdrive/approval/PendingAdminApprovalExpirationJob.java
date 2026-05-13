package com.ibizdrive.approval;

import com.ibizdrive.admin.CronPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * ADMIN_APPROVAL_EXPIRED cron — {@code pending_admin_approvals.expires_at <= NOW() AND
 * status='REQUESTED'} row를 자동 만료(ADR #47 Phase 3d, docs/04 §16).
 *
 * <p>매 cron 실행마다 {@link PendingApprovalService#findExpired}로 batch-size 한도까지 후보를
 * 스캔 → 각 id에 대해 {@link PendingApprovalService#expire} 호출 (개별 트랜잭션). 한 row 실패는
 * ERROR 로그 후 다음 row 진행 — race(secondary admin 동시 결정)는
 * {@link AlreadyDecidedException}으로 보호되어 정합 안전.
 *
 * <p><b>활성화</b>: {@link CronPolicyRepository#isEnabled} (DB 단일 row lookup, key=
 * {@code admin.approval.expire}) — 매 tick guard. yml의 {@code app.admin-approval.expiration.*}는
 * schedule/zone/batchSize만, enabled는 V21 시드(false) 후 admin UI 토글이 진실의 출처.
 *
 * <p><b>다중 인스턴스 안전성</b>: V20 {@code pending_admin_approvals} row-level pessimistic lock
 * ({@link PendingAdminApprovalRepository#lockById})이 두 인스턴스의 동시 {@code expire(sameId)}
 * 호출을 직렬화 — 한 쪽만 통과, 다른 쪽은 status≠REQUESTED로 AlreadyDecided. 분산락 별도 도입 불요.
 */
@Component
public class PendingAdminApprovalExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(PendingAdminApprovalExpirationJob.class);

    /** {@link CronPolicyRepository} PK — V21 시드와 동형. */
    static final String CRON_KEY = "admin.approval.expire";

    private final PendingApprovalService approvalService;
    private final PendingAdminApprovalExpirationProperties props;
    private final CronPolicyRepository cronPolicyRepository;

    public PendingAdminApprovalExpirationJob(PendingApprovalService approvalService,
                                             PendingAdminApprovalExpirationProperties props,
                                             CronPolicyRepository cronPolicyRepository) {
        this.approvalService = approvalService;
        this.props = props;
        this.cronPolicyRepository = cronPolicyRepository;
    }

    @Scheduled(cron = "${app.admin-approval.expiration.cron}", zone = "${app.admin-approval.expiration.zone}")
    public void run() {
        if (!cronPolicyRepository.isEnabled(CRON_KEY)) {
            log.debug("cron {} disabled, skipping tick", CRON_KEY);
            return;
        }
        List<PendingAdminApproval> candidates;
        try {
            candidates = approvalService.findExpired(props.batchSize());
        } catch (RuntimeException e) {
            log.error("admin approval expiration scan failed — will retry on next schedule", e);
            return;
        }
        if (candidates.isEmpty()) return;

        int ok = 0;
        int failed = 0;
        for (PendingAdminApproval row : candidates) {
            UUID id = row.getId();
            try {
                approvalService.expire(id);
                ok++;
            } catch (AlreadyDecidedException e) {
                // race-safe: secondary admin 동시 결정으로 이미 terminal status — 정상 case.
                log.debug("admin approval already decided approvalId={} (race-safe skip)", id);
            } catch (RuntimeException e) {
                failed++;
                log.error("admin approval expire failed approvalId={} (continuing)", id, e);
            }
        }
        if (failed > 0) {
            log.warn("admin approval expiration run summary — total={} ok={} failed={}",
                candidates.size(), ok, failed);
        } else {
            log.info("admin approval expiration run summary — total={} ok={}",
                candidates.size(), ok);
        }
    }
}
