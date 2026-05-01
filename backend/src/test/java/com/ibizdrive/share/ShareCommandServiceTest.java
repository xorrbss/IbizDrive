package com.ibizdrive.share;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A10.2 Mockito 단위 테스트 — {@link ShareCommandService#createShares}.
 *
 * <p>커버리지:
 * <ul>
 *   <li>입력 검증: null/빈 subjects, message 1000자 초과, preset='share' 거부, expiresAt 과거.</li>
 *   <li>Not-found: file이 없거나 soft-deleted면 {@link ResourceNotFoundException}.</li>
 *   <li>Happy path single subject: grantPermission + share INSERT + ShareCreatedEvent publish.</li>
 *   <li>Happy path multi subjects: 2회 grantPermission + 2 share + 2 event.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareCommandServiceTest {

    @Mock
    FileRepository fileRepository;
    @Mock
    PermissionService permissionService;
    @Mock
    PermissionRepository permissionRepository;
    @Mock
    ShareRepository shareRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ShareCommandService service;

    UUID fileId;
    UUID actorId;
    FileItem file;

    @BeforeEach
    void setUp() {
        fileId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        // FileItem has package-protected no-arg ctor — mock + stub getter to avoid cross-package access.
        file = mock(FileItem.class);
        when(file.getId()).thenReturn(fileId);
    }

    @Test
    void createShares_throwsNotFound_whenFileMissing() {
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.empty());
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "read", null, null
        );

        assertThatThrownBy(() -> service.createShares(fileId, req, actorId))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(permissionService, never()).grantPermission(
            any(), any(), any(), any(), any(), any(), any());
        verify(shareRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createShares_rejectsEmptySubjects() {
        ShareCreateRequest req = new ShareCreateRequest(List.of(), "read", null, null);

        assertThatThrownBy(() -> service.createShares(fileId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subjects");

        verify(fileRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void createShares_rejectsMessageOver1000Chars() {
        String tooLong = "x".repeat(1001);
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "read", null, tooLong
        );

        assertThatThrownBy(() -> service.createShares(fileId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1000");
    }

    @Test
    void createShares_rejectsPresetShare_v5CheckIncompatible() {
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "share", null, null   // Preset.SHARE는 enum-only — V5 CHECK 4값에 부재 (ADR #34).
        );

        assertThatThrownBy(() -> service.createShares(fileId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("share");
    }

    @Test
    void createShares_rejectsExpiresAtInPast() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "read", past, null
        );

        assertThatThrownBy(() -> service.createShares(fileId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("future");
    }

    @Test
    void createShares_rejectsUnknownPresetWire() {
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "bogus-preset", null, null
        );

        assertThatThrownBy(() -> service.createShares(fileId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("preset");
    }

    @Test
    void createShares_singleUserSubject_emitsGrantPermissionAndShareEvent() {
        UUID subjectId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        PermissionRow grant = grantRow(grantId);
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("user"),
            eq(subjectId), any(), any(), eq(actorId))).thenReturn(grant);

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", subjectId)),
            "edit", null, "hello"
        );

        List<Share> result = service.createShares(fileId, req, actorId);

        assertThat(result).hasSize(1);
        Share saved = result.get(0);
        assertThat(saved.getFileId()).isEqualTo(fileId);
        assertThat(saved.getFolderId()).isNull();
        assertThat(saved.getPermissionId()).isEqualTo(grantId);
        assertThat(saved.getSharedBy()).isEqualTo(actorId);
        assertThat(saved.getMessage()).isEqualTo("hello");
        assertThat(saved.getRevokedAt()).isNull();

        verify(shareRepository, times(1)).saveAndFlush(any(Share.class));

        ArgumentCaptor<ShareCreatedEvent> evCaptor = ArgumentCaptor.forClass(ShareCreatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(evCaptor.capture());
        ShareCreatedEvent ev = evCaptor.getValue();
        assertThat(ev.shareId()).isEqualTo(saved.getId());
        assertThat(ev.fileId()).isEqualTo(fileId);
        assertThat(ev.permissionId()).isEqualTo(grantId);
        assertThat(ev.subjectType()).isEqualTo("user");
        assertThat(ev.subjectId()).isEqualTo(subjectId);
        assertThat(ev.preset().wire()).isEqualTo("edit");
        assertThat(ev.message()).isEqualTo("hello");
    }

    @Test
    void createShares_everyoneSubject_passesNullSubjectIdToGrantPermission() {
        PermissionRow grant = grantRow(UUID.randomUUID());
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("everyone"),
            eq(null), any(), any(), eq(actorId))).thenReturn(grant);

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("everyone", UUID.randomUUID())),  // id 무시
            "read", null, null
        );

        service.createShares(fileId, req, actorId);

        verify(permissionService).grantPermission(eq("file"), eq(fileId), eq("everyone"),
            eq(null), any(), any(), eq(actorId));
    }

    @Test
    void createShares_multipleSubjects_callsGrantPermissionPerSubject() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        PermissionRow grant1 = grantRow(UUID.randomUUID());
        PermissionRow grant2 = grantRow(UUID.randomUUID());
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("user"),
            eq(s1), any(), any(), eq(actorId))).thenReturn(grant1);
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("department"),
            eq(s2), any(), any(), eq(actorId))).thenReturn(grant2);

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(
                new ShareCreateRequest.Subject("user", s1),
                new ShareCreateRequest.Subject("department", s2)
            ),
            "upload", null, null
        );

        List<Share> result = service.createShares(fileId, req, actorId);

        assertThat(result).hasSize(2);
        verify(permissionService, times(2)).grantPermission(
            any(), any(), any(), any(), any(), any(), any());
        verify(shareRepository, times(2)).saveAndFlush(any(Share.class));
        verify(eventPublisher, times(2)).publishEvent(any(ShareCreatedEvent.class));
    }

    @Test
    void createShares_acceptsExpiresAtInFutureAndMessage1000Chars() {
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        String exact1000 = "y".repeat(1000);
        UUID subjectId = UUID.randomUUID();
        PermissionRow grant = grantRow(UUID.randomUUID());
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("user"),
            eq(subjectId), any(), eq(future), eq(actorId))).thenReturn(grant);

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", subjectId)),
            "read", future, exact1000
        );

        List<Share> result = service.createShares(fileId, req, actorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExpiresAt()).isEqualTo(future);
        assertThat(result.get(0).getMessage()).isEqualTo(exact1000);
    }

    /** PermissionRow ctor도 package-protected — service는 row.getId()만 쓰므로 mock으로 충분. */
    private PermissionRow grantRow(UUID id) {
        PermissionRow row = mock(PermissionRow.class);
        when(row.getId()).thenReturn(id);
        return row;
    }

    // ── A10.3 — revokeShare ──────────────────────────────────────────────

    @Test
    void revokeShare_throwsNotFound_whenShareMissingOrAlreadyRevoked() {
        UUID shareId = UUID.randomUUID();
        when(shareRepository.lockByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeShare(shareId, actorId))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(permissionRepository, never()).deleteById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void revokeShare_setsRevokedAt_deletesPermission_publishesEvent() {
        UUID shareId = UUID.randomUUID();
        UUID sharePermissionId = UUID.randomUUID();
        UUID shareFileId = UUID.randomUUID();
        UUID sharedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");

        Share share = mock(Share.class);
        when(share.getFileId()).thenReturn(shareFileId);
        when(share.getPermissionId()).thenReturn(sharePermissionId);
        when(share.getSharedBy()).thenReturn(sharedBy);
        when(share.getCreatedAt()).thenReturn(createdAt);
        when(share.getExpiresAt()).thenReturn(expiresAt);
        when(share.getMessage()).thenReturn("orig msg");
        when(shareRepository.lockByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.of(share));

        service.revokeShare(shareId, actorId);

        // share row UPDATE — revoked_at + revoked_by setter 호출 확인.
        verify(share).setRevokedAt(any());
        verify(share).setRevokedBy(actorId);
        verify(shareRepository).saveAndFlush(share);
        // permission row 직접 deleteById — PermissionService.revokePermission 우회 (이중 audit 회피).
        verify(permissionRepository).deleteById(sharePermissionId);
        verify(permissionService, never()).revokePermission(any(), any());

        ArgumentCaptor<ShareRevokedEvent> evCaptor = ArgumentCaptor.forClass(ShareRevokedEvent.class);
        verify(eventPublisher).publishEvent(evCaptor.capture());
        ShareRevokedEvent ev = evCaptor.getValue();
        assertThat(ev.actorId()).isEqualTo(actorId);
        assertThat(ev.shareId()).isEqualTo(shareId);
        assertThat(ev.fileId()).isEqualTo(shareFileId);
        assertThat(ev.permissionId()).isEqualTo(sharePermissionId);
        assertThat(ev.originalSharedBy()).isEqualTo(sharedBy);
        assertThat(ev.originalCreatedAt()).isEqualTo(createdAt);
        assertThat(ev.originalExpiresAt()).isEqualTo(expiresAt);
        assertThat(ev.originalMessage()).isEqualTo("orig msg");
    }

    // ── A10.3 — canRevoke ────────────────────────────────────────────────

    @Test
    void canRevoke_returnsFalse_whenUserNull() {
        assertThat(service.canRevoke(UUID.randomUUID(), null)).isFalse();
    }

    @Test
    void canRevoke_returnsTrue_whenAdmin_withoutLookup() {
        IbizDriveUserDetails admin = userDetails(UUID.randomUUID(), Role.ADMIN);

        assertThat(service.canRevoke(UUID.randomUUID(), admin)).isTrue();
        verify(shareRepository, never()).findByIdAndRevokedAtIsNull(any());
    }

    @Test
    void canRevoke_returnsTrue_whenSharedByMatchesPrincipal() {
        UUID shareId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        IbizDriveUserDetails member = userDetails(userId, Role.MEMBER);
        Share share = mock(Share.class);
        when(share.getSharedBy()).thenReturn(userId);
        when(shareRepository.findByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.of(share));

        assertThat(service.canRevoke(shareId, member)).isTrue();
    }

    @Test
    void canRevoke_returnsFalse_whenSharedByDifferentAndNotAdmin() {
        UUID shareId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        IbizDriveUserDetails member = userDetails(userId, Role.MEMBER);
        Share share = mock(Share.class);
        when(share.getSharedBy()).thenReturn(otherUser);
        when(shareRepository.findByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.of(share));

        assertThat(service.canRevoke(shareId, member)).isFalse();
    }

    @Test
    void canRevoke_returnsFalse_whenShareNotFound() {
        UUID shareId = UUID.randomUUID();
        IbizDriveUserDetails member = userDetails(UUID.randomUUID(), Role.MEMBER);
        when(shareRepository.findByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.empty());

        assertThat(service.canRevoke(shareId, member)).isFalse();
    }

    private IbizDriveUserDetails userDetails(UUID userId, Role role) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(userId);
        when(u.getRole()).thenReturn(role);
        IbizDriveUserDetails details = mock(IbizDriveUserDetails.class);
        when(details.getUser()).thenReturn(u);
        return details;
    }
}
