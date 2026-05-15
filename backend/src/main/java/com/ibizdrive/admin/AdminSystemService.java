package com.ibizdrive.admin;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.util.UUID;

/**
 * `/admin/system` mutation 도메인 service — 현재는 cron toggle 단일 액션.
 *
 * <p>{@link AdminSystemController}가 사용. 트랜잭션 commit 후 {@link AdminCronToggledListener}가
 * audit_log row를 기록한다.
 */
@Service
public class AdminSystemService {

    private final CronPolicyRepository cronPolicyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminSystemService(
        CronPolicyRepository cronPolicyRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.cronPolicyRepository = cronPolicyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 6종 cron 중 하나의 enabled 값을 갱신한다. 같은 값으로의 no-op 토글도 audit row를 남긴다.
     *
     * @throws IllegalArgumentException unknown key (글로벌 핸들러가 400 BAD_REQUEST로 변환)
     */
    @Transactional
    public void toggleCron(
        String key, boolean requested, UUID actorId,
        InetAddress actorIp, String userAgent
    ) {
        CronPolicy policy = cronPolicyRepository.findById(key)
            .orElseThrow(() -> new IllegalArgumentException(
                "unknown cron key: " + key));
        boolean before = policy.isEnabled();
        policy.update(requested, actorId);
        cronPolicyRepository.save(policy);
        eventPublisher.publishEvent(new AdminCronToggledEvent(
            actorId, actorIp, userAgent, key, before, requested
        ));
    }
}
