package com.ibizdrive.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code permissions-expired-cron} — {@code permissions.expires_at <= NOW()} grant row를 자동 cleanup +
 * {@code permission.expired} audit 기록 (docs/04 §13, {@link com.ibizdrive.share.ShareExpirationJob} 동형).
 *
 * <p>매 cron 실행마다 {@link PermissionRepository#findExpiredActiveIds}로 batch-size 한도까지 후보를
 * 스캔 → 각 id에 대해 {@link PermissionService#expirePermission} 호출 (개별 트랜잭션). 한 row 실패는
 * ERROR 로그 후 다음 row 진행 — race(다른 인스턴스/사용자 동시 revoke)는
 * {@link com.ibizdrive.common.error.ResourceNotFoundException}으로 보호되어 정합 안전.
 *
 * <p><b>활성화</b>: {@code app.permission.expiration.enabled=true}일 때만 빈 등록 —
 * {@link PermissionExpirationProperties}와 본 클래스 모두 동일 조건 (이중 가드, ShareExpirationJob 동형).
 *
 * <p><b>다중 인스턴스 안전성</b>: V5 {@code permissions} row-level pessimistic lock이 두 인스턴스의 동시
 * {@code expirePermission(sameId)} 호출을 직렬화 — 한 쪽만 통과, 다른 쪽은 row 부재 → 404 swallow.
 * 분산락 별도 도입 불요.
 *
 * <p><b>현재 평가와의 관계</b>: {@link PermissionRepository#findEffective}는 이미
 * {@code expires_at > NOW()} 필터로 만료 grant를 평가에서 제외하므로 본 cron이 비활성이어도 보안 정합은
 * 유지된다. 본 cron의 가치는 (1) DB cleanup (장기 누적 row 제거) + (2) audit trail
 * ({@code permission.expired} 기록 유지, ADR #24 append-only 정합).
 */
@Component
@ConditionalOnProperty(name = "app.permission.expiration.enabled", havingValue = "true")
public class PermissionExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(PermissionExpirationJob.class);

    private final PermissionService permissionService;
    private final PermissionRepository permissionRepository;
    private final PermissionExpirationProperties props;

    public PermissionExpirationJob(PermissionService permissionService,
                                    PermissionRepository permissionRepository,
                                    PermissionExpirationProperties props) {
        this.permissionService = permissionService;
        this.permissionRepository = permissionRepository;
        this.props = props;
    }

    @Scheduled(cron = "${app.permission.expiration.cron}", zone = "${app.permission.expiration.zone}")
    public void run() {
        List<UUID> ids;
        try {
            ids = permissionRepository.findExpiredActiveIds(
                Instant.now(),
                PageRequest.of(0, props.batchSize())
            );
        } catch (RuntimeException e) {
            log.error("permission expiration scan failed — will retry on next schedule", e);
            return;
        }
        if (ids.isEmpty()) return;

        int ok = 0;
        int failed = 0;
        for (UUID id : ids) {
            try {
                permissionService.expirePermission(id);
                ok++;
            } catch (RuntimeException e) {
                // race-safe: ResourceNotFoundException은 다른 인스턴스/사용자 직접 revoke 충돌 정상 case.
                // 그 외 예외도 다음 row로 진행 — 단일 row 실패가 배치 전체를 막지 않음.
                failed++;
                log.error("permission expire failed permissionId={} (continuing)", id, e);
            }
        }
        if (failed > 0) {
            log.warn("permission expiration run summary — total={} ok={} failed={}", ids.size(), ok, failed);
        } else {
            log.info("permission expiration run summary — total={} ok={}", ids.size(), ok);
        }
    }
}
