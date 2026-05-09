package com.ibizdrive.folder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.common.normalize.NormalizeUtil;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.team.TeamArchiveGuard;
import com.ibizdrive.trash.TrashRetentionProperties;
import jakarta.annotation.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p><b>archived 팀 차단 (spec §2.2/§5.4 — Plan A T3)</b>: 5개 write 진입점
 * (create/rename/move/delete/restore)은 target/parent fetch 직후 mutation 직전에
 * {@link TeamArchiveGuard#assertNotArchived(ScopeType, java.util.UUID)}를 호출해
 * archived 팀의 콘텐츠를 read-only로 강제한다. {@code TEAM_ARCHIVED} (HTTP 423) 변환은
 * {@link com.ibizdrive.common.error.GlobalExceptionHandler}.
 */
@Service
@Transactional
public class FolderMutationService {

    /** V5 audit_level CHECK과 동일 — service 단에서도 사전 검증해 DB 위반 메시지 노출 회피. */
    private static final Set<String> ALLOWED_AUDIT_LEVELS = Set.of("standard", "strict");

    /** cycle walk 안전 한도 — 데이터 corruption 시 무한 루프 방어 (정상 트리 깊이는 수십 이내). */
    private static final int MAX_ANCESTOR_WALK = 1000;

    /** cascade BFS 안전 한도 — 비정상 트리 폭에 대한 트랜잭션 timeout/OOM 방어 (A6.1). */
    private static final int MAX_CASCADE_NODES = 100_000;

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    /** 휴지통 보존 기간(일) — application.yml {@code app.trash.retention-days} (FileMutationService와 동일 source). */
    private final TrashRetentionProperties retention;
    private final TeamArchiveGuard teamArchiveGuard;

    public FolderMutationService(FolderRepository folderRepository,
                                 FileRepository fileRepository,
                                 AuditService auditService,
                                 ObjectMapper objectMapper,
                                 TrashRetentionProperties retention,
                                 TeamArchiveGuard teamArchiveGuard) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.retention = retention;
        this.teamArchiveGuard = teamArchiveGuard;
    }

    // ──────────────────────────────────────────────────────────────────
    // create
    // ──────────────────────────────────────────────────────────────────

    /**
     * 새 child 폴더 생성. parent의 {@code (scope_type, scope_id)}를 그대로 상속한다 (spec §1.2 invariant).
     *
     * <p><b>Root 폴더 생성 차단</b>: 본 public API는 {@code parentId == null}을 거부한다 — workspace
     * lifecycle (Task 16 team root, Task 20 department root)이 root 폴더를 생성하는 유일한 경로.
     * 이 invariant는 "모든 폴더는 정확히 하나의 워크스페이스에 속한다"는 §1.2 보장의 1차 가드.
     *
     * @throws IllegalArgumentException name/owner/auditLevel 검증 실패, 또는 {@code parentId == null}
     * @throws FolderNotFoundException  parentId가 활성 폴더가 아님
     * @throws FolderNameConflictException 동일 부모 내 같은 normalized_name 활성 폴더 존재
     */
    public Folder create(UUID parentId, String name, UUID ownerId, String auditLevel, UUID actorId) {
        if (parentId == null) {
            throw new IllegalArgumentException(
                "parent_id required — root folders are created by workspace lifecycle only (spec §1.3)");
        }
        if (name == null) throw new IllegalArgumentException("name is required");
        if (ownerId == null) throw new IllegalArgumentException("ownerId is required");
        if (auditLevel == null || !ALLOWED_AUDIT_LEVELS.contains(auditLevel)) {
            throw new IllegalArgumentException("auditLevel must be one of: " + ALLOWED_AUDIT_LEVELS);
        }

        // parent가 활성 상태인지 확인 + scope 상속을 위해 entity를 가져온다 — soft-deleted/존재하지
        // 않는 부모 아래 생성 차단. 잠금까지는 필요 없음 (parent 메타데이터 변경이 아닌 자식 INSERT).
        // DB FK가 최종 가드.
        Folder parent = folderRepository.findByIdAndDeletedAtIsNull(parentId)
            .orElseThrow(() -> new FolderNotFoundException("parent folder not found: " + parentId));

        // spec §2.2/§5.4 — archived 팀 콘텐츠는 read-only. parent fetch 직후, normalize/conflict 이전.
        teamArchiveGuard.assertNotArchived(parent.getScopeType(), parent.getScopeId());

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
        // spec §1.2 invariant: child는 parent의 scope를 그대로 상속. assignScope는 V13 NOT NULL 제약과
        // 일치하는 non-null 검증을 entity 레벨에서 수행.
        folder.assignScope(parent.getScopeType(), parent.getScopeId());
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

        // child-only 경로 — parentId는 항상 non-null이지만 LinkedHashMap 유지 (key 순서 보존).
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("name", displayName);
        afterState.put("normalizedName", normalizedName);
        afterState.put("parentId", parentId);
        afterState.put("ownerId", ownerId);
        afterState.put("auditLevel", auditLevel);
        afterState.put("scopeType", saved.getScopeType().dbValue());
        afterState.put("scopeId", saved.getScopeId());
        emitAudit(AuditEventType.FOLDER_CREATED, saved.getId(), actorId, null, afterState);
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // createRootForScope
    // ──────────────────────────────────────────────────────────────────

    /**
     * Workspace root folder 생성 — Plan A Task 16+. parentId=null, scope explicit.
     *
     * <p>일반 {@link #create} 경로와 달리 (a) parent lookup/충돌검사 없음, (b)
     * scope를 호출자가 명시 (예: {@link ScopeType#TEAM} + teamId), (c) {@code FOLDER_CREATED}
     * audit 발행하지 않음 — workspace 생성 audit이 우선이고 root folder는 내부 artifact.
     *
     * <p>호출자(예: {@link com.ibizdrive.team.TeamService})는 자신의 트랜잭션 안에서 호출 — 이 메서드는
     * 단독 트랜잭션을 만들지 않는다 ({@code @Transactional} 어노테이션 없음). save 실패 시 호출자
     * 트랜잭션이 rollback.
     *
     * @param scopeType DEPARTMENT 또는 TEAM
     * @param scopeId workspace의 id (department/team)
     * @param ownerId root folder owner (보통 workspace creator)
     * @param displayName workspace 이름과 동일
     * @return 영속화된 root Folder
     */
    public Folder createRootForScope(ScopeType scopeType, UUID scopeId, UUID ownerId, String displayName) {
        if (scopeType == null) throw new IllegalArgumentException("scopeType is required");
        if (scopeId == null) throw new IllegalArgumentException("scopeId is required");
        if (ownerId == null) throw new IllegalArgumentException("ownerId is required");
        if (displayName == null) throw new IllegalArgumentException("displayName is required");

        String normalizedName = NormalizeUtil.normalizedNameForDedup(displayName);

        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setParentId(null);
        folder.setName(NormalizeUtil.normalizeFileName(displayName));
        folder.setNormalizedName(normalizedName);
        folder.setSlug(normalizedName);
        folder.setOwnerId(ownerId);
        folder.setAuditLevel("standard");
        folder.assignScope(scopeType, scopeId);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);

        return folderRepository.saveAndFlush(folder);
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

        // spec §2.2/§5.4 — archived 팀 콘텐츠는 read-only. no-op 단락 이전 — 시도 자체가 write.
        teamArchiveGuard.assertNotArchived(target.getScopeType(), target.getScopeId());

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
     * <p><b>Same-scope 강제 (spec §1.2, §5.6 — Plan A Task 25)</b>: {@code newParentId}가 non-null일
     * 때 target과 newParent의 {@code (scope_type, scope_id)}가 동일해야 한다. 다르면
     * {@link CrossScopeMoveException} (HTTP 409 + {@code ERR_CROSS_SCOPE_MOVE}). 명시적
     * cross-workspace move는 Plan D scope ({@code allowCrossScope: true} + 영향 미리보기).
     *
     * @throws FolderNotFoundException  folderId 또는 newParentId가 활성 폴더가 아님
     * @throws IllegalArgumentException 자기 자신 또는 자기 자신의 하위 트리로 이동 (cycle)
     * @throws CrossScopeMoveException  newParent의 scope가 target과 다름 (Plan A: same-scope만)
     * @throws FolderNameConflictException 새 부모 안에 동일 normalized_name 활성 폴더 존재
     */
    public Folder move(UUID folderId, UUID newParentId, UUID actorId) {
        if (folderId == null) throw new IllegalArgumentException("folderId is required");

        Folder target = folderRepository.lockByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("folder not found: " + folderId));

        // spec §2.2/§5.4 — archived 팀 콘텐츠는 read-only. same-scope 가드가 dst==src를 보장하므로
        // target.scope 기준 1회 호출이면 newParent도 동일하게 차단된다.
        teamArchiveGuard.assertNotArchived(target.getScopeType(), target.getScopeId());

        UUID currentParent = target.getParentId();
        if (java.util.Objects.equals(currentParent, newParentId)) {
            return target;                                  // short-circuit
        }

        if (newParentId != null) {
            // 새 부모가 활성인지 확인. cycle 검사를 위해서도 ancestor walk 진입점이 활성이어야 함.
            Folder newParent = folderRepository.findByIdAndDeletedAtIsNull(newParentId)
                .orElseThrow(() -> new FolderNotFoundException(
                    "new parent folder not found: " + newParentId));
            // Plan A Task 25 — same-scope 가드. newParentId == null (root) 케이스는 §1.2 invariant
            // 와 충돌하나 본 task는 명시적으로 same-scope 검증만 다룸 (rootless folder 차단은
            // 별도 트랙). 가드는 mutation 직전, 가장 저렴한 비교부터 (cycle walk 이전) 배치.
            if (target.getScopeType() != newParent.getScopeType()
                || !target.getScopeId().equals(newParent.getScopeId())) {
                throw new CrossScopeMoveException();
            }
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
    // delete (soft, cascade)
    // ──────────────────────────────────────────────────────────────────

    /**
     * 활성 폴더와 그 후손(폴더 + 파일)을 휴지통으로 이동(soft-delete).
     *
     * <p><b>cascade 전략 (A6.1, plan §A6.1.a)</b>: service 레벨 BFS — {@link #assertNoCycle}와 동일
     * 일관성. 후손 폴더 id를 frontier expansion으로 수집한 뒤 {@link FolderRepository#softDeleteByIds}
     * 1회로 batch UPDATE. WITH RECURSIVE native query로의 전환은 성능 이슈 발견 시 ADR로 기록 후 적용.
     *
     * <p><b>audit 정책 (A6.1, plan §A6.1.b)</b>: cascade 전체에 대해 root {@code FOLDER_DELETED}
     * 1건만 발행. 후손 폴더/파일은 audit 발행 안 함 — audit_log 폭증 회피. after_state에
     * descendantFolders/descendantFiles 카운트가 보존되어 추적 가능.
     *
     * <p><b>originalParentId 정책</b>: root는 {@code parentId}를 {@code originalParentId}로 보존
     * (restore destination 스냅샷). 후손은 NULL 유지 — 자기 자신만 복원 정책에서는 후손 일괄 복원이
     * 없어 originalParentId 사용 경로가 없다 (KISS).
     *
     * @throws FolderNotFoundException folderId가 활성 폴더가 아님 (이미 휴지통 또는 미존재)
     */
    public void delete(UUID folderId, UUID actorId) {
        if (folderId == null) throw new IllegalArgumentException("folderId is required");

        Folder root = folderRepository.lockByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("folder not found: " + folderId));

        // spec §2.2/§5.4 — archived 팀 콘텐츠는 read-only. BFS/cascade 이전.
        teamArchiveGuard.assertNotArchived(root.getScopeType(), root.getScopeId());

        // BFS frontier expansion: root → 후손 ids 수집. visited Set은 데이터 corruption 방어.
        // root 자체는 별도 처리(originalParentId 보존 + entity 단 update + saveAndFlush)이므로
        // descendantIds에는 포함하지 않는다.
        List<UUID> descendantIds = collectDescendantFolderIds(root.getId());

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant purgeAfter = now.plus(retention.days(), ChronoUnit.DAYS);

        // 후손 폴더 batch UPDATE — 비어 있으면 skip (Hibernate가 빈 IN(...)을 거부하는 환경 보호).
        // V10: actorId 전파 — cascade 후손도 root와 동일한 deleter로 기록.
        if (!descendantIds.isEmpty()) {
            folderRepository.softDeleteByIds(descendantIds, actorId, now, purgeAfter);
        }

        // root + 후손 모두를 포함한 folder id 집합 — 파일 cascade 대상.
        List<UUID> allFolderIds = new ArrayList<>(descendantIds.size() + 1);
        allFolderIds.add(root.getId());
        allFolderIds.addAll(descendantIds);

        int descendantFiles = fileRepository.softDeleteByFolderIds(allFolderIds, actorId, now, purgeAfter);

        // root entity — originalParentId 스냅샷 + tombstone 컬럼 set + flush. lock된 entity이므로
        // dirty checking으로도 가능하지만 명시적 saveAndFlush로 audit 발행 직전 상태 확정.
        UUID parentSnapshot = root.getParentId();
        root.setOriginalParentId(parentSnapshot);
        root.setDeletedAt(now);
        root.setPurgeAfter(purgeAfter);
        // V10 — root는 entity-level set, 후손은 softDeleteByIds JPQL이 동일 actorId로 set.
        root.setDeletedBy(actorId);
        root.setUpdatedAt(now);
        Folder saved = folderRepository.saveAndFlush(root);

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("name", saved.getName());
        beforeState.put("parentId", parentSnapshot);
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("deletedAt", now.toString());
        afterState.put("purgeAfter", purgeAfter.toString());
        afterState.put("originalParentId", parentSnapshot);
        afterState.put("descendantFolders", descendantIds.size());
        afterState.put("descendantFiles", descendantFiles);
        emitAudit(AuditEventType.FOLDER_DELETED, saved.getId(), actorId, beforeState, afterState);
    }

    // ──────────────────────────────────────────────────────────────────
    // restore
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 폴더를 {@code original_parent_id}로 복원한다. tombstone 컬럼 3종을 모두 NULL로 클리어.
     *
     * <p><b>복원 범위 (A6.2, plan §A6.1.b)</b>: 자기 자신만 복원. 후손은 휴지통 잔존 — 후손 일괄
     * 복원은 별도 endpoint 트랙. 사용자에게 "부모 먼저 복원" 흐름을 강제(KISS).
     *
     * <p>{@code originalParentId == null}이면 root였던 폴더로 정상 처리 (parent active 검사 skip).
     * non-null이면 원래 parent가 여전히 active인지 확인 — soft-deleted parent 아래로의 복원은 불허.
     *
     * <p>UNIQUE 재검사: V5 partial unique index가 {@code deleted_at IS NULL}인 행만 대상으로 하므로,
     * tombstone NULL 클리어 시점에 동일 (parent, normalized_name) 충돌 가능 — 사전 native query +
     * UPDATE 시점 {@code DataIntegrityViolationException} 이중 가드.
     *
     * @throws FolderNotFoundException     folderId가 휴지통 폴더가 아님 (이미 활성 또는 미존재) 또는
     *                                     {@code originalParentId}가 활성 폴더가 아님
     * @throws FolderRestoreConflictException 원위치에 동일 normalized_name 활성 폴더 존재
     */
    public Folder restore(UUID folderId, UUID actorId) {
        return restore(folderId, actorId, null);
    }

    public Folder restore(UUID folderId, UUID actorId, @Nullable String newName) {
        if (folderId == null) throw new IllegalArgumentException("folderId is required");

        Folder target = folderRepository.lockByIdAndDeletedAtIsNotNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("trashed folder not found: " + folderId));

        // spec §2.2/§5.4 — archived 팀 콘텐츠는 read-only. soft-deleted row의 scope_type/scope_id는
        // V13 NOT NULL 제약으로 preserve되므로 target.scope로 그대로 검증.
        teamArchiveGuard.assertNotArchived(target.getScopeType(), target.getScopeId());

        UUID originalParentSnapshot = target.getOriginalParentId();
        UUID restoreParentId = originalParentSnapshot != null
            ? originalParentSnapshot
            : target.getParentId();
        if (restoreParentId != null) {
            // 원래 parent가 활성인지 확인. soft-deleted parent로의 복원은 불허 — 사용자가 parent부터
            // 복원해야 한다는 UX 강제 (자기 자신만 복원 정책의 일관성).
            folderRepository.findByIdAndDeletedAtIsNull(restoreParentId)
                .orElseThrow(() -> new FolderNotFoundException(
                    "original parent is not active: " + restoreParentId));
        }

        // newName 정규화 (지정 시) — rename 패턴 미러.
        String oldDisplay = target.getName();
        String oldNormalized = target.getNormalizedName();
        String resolvedDisplay;
        String resolvedNormalized;
        boolean renaming = newName != null;
        if (renaming) {
            resolvedDisplay = NormalizeUtil.normalizeFileName(newName);
            resolvedNormalized = NormalizeUtil.normalizedNameForDedup(newName);
        } else {
            resolvedDisplay = oldDisplay;
            resolvedNormalized = oldNormalized;
        }

        if (folderRepository.existsActiveByParentAndNormalizedNameExcludingId(
                restoreParentId, resolvedNormalized, target.getId())) {
            if (renaming) {
                throw new FolderNameConflictException(
                    "folder name already exists at restore destination: " + resolvedNormalized);
            }
            throw new FolderRestoreConflictException(
                "folder name already exists at restore destination: " + resolvedNormalized);
        }

        Instant deletedAtBefore = target.getDeletedAt();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        target.setParentId(restoreParentId);
        if (renaming) {
            target.setName(resolvedDisplay);
            target.setNormalizedName(resolvedNormalized);
        }
        target.setDeletedAt(null);
        target.setPurgeAfter(null);
        target.setOriginalParentId(null);
        // V10 — restore 시 deleter 정보도 클리어 (CHECK 단방향: 활성 row는 deleted_by IS NULL).
        target.setDeletedBy(null);
        target.setUpdatedAt(now);

        Folder saved;
        try {
            saved = folderRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException ex) {
            // partial unique index가 deleted_at IS NULL인 행에만 적용되므로 NULL로 클리어하는
            // UPDATE 시점에 race로 충돌 가능 — 사전 검사 이중 가드 (CLAUDE.md §3 원칙 6).
            if (renaming) {
                throw new FolderNameConflictException(
                    "folder name conflict at restore: " + resolvedNormalized, ex);
            }
            throw new FolderRestoreConflictException(
                "folder name conflict at restore: " + resolvedNormalized, ex);
        }

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("deletedAt", deletedAtBefore == null ? null : deletedAtBefore.toString());
        beforeState.put("originalParentId", originalParentSnapshot);
        beforeState.put("restoreParentId", restoreParentId);
        if (renaming) {
            beforeState.put("name", oldDisplay);
            beforeState.put("normalizedName", oldNormalized);
        }
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("parentId", restoreParentId);
        afterState.put("deletedAt", null);
        if (renaming) {
            afterState.put("name", resolvedDisplay);
            afterState.put("normalizedName", resolvedNormalized);
        }
        emitAudit(AuditEventType.FOLDER_RESTORED, saved.getId(), actorId, beforeState, afterState);
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
            // Optional.map(Folder::getParentId) 는 root 폴더의 parentId=null 을
            // Optional.empty() 로 collapse 시켜 orElseThrow 가 오발 — 분리해서 lookup 후 getParentId 호출.
            Folder ancestor = folderRepository.findByIdAndDeletedAtIsNull(currentCursor)
                .orElseThrow(() -> new FolderNotFoundException("ancestor folder not found: " + currentCursor));
            cursor = ancestor.getParentId();
        }
    }

    /**
     * cascade 대상 후손 폴더 id를 BFS로 수집한다 ({@code rootId} 자체는 결과에 포함하지 않음).
     *
     * <p>visited Set은 정상 트리에서는 동작에 영향이 없으나, 데이터 corruption(orphan cycle)
     * 시나리오에서 무한 루프를 차단한다. {@link #MAX_CASCADE_NODES}는 트랜잭션 timeout/OOM 대비
     * 안전 한도 — 정상 운영에서 도달하지 않는다.
     */
    private List<UUID> collectDescendantFolderIds(UUID rootId) {
        List<UUID> descendants = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(rootId);
        Deque<UUID> frontier = new ArrayDeque<>();
        frontier.add(rootId);

        while (!frontier.isEmpty()) {
            UUID current = frontier.pollFirst();
            List<UUID> children = folderRepository.findIdsByParentIdAndDeletedAtIsNull(current);
            for (UUID childId : children) {
                if (!visited.add(childId)) continue;            // corruption guard
                descendants.add(childId);
                frontier.addLast(childId);
                if (descendants.size() > MAX_CASCADE_NODES) {
                    throw new IllegalStateException(
                        "cascade descendant count exceeded safety limit at " + childId);
                }
            }
        }
        return descendants;
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
