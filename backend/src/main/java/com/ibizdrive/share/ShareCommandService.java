package com.ibizdrive.share;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A10.2 — POST /api/files/:fileId/share 트랜잭션 처리 (docs/02 §7.9, ADR #34).
 *
 * <p>한 요청은 1+ subject에 대해 N개의 share를 생성한다. 각 subject 별로:
 * <ol>
 *   <li>{@link PermissionService#grantPermission}로 permissions row 생성
 *       (→ {@code PermissionGrantedEvent} → audit {@code permission.granted}).</li>
 *   <li>{@code shares} row INSERT (메타: message / expiresAt / sharedBy).</li>
 *   <li>{@link ShareCreatedEvent} publish
 *       (→ {@code share.created} audit는 ShareAuditListener에서 — A10.3).</li>
 * </ol>
 *
 * <p>전체는 단일 {@code @Transactional}. 한 subject가 conflict(409)를 발생시키면 트랜잭션 rollback —
 * 부분 성공 없음. audit row(REQUIRES_NEW)는 이미 INSERT된 만큼만 보존된다 (감사 무결성, ADR #25 동형).
 *
 * <p><b>입력 검증</b>:
 * <ul>
 *   <li>{@code fileId} active file이어야 함 (soft-deleted 제외).</li>
 *   <li>{@code subjects}는 1개 이상.</li>
 *   <li>{@code preset}은 V5 CHECK 4값({@code read|upload|edit|admin})만. {@code share}는 enum-only,
 *       persistable 아님 — controller에서 400으로 거부.</li>
 *   <li>{@code expiresAt}은 NULL 또는 미래 시각 (PermissionService.validateGrantInput과 동일).</li>
 *   <li>{@code message}는 NULL 또는 ≤ 1000자.</li>
 * </ul>
 */
@Service
public class ShareCommandService {

    /** V5 permissions_preset_check 호환 — Preset.SHARE는 별도 shares 테이블에서만 사용 (enum-only). */
    private static final Set<Preset> PERSISTABLE_PRESETS =
        Set.of(Preset.READ, Preset.UPLOAD, Preset.EDIT, Preset.ADMIN);

    /** controller가 1차로 검증하지만 service도 방어적 재검증 (CLAUDE.md §3 원칙 6). */
    private static final int MESSAGE_MAX_LENGTH = 1000;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final PermissionService permissionService;
    private final PermissionRepository permissionRepository;
    private final ShareRepository shareRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ShareCommandService(FileRepository fileRepository,
                               FolderRepository folderRepository,
                               PermissionService permissionService,
                               PermissionRepository permissionRepository,
                               ShareRepository shareRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.permissionService = permissionService;
        this.permissionRepository = permissionRepository;
        this.shareRepository = shareRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * N subjects → N shares를 단일 트랜잭션에서 생성.
     *
     * @param fileId  대상 파일 (active만). 미존재/soft-deleted면 {@link ResourceNotFoundException}.
     * @param request subjects + preset + expiresAt? + message?
     * @param actorId 호출자 (shared_by + permissions.granted_by + audit actor).
     * @return 생성된 ShareDto 목록 — A13. 각 row는 매칭된 permission grant 메타(subjectType/subjectId/preset)
     *     까지 포함. loop 내 {@link PermissionRow grant}를 그대로 사용 → 추가 query 없음.
     */
    @Transactional
    public List<ShareDto> createShares(UUID fileId, ShareCreateRequest request, UUID actorId) {
        if (fileId == null) throw new IllegalArgumentException("fileId must not be null");
        if (actorId == null) throw new IllegalArgumentException("actorId must not be null");
        if (request == null) throw new IllegalArgumentException("request must not be null");

        Preset preset = parsePreset(request.preset());
        validateMessage(request.message());
        // expiresAt 미래 검사는 PermissionService.validateGrantInput에서 일관 적용 — 여기서 사전 short-circuit.
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        List<ShareCreateRequest.Subject> subjects = request.subjects();
        if (subjects == null || subjects.isEmpty()) {
            throw new IllegalArgumentException("subjects must not be empty");
        }

        FileItem file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new ResourceNotFoundException("file not found: " + fileId));

        List<ShareDto> created = new ArrayList<>(subjects.size());
        for (ShareCreateRequest.Subject subject : subjects) {
            if (subject == null || subject.type() == null) {
                throw new IllegalArgumentException("subject.type must not be null");
            }
            // PermissionService가 subjectType / subjectId 매트릭스 + everyone 분기를 검증.
            UUID subjectId = "everyone".equals(subject.type()) ? null : subject.id();

            PermissionRow grant = permissionService.grantPermission(
                "file", file.getId(), subject.type(), subjectId,
                preset, request.expiresAt(), actorId
            );

            Share share = new Share();
            share.setId(UUID.randomUUID());
            share.setFileId(file.getId());
            share.setFolderId(null);                       // MVP: file 공유 endpoint 한정
            share.setPermissionId(grant.getId());
            share.setSharedBy(actorId);
            share.setMessage(request.message());
            share.setExpiresAt(request.expiresAt());
            share.setCreatedAt(Instant.now());
            share.setRevokedAt(null);
            share.setRevokedBy(null);
            shareRepository.saveAndFlush(share);

            eventPublisher.publishEvent(new ShareCreatedEvent(
                actorId,
                share.getId(),
                file.getId(),
                null,                                  // folderId — file path
                grant.getId(),
                subject.type(),
                subjectId,
                preset,
                request.expiresAt(),
                request.message()
            ));
            // A13: grant는 본 트랜잭션에서 막 INSERT한 row → ShareDto join용으로 그대로 사용.
            created.add(ShareDto.from(share, grant));
        }
        return created;
    }

    /**
     * A12 — POST /api/folders/:folderId/share 트랜잭션 처리. {@link #createShares}(file)와 동형이며,
     * 차이는 (1) {@link FolderRepository}로 active folder 검증 (2) {@code share.setFolderId(folderId)} +
     * {@code share.setFileId(null)} (3) {@link PermissionService#grantPermission}에 {@code "folder"} 전달
     * (4) {@link ShareCreatedEvent}에 {@code folderId} 채움 (XOR invariant).
     *
     * <p>입력 검증/공통 helper(parsePreset/validateMessage/expiresAt 미래)는 file 변형과 1:1 재사용.
     */
    @Transactional
    public List<ShareDto> createFolderShares(UUID folderId, ShareCreateRequest request, UUID actorId) {
        if (folderId == null) throw new IllegalArgumentException("folderId must not be null");
        if (actorId == null) throw new IllegalArgumentException("actorId must not be null");
        if (request == null) throw new IllegalArgumentException("request must not be null");

        Preset preset = parsePreset(request.preset());
        validateMessage(request.message());
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        List<ShareCreateRequest.Subject> subjects = request.subjects();
        if (subjects == null || subjects.isEmpty()) {
            throw new IllegalArgumentException("subjects must not be empty");
        }

        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new ResourceNotFoundException("folder not found: " + folderId));

        List<ShareDto> created = new ArrayList<>(subjects.size());
        for (ShareCreateRequest.Subject subject : subjects) {
            if (subject == null || subject.type() == null) {
                throw new IllegalArgumentException("subject.type must not be null");
            }
            UUID subjectId = "everyone".equals(subject.type()) ? null : subject.id();

            PermissionRow grant = permissionService.grantPermission(
                "folder", folder.getId(), subject.type(), subjectId,
                preset, request.expiresAt(), actorId
            );

            Share share = new Share();
            share.setId(UUID.randomUUID());
            share.setFileId(null);                          // folder share — XOR invariant
            share.setFolderId(folder.getId());
            share.setPermissionId(grant.getId());
            share.setSharedBy(actorId);
            share.setMessage(request.message());
            share.setExpiresAt(request.expiresAt());
            share.setCreatedAt(Instant.now());
            share.setRevokedAt(null);
            share.setRevokedBy(null);
            shareRepository.saveAndFlush(share);

            eventPublisher.publishEvent(new ShareCreatedEvent(
                actorId,
                share.getId(),
                null,                                  // fileId — folder path
                folder.getId(),
                grant.getId(),
                subject.type(),
                subjectId,
                preset,
                request.expiresAt(),
                request.message()
            ));
            // A13: file 변형과 동형 — grant를 ShareDto join에 그대로 사용.
            created.add(ShareDto.from(share, grant));
        }
        return created;
    }

    /**
     * DELETE /api/shares/:shareId — 사용자/관리자 의도 revoke (docs/02 §7.9, ADR #34 결정 1).
     *
     * <p>{@link #lockAndCascadeRevoke} helper에 흐름을 위임하고 본 메서드는 actor 검증 +
     * {@link ShareRevokedEvent} publish만 책임. {@link #expireShare}와 helper를 공유하나
     * 발행 이벤트가 달라 audit listener에서 {@code share.revoked} ↔ {@code share.expired} 분기.
     *
     * <p>권한 가드는 {@link #canRevoke}가 {@code @PreAuthorize}에서 1차 처리. 본 메서드는 권한 검사를
     * 다시 하지 않는다 — controller-service 분리 일관성.
     *
     * @throws ResourceNotFoundException share 미존재 또는 이미 revoke된 share
     */
    @Transactional
    public void revokeShare(UUID shareId, UUID actorId) {
        if (shareId == null) throw new IllegalArgumentException("shareId must not be null");
        if (actorId == null) throw new IllegalArgumentException("actorId must not be null");

        Snapshot snap = lockAndCascadeRevoke(shareId, actorId);

        eventPublisher.publishEvent(new ShareRevokedEvent(
            actorId,
            shareId,
            snap.fileId(),
            snap.folderId(),
            snap.permissionId(),
            snap.originalSharedBy(),
            snap.originalCreatedAt(),
            snap.originalExpiresAt(),
            snap.originalMessage()
        ));
    }

    /**
     * 시스템 트리거 만료 — {@link ShareExpirationJob}이 {@code shares.expires_at <= NOW()} row를
     * 발견하면 본 메서드 호출 (ADR #34 backlog "SHARE_EXPIRED 배치 cron" closure).
     *
     * <p>{@link #revokeShare}와 동일한 cascade 흐름이지만 (1) actor 인자 부재 (시스템 트리거,
     * {@code revoked_by=NULL}) (2) {@link ShareExpiredEvent} 발행으로 audit이 {@code share.expired}로 기록.
     *
     * <p>controller 매핑이 없는 internal API — {@code @PreAuthorize} 미적용. 호출 경로는 Spring application
     * context 내 {@link ShareExpirationJob}만이며, race condition(사용자 직접 revoke 직전)은 lock helper의
     * {@code Optional.orElseThrow}가 {@link ResourceNotFoundException}으로 보호 → cron 호출 측이 swallow + 다음 row.
     *
     * @throws ResourceNotFoundException share 미존재 또는 이미 revoke된 share (race-safe)
     */
    @Transactional
    public void expireShare(UUID shareId) {
        if (shareId == null) throw new IllegalArgumentException("shareId must not be null");

        Snapshot snap = lockAndCascadeRevoke(shareId, /*revokedBy=*/ null);

        eventPublisher.publishEvent(new ShareExpiredEvent(
            shareId,
            snap.fileId(),
            snap.folderId(),
            snap.permissionId(),
            snap.originalSharedBy(),
            snap.originalCreatedAt(),
            snap.originalExpiresAt(),
            snap.originalMessage()
        ));
    }

    /**
     * {@link #revokeShare}/{@link #expireShare} 공통 helper — lock + snapshot + revoked_at SET +
     * permission cascade-delete까지 일괄 수행. 호출자는 반환된 {@link Snapshot}으로 도메인 이벤트만 publish.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link ShareRepository#lockByIdAndRevokedAtIsNull}로 row lock + 활성 확인 → 미존재면 404.</li>
     *   <li>snapshot 캡처 — V6 ON DELETE CASCADE 직전 event payload용 보존.</li>
     *   <li>{@code revoked_at=NOW()}, {@code revoked_by=revokedBy(nullable)} UPDATE — lock 보유 의도 표시.</li>
     *   <li>{@link PermissionRepository#deleteById}로 permission row 직접 삭제 → V6 CASCADE로 share row 함께 삭제.
     *       {@link PermissionService#revokePermission} 우회 → {@code PermissionRevokedEvent} 미발행
     *       (이중 audit 회피, ADR #34 결정 1).</li>
     * </ol>
     *
     * @param revokedBy nullable — 사용자 revoke는 actor UUID, 시스템 만료는 {@code null}
     */
    private Snapshot lockAndCascadeRevoke(UUID shareId, UUID revokedBy) {
        Share share = shareRepository.lockByIdAndRevokedAtIsNull(shareId)
            .orElseThrow(() -> new ResourceNotFoundException("share not found or already revoked: " + shareId));

        // file/folder XOR — V6 CHECK 보증, listener에서 분기.
        Snapshot snap = new Snapshot(
            share.getFileId(),
            share.getFolderId(),
            share.getPermissionId(),
            share.getSharedBy(),
            share.getCreatedAt(),
            share.getExpiresAt(),
            share.getMessage()
        );

        share.setRevokedAt(Instant.now());
        share.setRevokedBy(revokedBy);
        shareRepository.saveAndFlush(share);

        permissionRepository.deleteById(snap.permissionId());
        return snap;
    }

    /** {@link #lockAndCascadeRevoke} 결과 — event payload 조립 직전 보존된 share row 상태. */
    private record Snapshot(
        UUID fileId,
        UUID folderId,
        UUID permissionId,
        UUID originalSharedBy,
        Instant originalCreatedAt,
        Instant originalExpiresAt,
        String originalMessage
    ) {}

    /**
     * DELETE /api/shares/:shareId의 SpEL 가드. {@link com.ibizdrive.permission.PermissionService#canRevokePermission}
     * 패턴 동형.
     *
     * <p>호출 형태: {@code @PreAuthorize("@shareCommandService.canRevoke(#shareId, principal)")}.
     *
     * <p>평가 (ADR #34 결정 4):
     * <ul>
     *   <li>{@code share.shared_by == principal.userId} — 자기가 만든 share는 revoke 가능.</li>
     *   <li>OR {@code principal.role == ADMIN} — 관리자는 모든 share revoke 가능.</li>
     * </ul>
     *
     * <p>{@code shareId}가 존재하지 않거나 이미 revoke된 share는 false 반환 — Spring Security가 403으로 매핑.
     * 404는 controller 본체에서 분리 판정.
     */
    public boolean canRevoke(UUID shareId, IbizDriveUserDetails currentUser) {
        if (currentUser == null || shareId == null) return false;
        Role role = currentUser.getUser().getRole();
        if (role == Role.ADMIN) return true;
        return shareRepository.findByIdAndRevokedAtIsNull(shareId)
            .map(s -> s.getSharedBy() != null && s.getSharedBy().equals(currentUser.getUser().getId()))
            .orElse(false);
    }

    private static Preset parsePreset(String wire) {
        if (wire == null) throw new IllegalArgumentException("preset must not be null");
        Preset preset;
        try {
            preset = Preset.from(wire);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid preset: " + wire, ex);
        }
        if (!PERSISTABLE_PRESETS.contains(preset)) {
            // ADR #34: Preset.SHARE는 enum-only — V5 permissions_preset_check 4값에 부재.
            throw new IllegalArgumentException(
                "preset 'share' is not persistable in shares endpoint (ADR #34); "
                    + "use read|upload|edit|admin");
        }
        return preset;
    }

    private static void validateMessage(String message) {
        if (message != null && message.length() > MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "message exceeds " + MESSAGE_MAX_LENGTH + " characters");
        }
    }
}
