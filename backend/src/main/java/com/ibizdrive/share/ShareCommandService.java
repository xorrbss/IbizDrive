package com.ibizdrive.share;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.permission.Preset;
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
    private final PermissionService permissionService;
    private final ShareRepository shareRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ShareCommandService(FileRepository fileRepository,
                               PermissionService permissionService,
                               ShareRepository shareRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.fileRepository = fileRepository;
        this.permissionService = permissionService;
        this.shareRepository = shareRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * N subjects → N shares를 단일 트랜잭션에서 생성.
     *
     * @param fileId  대상 파일 (active만). 미존재/soft-deleted면 {@link ResourceNotFoundException}.
     * @param request subjects + preset + expiresAt? + message?
     * @param actorId 호출자 (shared_by + permissions.granted_by + audit actor).
     * @return 생성된 Share row 목록 (id/createdAt 채워진 상태). controller가 ShareDto 매핑.
     */
    @Transactional
    public List<Share> createShares(UUID fileId, ShareCreateRequest request, UUID actorId) {
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

        List<Share> created = new ArrayList<>(subjects.size());
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
                grant.getId(),
                subject.type(),
                subjectId,
                preset,
                request.expiresAt(),
                request.message()
            ));
            created.add(share);
        }
        return created;
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
