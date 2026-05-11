package com.ibizdrive.trash;

import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 휴지통 보존 정책 read/update 진입점 — trash-retention-mutation Phase B.
 *
 * <p>기존 {@link TrashRetentionProperties} (yml @ConfigurationProperties)는 V17 row 부재 시
 * 첫 INSERT의 default value source로 잔존 (운영자 yml override 이력 보존). 이후 모든 read/update는
 * 본 service만 — {@link com.ibizdrive.file.FileMutationService} / {@link com.ibizdrive.folder.FolderMutationService}
 * 가 {@link #getRetentionDays()} 호출.
 *
 * <p>Mutation은 {@link RetentionPolicyChangedEvent} publish (AFTER_COMMIT 리스너가 audit_log
 * 변환, ADR #24 패턴 동형).
 */
@Service
public class TrashPolicyService {

    private final TrashPolicyRepository repository;
    private final TrashRetentionProperties yml;
    private final ApplicationEventPublisher eventPublisher;

    public TrashPolicyService(TrashPolicyRepository repository,
                              TrashRetentionProperties yml,
                              ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.yml = yml;
        this.eventPublisher = eventPublisher;
    }

    /**
     * V17 row 부재 시 yml default 값으로 idempotent INSERT.
     *
     * <p>{@code @PostConstruct}에서 실행 — 이후 어떤 service 호출보다 먼저 row가 존재함이 보장된다.
     * 다중 instance 환경에서 race가 발생해도 V17의 {@code id = 1} CHECK + PRIMARY KEY 제약이
     * 두 번째 INSERT를 차단한다.
     */
    @PostConstruct
    @Transactional
    public void ensureSingletonRow() {
        if (repository.existsById(TrashPolicy.SINGLETON_ID)) {
            return;
        }
        try {
            repository.save(new TrashPolicy(
                TrashPolicy.SINGLETON_ID,
                yml.days(),
                Instant.now(),
                null
            ));
        } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
            // 다른 instance가 먼저 INSERT — 정상 무시 (idempotency).
        }
    }

    /**
     * 현재 보존 일수 조회. {@code FileMutationService}/{@code FolderMutationService}가 soft-delete
     * 시점에 호출.
     *
     * @throws IllegalStateException V17 row 미존재 (V17 migration 미실행 또는
     *         {@link #ensureSingletonRow}가 아직 실행 안 됨 — 부팅 직후 race).
     */
    @Transactional(readOnly = true)
    public int getRetentionDays() {
        return repository.findById(TrashPolicy.SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException(
                "trash_policy row(id=1) not found — V17 migration or ensureSingletonRow missing"))
            .getRetentionDays();
    }

    /**
     * 보존 일수 변경. 7..90 범위 검증 후 row UPDATE + audit event publish.
     *
     * <p>변경은 {@link RetentionPolicyChangedEvent} 발행만으로 외부에 알린다 — 기존 trash row의
     * {@code purge_after}는 재계산 안 함 (일수 감소 시 hard purge 폭증 회피, docs/04 §15.4).
     *
     * @param newDays 새 보존 일수 (7..90)
     * @param actorId 변경 actor (`@PreAuthorize` 통과한 ADMIN principal)
     * @throws IllegalArgumentException newDays가 7..90 범위 밖 — controller에서 400으로 매핑
     * @throws IllegalStateException row 미존재 ({@link #getRetentionDays} 동형)
     */
    @Transactional
    public int updateRetentionDays(int newDays, UUID actorId) {
        if (newDays < 7 || newDays > 90) {
            throw new IllegalArgumentException("retention days must be between 7 and 90, got: " + newDays);
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }
        TrashPolicy row = repository.findById(TrashPolicy.SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException(
                "trash_policy row(id=1) not found"));
        int beforeDays = row.getRetentionDays();
        if (beforeDays == newDays) {
            return beforeDays; // no-op, audit emit 생략
        }
        row.setRetentionDays(newDays);
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(actorId);
        TrashPolicy saved = repository.save(row);

        eventPublisher.publishEvent(new RetentionPolicyChangedEvent(beforeDays, newDays, actorId));
        return saved.getRetentionDays();
    }
}
