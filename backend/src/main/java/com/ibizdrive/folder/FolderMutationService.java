package com.ibizdrive.folder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.common.normalize.NormalizeUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Folder mutation 도메인 서비스 — A4.6 (docs/02 §2.3, §6).
 *
 * <p>책임:
 * <ul>
 *   <li>{@link #create} — 새 폴더 INSERT (root 또는 nested)</li>
 *   <li>{@link #rename} — 활성 폴더 이름 변경 (동일 부모 내)</li>
 *   <li>{@link #move} — 활성 폴더 부모 변경 (cycle 방지)</li>
 * </ul>
 *
 * <p><b>트랜잭션 + lock 정책 (CLAUDE.md §3 원칙 7)</b>: 모든 mutation은 클래스 레벨
 * {@link Transactional}로 감싸지고, 대상 폴더는 진입 시점에
 * {@link FolderRepository#lockByIdAndDeletedAtIsNull}로 PESSIMISTIC_WRITE 잠긴다. 충돌 검사 → 변경
 * → flush 사이의 race를 차단.
 *
 * <p><b>충돌 검사 이중 가드 (CLAUDE.md §3 원칙 6)</b>: 사전 native query
 * ({@link FolderRepository#existsActiveByParentAndNormalizedName})와 INSERT 시점 V5 unique index
 * 위반({@link DataIntegrityViolationException}) 양쪽 모두 {@link FolderNameConflictException}로 변환.
 * 진실의 출처는 항상 DB unique index.
 *
 * <p><b>감사 emission 정책 (CLAUDE.md §3 원칙 8)</b>: {@link AuditService#record}는 {@code REQUIRES_NEW}
 * 별도 트랜잭션에서 INSERT만 수행하므로 본 서비스의 비즈니스 트랜잭션이 rollback되어도 audit row는
 * 보존된다. before/after 상태는 {@link ObjectMapper}로 JSON 직렬화 — JSONB 컬럼에 대한 caller
 * contract (AuditEvent.beforeState/afterState javadoc 참조).
 *
 * <p><b>actor / 컨텍스트</b>: {@code actorId}는 호출자(controller)가 명시적으로 전달.
 * IP/User-Agent는 {@link WebRequestContextHolder}로 현재 HTTP 요청에서 추출 — 비-HTTP 컨텍스트
 * (스케줄러 등)에서는 자연스럽게 null.
 */
@Service
@Transactional
public class FolderMutationService {

    /** V5 audit_level CHECK과 동일 — service 단에서도 사전 검증해 DB 위반 메시지 노출 회피. */
    private static final Set<String> ALLOWED_AUDIT_LEVELS = Set.of("standard", "strict");

    /** cycle walk 안전 한도 — 데이터 corruption 시 무한 루프 방어 (정상 트리 깊이는 수십 이내). */
    private static final int MAX_ANCESTOR_WALK = 1000;

    private final FolderRepository folderRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public FolderMutationService(FolderRepository folderRepository,
                                 AuditService auditService,
                                 ObjectMapper objectMapper) {
        this.folderRepository = folderRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ──────────────────────────────────────────────────────────────────
    // create
    // ──────────────────────────────────────────────────────────────────

    /**
     * 새 폴더 생성. {@code parentId == null}이면 root 폴더.
     *
     * @throws IllegalArgumentException name/owner/auditLevel 검증 실패
     * @throws FolderNotFoundException  parentId가 활성 폴더가 아님
     * @throws FolderNameConflictException 동일 부모 내 같은 normalized_name 활성 폴더 존재
     */
    public Folder create(UUID parentId, String name, UUID ownerId, String auditLevel, UUID actorId) {
        if (name == null) throw new IllegalArgumentException("name is required");
        if (ownerId == null) throw new IllegalArgumentException("ownerId is required");
        if (auditLevel == null || !ALLOWED_AUDIT_LEVELS.contains(auditLevel)) {
            throw new IllegalArgumentException("auditLevel must be one of: " + ALLOWED_AUDIT_LEVELS);
        }

        // parent가 활성 상태인지 확인 — soft-deleted/존재하지 않는 부모 아래 생성 차단.
        // 잠금까지는 필요 없음 (parent 메타데이터 변경이 아닌 자식 INSERT). DB FK가 최종 가드.
        if (parentId != null) {
            folderRepository.findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new FolderNotFoundException("parent folder not found: " + parentId));
        }

        String displayName = NormalizeUtil.normalizeFileName(name);
        String normalizedName = NormalizeUtil.normalizedNameForDedup(name);

        if (folderRepository.existsActiveByParentAndNormalizedName(parentId, normalizedName)) {
            throw new FolderNameConflictException(
                "folder name already exists under parent: " + normalizedName);
        }

        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setParentId(parentId);
        folder.setName(displayName);
        folder.setNormalizedName(normalizedName);
        folder.setSlug(normalizedName);            // MVP — slug = normalized_name (별도 정책 도입 시 분리)
        folder.setOwnerId(ownerId);
        folder.setAuditLevel(auditLevel);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);

        Folder saved;
        try {
            saved = folderRepository.saveAndFlush(folder);
        } catch (DataIntegrityViolationException ex) {
            // 사전 exists 검사와 INSERT 사이 race — V5 unique index가 최종 진실의 출처.
            throw new FolderNameConflictException(
                "folder name conflict at insert: " + normalizedName, ex);
        }

        // root 폴더는 parentId=null — Map.of 가 null 값을 거부하므로 LinkedHashMap 사용.
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("name", displayName);
        afterState.put("normalizedName", normalizedName);
        afterState.put("parentId", parentId);
        afterState.put("ownerId", ownerId);
        afterState.put("auditLevel", auditLevel);
        emitAudit(AuditEventType.FOLDER_CREATED, saved.getId(), actorId, null, afterState);
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // rename
    // ──────────────────────────────────────────────────────────────────

    /**
     * 활성 폴더의 이름을 변경한다. {@code newName}이 정규화 후 기존 값과 동일하면 no-op (audit 미발행).
     *
     * @throws FolderNotFoundException  folderId가 활성 폴더가 아님 (soft-deleted 포함)
     * @throws FolderNameConflictException 같은 부모 내 다른 활성 폴더와 normalized_name 충돌
     */
    public Folder rename(UUID folderId, String newName, UUID actorId) {
        if (folderId == null) throw new IllegalArgumentException("folderId is required");
        if (newName == null) throw new IllegalArgumentException("newName is required");

        Folder target = folderRepository.lockByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("folder not found: " + folderId));

        String newDisplay = NormalizeUtil.normalizeFileName(newName);
        String newNormalized = NormalizeUtil.normalizedNameForDedup(newName);

        // short-circuit: 정규화 결과가 동일하면 audit 발행 없이 그대로 반환.
        // display name만 다르면 변경 의미가 있으므로 audit 발행 (예: 대소문자/whitespace).
        if (newNormalized.equals(target.getNormalizedName()) && newDisplay.equals(target.getName())) {
            return target;
        }

        if (folderRepository.existsActiveByParentAndNormalizedNameExcludingId(
                target.getParentId(), newNormalized, target.getId())) {
            throw new FolderNameConflictException(
                "folder name already exists under parent: " + newNormalized);
        }

        String oldDisplay = target.getName();
        String oldNormalized = target.getNormalizedName();

        target.setName(newDisplay);
        target.setNormalizedName(newNormalized);
        target.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));

        Folder saved;
        try {
            saved = folderRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException ex) {
            throw new FolderNameConflictException(
                "folder name conflict at rename: " + newNormalized, ex);
        }

        emitAudit(
            AuditEventType.FOLDER_RENAMED,
            saved.getId(),
            actorId,
            Map.of("name", oldDisplay, "normalizedName", oldNormalized),
            Map.of("name", newDisplay, "normalizedName", newNormalized)
        );
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // move
    // ──────────────────────────────────────────────────────────────────

    /**
     * 활성 폴더의 부모를 변경한다. {@code newParentId == null}이면 root로 이동.
     *
     * @throws FolderNotFoundException  folderId 또는 newParentId가 활성 폴더가 아님
     * @throws IllegalArgumentException 자기 자신 또는 자기 자신의 하위 트리로 이동 (cycle)
     * @throws FolderNameConflictException 새 부모 안에 동일 normalized_name 활성 폴더 존재
     */
    public Folder move(UUID folderId, UUID newParentId, UUID actorId) {
        if (folderId == null) throw new IllegalArgumentException("folderId is required");

        Folder target = folderRepository.lockByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("folder not found: " + folderId));

        UUID currentParent = target.getParentId();
        if (java.util.Objects.equals(currentParent, newParentId)) {
            return target;                                  // short-circuit
        }

        if (newParentId != null) {
            // 새 부모가 활성인지 확인. cycle 검사를 위해서도 ancestor walk 진입점이 활성이어야 함.
            folderRepository.findByIdAndDeletedAtIsNull(newParentId)
                .orElseThrow(() -> new FolderNotFoundException(
                    "new parent folder not found: " + newParentId));
            assertNoCycle(folderId, newParentId);
        }

        if (folderRepository.existsActiveByParentAndNormalizedNameExcludingId(
                newParentId, target.getNormalizedName(), target.getId())) {
            throw new FolderNameConflictException(
                "folder name already exists under target parent: " + target.getNormalizedName());
        }

        target.setParentId(newParentId);
        target.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));

        Folder saved;
        try {
            saved = folderRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException ex) {
            throw new FolderNameConflictException(
                "folder name conflict at move: " + target.getNormalizedName(), ex);
        }

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("parentId", currentParent);
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("parentId", newParentId);
        emitAudit(AuditEventType.FOLDER_MOVED, saved.getId(), actorId, beforeState, afterState);
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * {@code newParentId}부터 root까지 부모 체인을 따라가며 {@code folderId}가 발견되면 cycle.
     * 정상 케이스는 root(null) 도달 시 종료. 데이터 corruption(orphan cycle)은 visited Set으로 방어.
     */
    private void assertNoCycle(UUID folderId, UUID newParentId) {
        if (folderId.equals(newParentId)) {
            throw new IllegalArgumentException("cannot move folder into itself");
        }
        UUID cursor = newParentId;
        Set<UUID> visited = new HashSet<>();
        int hops = 0;
        while (cursor != null) {
            if (++hops > MAX_ANCESTOR_WALK) {
                throw new IllegalStateException("ancestor walk exceeded safety limit at " + cursor);
            }
            if (!visited.add(cursor)) {
                // DB에 이미 cycle이 있는 corruption — 이동 자체는 차단하고 위로 보고.
                throw new IllegalStateException("ancestor cycle detected at " + cursor);
            }
            if (cursor.equals(folderId)) {
                throw new IllegalArgumentException("cannot move folder into its own descendant");
            }
            final UUID currentCursor = cursor;
            UUID next = folderRepository.findByIdAndDeletedAtIsNull(currentCursor)
                .map(Folder::getParentId)
                .orElseThrow(() -> new FolderNotFoundException("ancestor folder not found: " + currentCursor));
            cursor = next;
        }
    }

    /**
     * 단일 emission 진입점. ObjectMapper 직렬화 실패는 audit 누락보다 비즈니스 일관성 추적이
     * 더 가치 있으므로 RuntimeException으로 승격 — 그러나 비즈니스 INSERT는 이미 이뤄졌으므로
     * AuditService가 REQUIRES_NEW 트랜잭션에서 받아 INSERT한다 (rollback 격리).
     */
    private void emitAudit(AuditEventType eventType,
                           UUID targetId,
                           UUID actorId,
                           Map<String, ?> beforeState,
                           Map<String, ?> afterState) {
        AuditEvent event = new AuditEvent(
            eventType,
            actorId,
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.FOLDER,
            targetId,
            toJson(beforeState),
            toJson(afterState),
            null
        );
        auditService.record(event);
    }

    private String toJson(Map<String, ?> state) {
        if (state == null) return null;
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit state serialization failed", e);
        }
    }
}
