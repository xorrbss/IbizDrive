package com.ibizdrive.admin;

import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import com.ibizdrive.user.UserStorageQuotaChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 사용자 quota read/update 진입점 — quota mutation Phase 3 (`docs/04 §6.1`).
 *
 * <p>{@link com.ibizdrive.trash.TrashPolicyService} 패턴 답습:
 * <ul>
 *   <li>@Transactional update + idempotent no-op 분기 (audit 폭증 회피)</li>
 *   <li>AFTER_COMMIT event publish — {@link com.ibizdrive.audit.UserQuotaAuditListener}가
 *       audit_log row 변환</li>
 *   <li>actor null 가드 — controller {@code @PreAuthorize}가 ADMIN principal 보장하지만 service
 *       단에서 재검증 (CLAUDE.md §3 원칙 10)</li>
 * </ul>
 *
 * <p>{@code storage_used}는 본 service에서 변경하지 않는다 — Phase 5 enforcement에서 upload
 * commit 시점만 증가 + FOR UPDATE 트랜잭션 (`docs/04 §6.1` Phase 5).
 */
@Service
public class AdminUserQuotaService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminUserQuotaService(UserRepository userRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 사용자 quota 조회.
     *
     * @throws AdminUserNotFoundException user 부재 또는 soft-deleted (`deleted_at IS NOT NULL`).
     *         admin 화면은 비활성(`is_active=false`) 사용자도 quota 조회 가능 — soft-delete만 차단.
     */
    @Transactional(readOnly = true)
    public AdminUserQuotaDto getQuota(UUID userId) {
        User user = userRepository.findById(userId)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new AdminUserNotFoundException(userId.toString()));
        return new AdminUserQuotaDto(user.getStorageQuota(), user.getStorageUsed());
    }

    /**
     * 사용자 quota 변경 (admin only). {@code newQuota >= 0} 가드 후 row UPDATE + audit event publish.
     *
     * <p>idempotent: 동일 값 입력은 no-op (audit emit 생략, 응답은 동일).
     *
     * <p>한도 감소로 over-quota가 되는 케이스(`newQuota < user.storageUsed`)는 허용 — Phase 5
     * enforcement에서 신규 업로드만 차단, 기존 사용량은 그대로 보존.
     *
     * @throws IllegalArgumentException newQuota가 음수 (controller 400 매핑)
     * @throws AdminUserNotFoundException user 부재 또는 soft-deleted (controller 404 매핑)
     */
    @Transactional
    public AdminUserQuotaDto updateQuota(UUID userId, long newQuota, UUID actorId) {
        if (newQuota < 0) {
            throw new IllegalArgumentException("storageQuota must be >= 0, got: " + newQuota);
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }
        User user = userRepository.findById(userId)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new AdminUserNotFoundException(userId.toString()));

        long beforeQuota = user.getStorageQuota();
        if (beforeQuota == newQuota) {
            return new AdminUserQuotaDto(beforeQuota, user.getStorageUsed()); // no-op, audit emit 생략
        }

        user.changeStorageQuota(newQuota);
        User saved = userRepository.save(user);

        eventPublisher.publishEvent(
            new UserStorageQuotaChangedEvent(userId, beforeQuota, newQuota, actorId));

        return new AdminUserQuotaDto(saved.getStorageQuota(), saved.getStorageUsed());
    }
}
