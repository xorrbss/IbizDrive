package com.ibizdrive.share;

import com.ibizdrive.admin.CronPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SHARE_EXPIRED cron — {@code shares.expires_at <= NOW() AND revoked_at IS NULL} row를 자동 만료
 * (ADR #34 backlog closure, docs/04 §13).
 *
 * <p>매 cron 실행마다 {@link ShareRepository#findExpiredActiveIds}로 batch-size 한도까지 후보를
 * 스캔 → 각 id에 대해 {@link ShareCommandService#expireShare} 호출 (개별 트랜잭션). 한 row 실패는
 * ERROR 로그 후 다음 row 진행 — race(사용자 동시 revoke)는 {@link com.ibizdrive.common.error.ResourceNotFoundException}
 * 으로 보호되어 정합 안전.
 *
 * <p><b>활성화</b>: {@link CronPolicyRepository#isEnabled} (DB 단일 row lookup) — 본 잡은 매 tick
 * DB 조회 후 비활성이면 즉시 return. yml의 {@code app.share.expiration.enabled}는 시드 후 효력 없음
 * (admin-cron-policy-toggle 트랙, V11 이후).
 *
 * <p><b>다중 인스턴스 안전성</b>: V6 {@code shares} row-level pessimistic lock이 두 인스턴스의 동시
 * {@code expireShare(sameId)} 호출을 직렬화 — 한 쪽만 통과, 다른 쪽은 이미 revoked 상태로 lock query
 * miss → 404 swallow. 분산락 별도 도입 불요.
 */
@Component
public class ShareExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(ShareExpirationJob.class);

    private final ShareCommandService shareCommandService;
    private final ShareRepository shareRepository;
    private final ShareExpirationProperties props;
    private final CronPolicyRepository cronPolicyRepository;

    public ShareExpirationJob(ShareCommandService shareCommandService,
                              ShareRepository shareRepository,
                              ShareExpirationProperties props,
                              CronPolicyRepository cronPolicyRepository) {
        this.shareCommandService = shareCommandService;
        this.shareRepository = shareRepository;
        this.props = props;
        this.cronPolicyRepository = cronPolicyRepository;
    }

    @Scheduled(cron = "${app.share.expiration.cron}", zone = "${app.share.expiration.zone}")
    public void run() {
        if (!cronPolicyRepository.isEnabled("share.expire")) {
            log.debug("cron share.expire disabled, skipping tick");
            return;
        }
        List<UUID> ids;
        try {
            ids = shareRepository.findExpiredActiveIds(
                Instant.now(),
                PageRequest.of(0, props.batchSize())
            );
        } catch (RuntimeException e) {
            log.error("share expiration scan failed — will retry on next schedule", e);
            return;
        }
        if (ids.isEmpty()) return;

        int ok = 0;
        int failed = 0;
        for (UUID id : ids) {
            try {
                shareCommandService.expireShare(id);
                ok++;
            } catch (RuntimeException e) {
                // race-safe: ResourceNotFoundException은 사용자 직접 revoke과 충돌한 정상 case로 간주.
                // 그 외 예외도 다음 row로 진행 — 단일 row 실패가 배치 전체를 막지 않음.
                failed++;
                log.error("share expire failed shareId={} (continuing)", id, e);
            }
        }
        if (failed > 0) {
            log.warn("share expiration run summary — total={} ok={} failed={}", ids.size(), ok, failed);
        } else {
            log.info("share expiration run summary — total={} ok={}", ids.size(), ok);
        }
    }
}
