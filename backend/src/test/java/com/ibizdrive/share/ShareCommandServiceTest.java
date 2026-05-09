package com.ibizdrive.share;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamRepository;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
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
    FolderRepository folderRepository;
    @Mock
    PermissionService permissionService;
    @Mock
    PermissionRepository permissionRepository;
    @Mock
    ShareRepository shareRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    DepartmentRepository departmentRepository;
    @Mock
    TeamRepository teamRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ShareCommandService service;

    UUID fileId;
    UUID folderId;
    UUID actorId;
    FileItem file;
    Folder folder;

    @BeforeEach
    void setUp() {
        fileId = UUID.randomUUID();
        folderId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        // FileItem has package-protected no-arg ctor — mock + stub getter to avoid cross-package access.
        file = mock(FileItem.class);
        when(file.getId()).thenReturn(fileId);
        folder = mock(Folder.class);
        when(folder.getId()).thenReturn(folderId);
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
        // A13 — grant 메타가 ShareDto에 join되므로 명시.
        PermissionRow grant = grantRow(grantId, "user", subjectId, "edit");
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("user"),
            eq(subjectId), any(), any(), eq(actorId))).thenReturn(grant);
        // A16 — subject 표시명 단건 lookup. nested stubbing 회피: 외부 thenReturn 호출 전에 User mock을 미리 빌드.
        User aliceMock = userWithName("Alice");
        when(userRepository.findById(subjectId)).thenReturn(Optional.of(aliceMock));

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", subjectId)),
            "edit", null, "hello"
        );

        List<ShareDto> result = service.createShares(fileId, req, actorId);

        assertThat(result).hasSize(1);
        // A13: service 반환은 ShareDto record (permission grant join 완료 형태).
        ShareDto saved = result.get(0);
        assertThat(saved.fileId()).isEqualTo(fileId);
        assertThat(saved.folderId()).isNull();
        assertThat(saved.permissionId()).isEqualTo(grantId);
        assertThat(saved.sharedBy()).isEqualTo(actorId);
        assertThat(saved.message()).isEqualTo("hello");
        assertThat(saved.revokedAt()).isNull();
        // A13 — permissions join 결과.
        assertThat(saved.subjectType()).isEqualTo("user");
        assertThat(saved.subjectId()).isEqualTo(subjectId);
        assertThat(saved.preset()).isEqualTo("edit");
        // A16 — user displayName이 subjectName으로 join.
        assertThat(saved.subjectName()).isEqualTo("Alice");

        verify(shareRepository, times(1)).saveAndFlush(any(Share.class));

        ArgumentCaptor<ShareCreatedEvent> evCaptor = ArgumentCaptor.forClass(ShareCreatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(evCaptor.capture());
        ShareCreatedEvent ev = evCaptor.getValue();
        assertThat(ev.shareId()).isEqualTo(saved.id());
        assertThat(ev.fileId()).isEqualTo(fileId);
        assertThat(ev.permissionId()).isEqualTo(grantId);
        assertThat(ev.subjectType()).isEqualTo("user");
        assertThat(ev.subjectId()).isEqualTo(subjectId);
        assertThat(ev.preset().wire()).isEqualTo("edit");
        assertThat(ev.message()).isEqualTo("hello");
    }

    @Test
    void createShares_everyoneSubject_passesNullSubjectIdToGrantPermission() {
        PermissionRow grant = grantRow(UUID.randomUUID(), "everyone", null, "read");
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("everyone"),
            eq(null), any(), any(), eq(actorId))).thenReturn(grant);

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("everyone", UUID.randomUUID())),  // id 무시
            "read", null, null
        );

        List<ShareDto> result = service.createShares(fileId, req, actorId);

        verify(permissionService).grantPermission(eq("file"), eq(fileId), eq("everyone"),
            eq(null), any(), any(), eq(actorId));
        // A16 — everyone subject → subjectName=null. user/dept lookup 미호출 (resolveSubjectName 가드).
        assertThat(result).hasSize(1);
        assertThat(result.get(0).subjectName()).isNull();
        verify(userRepository, never()).findById(any());
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    void createShares_departmentSubject_resolvesDeptName() {
        // A16 — subject_type='department' 분기는 departmentRepository.findById로 dept name resolve.
        UUID deptId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        PermissionRow grant = grantRow(grantId, "department", deptId, "read");
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("department"),
            eq(deptId), any(), any(), eq(actorId))).thenReturn(grant);
        // nested stubbing 회피.
        Department engMock = deptWithName("Engineering");
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(engMock));

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("department", deptId)),
            "read", null, null
        );

        List<ShareDto> result = service.createShares(fileId, req, actorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).subjectType()).isEqualTo("department");
        assertThat(result.get(0).subjectName()).isEqualTo("Engineering");
        // user repository는 dept share에서 호출되지 않음.
        verify(userRepository, never()).findById(any());
    }

    @Test
    void createShares_teamSubject_resolvesTeamName() {
        // A16 — subject_type='team' 분기는 teamRepository.findById로 team name resolve.
        UUID teamId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        PermissionRow grant = grantRow(grantId, "team", teamId, "read");
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("team"),
            eq(teamId), any(), any(), eq(actorId))).thenReturn(grant);
        // nested stubbing 회피.
        Team teamMock = teamWithName("ProjectAlpha");
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(teamMock));

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("team", teamId)),
            "read", null, null
        );

        List<ShareDto> result = service.createShares(fileId, req, actorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).subjectType()).isEqualTo("team");
        assertThat(result.get(0).subjectName()).isEqualTo("ProjectAlpha");
        // user/department repositories는 team share에서 호출되지 않음.
        verify(userRepository, never()).findById(any());
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    void createShares_userSubjectLookupMiss_returnsNullSubjectName() {
        // A16 — soft-delete 등으로 user를 찾지 못해도 share 생성은 계속 (subjectName=null fallback).
        UUID subjectId = UUID.randomUUID();
        PermissionRow grant = grantRow(UUID.randomUUID(), "user", subjectId, "read");
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(permissionService.grantPermission(eq("file"), eq(fileId), eq("user"),
            eq(subjectId), any(), any(), eq(actorId))).thenReturn(grant);
        when(userRepository.findById(subjectId)).thenReturn(Optional.empty());

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", subjectId)),
            "read", null, null
        );

        List<ShareDto> result = service.createShares(fileId, req, actorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).subjectName()).isNull();
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

        List<ShareDto> result = service.createShares(fileId, req, actorId);

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

        List<ShareDto> result = service.createShares(fileId, req, actorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).expiresAt()).isEqualTo(future);
        assertThat(result.get(0).message()).isEqualTo(exact1000);
    }

    // ── A12 — createFolderShares (folder variant) ────────────────────────

    @Test
    void createFolderShares_throwsNotFound_whenFolderMissing() {
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.empty());
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "read", null, null
        );

        assertThatThrownBy(() -> service.createFolderShares(folderId, req, actorId))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(permissionService, never()).grantPermission(
            any(), any(), any(), any(), any(), any(), any());
        verify(shareRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createFolderShares_rejectsEmptySubjects() {
        ShareCreateRequest req = new ShareCreateRequest(List.of(), "read", null, null);

        assertThatThrownBy(() -> service.createFolderShares(folderId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subjects");

        verify(folderRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void createFolderShares_rejectsPresetShare_v5CheckIncompatible() {
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "share", null, null
        );

        assertThatThrownBy(() -> service.createFolderShares(folderId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("share");
    }

    @Test
    void createFolderShares_rejectsExpiresAtInPast() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "read", past, null
        );

        assertThatThrownBy(() -> service.createFolderShares(folderId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("future");
    }

    @Test
    void createFolderShares_rejectsMessageOver1000Chars() {
        String tooLong = "x".repeat(1001);
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "read", null, tooLong
        );

        assertThatThrownBy(() -> service.createFolderShares(folderId, req, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1000");
    }

    @Test
    void createFolderShares_singleUserSubject_passesFolderTypeAndSetsXorInvariant() {
        UUID subjectId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        // A13 — folder 변형도 grant 메타가 DTO에 join.
        PermissionRow grant = grantRow(grantId, "user", subjectId, "edit");
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(permissionService.grantPermission(eq("folder"), eq(folderId), eq("user"),
            eq(subjectId), any(), any(), eq(actorId))).thenReturn(grant);

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", subjectId)),
            "edit", null, "folder hello"
        );

        List<ShareDto> result = service.createFolderShares(folderId, req, actorId);

        assertThat(result).hasSize(1);
        ShareDto saved = result.get(0);
        // V6 XOR — folderId NOT NULL, fileId NULL.
        assertThat(saved.folderId()).isEqualTo(folderId);
        assertThat(saved.fileId()).isNull();
        assertThat(saved.permissionId()).isEqualTo(grantId);
        assertThat(saved.sharedBy()).isEqualTo(actorId);
        assertThat(saved.message()).isEqualTo("folder hello");
        // A13 — folder 변형도 join 동일 적용.
        assertThat(saved.subjectType()).isEqualTo("user");
        assertThat(saved.subjectId()).isEqualTo(subjectId);
        assertThat(saved.preset()).isEqualTo("edit");

        // PermissionService에 nodeType="folder" 전달 (file path와 분기 확인).
        verify(permissionService).grantPermission(eq("folder"), eq(folderId), eq("user"),
            eq(subjectId), any(), any(), eq(actorId));
        verify(shareRepository, times(1)).saveAndFlush(any(Share.class));

        ArgumentCaptor<ShareCreatedEvent> evCaptor = ArgumentCaptor.forClass(ShareCreatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(evCaptor.capture());
        ShareCreatedEvent ev = evCaptor.getValue();
        // file/folder XOR invariant in event payload.
        assertThat(ev.fileId()).isNull();
        assertThat(ev.folderId()).isEqualTo(folderId);
        assertThat(ev.permissionId()).isEqualTo(grantId);
        assertThat(ev.subjectType()).isEqualTo("user");
        assertThat(ev.subjectId()).isEqualTo(subjectId);
        assertThat(ev.preset().wire()).isEqualTo("edit");
    }

    @Test
    void createFolderShares_multipleSubjects_callsGrantPermissionPerSubject() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        PermissionRow grant1 = grantRow(UUID.randomUUID());
        PermissionRow grant2 = grantRow(UUID.randomUUID());
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(permissionService.grantPermission(eq("folder"), eq(folderId), eq("user"),
            eq(s1), any(), any(), eq(actorId))).thenReturn(grant1);
        when(permissionService.grantPermission(eq("folder"), eq(folderId), eq("department"),
            eq(s2), any(), any(), eq(actorId))).thenReturn(grant2);

        ShareCreateRequest req = new ShareCreateRequest(
            List.of(
                new ShareCreateRequest.Subject("user", s1),
                new ShareCreateRequest.Subject("department", s2)
            ),
            "upload", null, null
        );

        List<ShareDto> result = service.createFolderShares(folderId, req, actorId);

        assertThat(result).hasSize(2);
        verify(permissionService, times(2)).grantPermission(
            eq("folder"), eq(folderId), any(), any(), any(), any(), any());
        verify(shareRepository, times(2)).saveAndFlush(any(Share.class));
        verify(eventPublisher, times(2)).publishEvent(any(ShareCreatedEvent.class));
    }

    @Test
    void revokeShare_folderShare_publishesEventWithFolderIdAndNullFileId() {
        UUID shareId = UUID.randomUUID();
        UUID sharePermissionId = UUID.randomUUID();
        UUID shareFolderId = UUID.randomUUID();
        UUID sharedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");

        Share share = mock(Share.class);
        when(share.getFileId()).thenReturn(null);              // folder share — XOR
        when(share.getFolderId()).thenReturn(shareFolderId);
        when(share.getPermissionId()).thenReturn(sharePermissionId);
        when(share.getSharedBy()).thenReturn(sharedBy);
        when(share.getCreatedAt()).thenReturn(createdAt);
        when(shareRepository.lockByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.of(share));

        service.revokeShare(shareId, actorId);

        verify(permissionRepository).deleteById(sharePermissionId);

        ArgumentCaptor<ShareRevokedEvent> evCaptor = ArgumentCaptor.forClass(ShareRevokedEvent.class);
        verify(eventPublisher).publishEvent(evCaptor.capture());
        ShareRevokedEvent ev = evCaptor.getValue();
        assertThat(ev.fileId()).isNull();
        assertThat(ev.folderId()).isEqualTo(shareFolderId);
        assertThat(ev.permissionId()).isEqualTo(sharePermissionId);
    }

    /**
     * PermissionRow ctor가 package-protected — mock으로 우회.
     * A13 — service.createShares는 ShareDto.from(share, grant)에서 grant의 subjectType/subjectId/preset도 읽음.
     */
    private PermissionRow grantRow(UUID id) {
        return grantRow(id, "user", UUID.randomUUID(), "read");
    }

    private PermissionRow grantRow(UUID id, String subjectType, UUID subjectId, String preset) {
        PermissionRow row = mock(PermissionRow.class);
        when(row.getId()).thenReturn(id);
        when(row.getSubjectType()).thenReturn(subjectType);
        when(row.getSubjectId()).thenReturn(subjectId);
        when(row.getPreset()).thenReturn(preset);
        return row;
    }

    /** A16 — User mock with displayName for subjectName lookup tests. */
    private static User userWithName(String displayName) {
        User u = mock(User.class);
        when(u.getDisplayName()).thenReturn(displayName);
        return u;
    }

    /** A16 — Department mock with name for subjectName lookup tests. */
    private static Department deptWithName(String name) {
        Department d = mock(Department.class);
        when(d.getName()).thenReturn(name);
        return d;
    }

    /** A16 — Team mock with name for subjectName lookup tests. */
    private static Team teamWithName(String name) {
        Team t = mock(Team.class);
        when(t.getName()).thenReturn(name);
        return t;
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
        assertThat(ev.folderId()).isNull();   // A12 — file path XOR invariant
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

    // ── SHARE_EXPIRED — expireShare ──────────────────────────────────────────

    @Test
    void expireShare_throwsNotFound_whenShareMissingOrAlreadyRevoked() {
        UUID shareId = UUID.randomUUID();
        when(shareRepository.lockByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.empty());

        // race-safe: 사용자가 직접 revoke 직전이면 동일 NotFound로 처리 → cron이 swallow.
        assertThatThrownBy(() -> service.expireShare(shareId))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(permissionRepository, never()).deleteById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void expireShare_setsRevokedByNull_deletesPermission_publishesShareExpiredEvent_fileShare() {
        UUID shareId = UUID.randomUUID();
        UUID sharePermissionId = UUID.randomUUID();
        UUID shareFileId = UUID.randomUUID();
        UUID sharedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-04-30T23:59:59Z");

        Share share = mock(Share.class);
        when(share.getFileId()).thenReturn(shareFileId);
        when(share.getFolderId()).thenReturn(null);
        when(share.getPermissionId()).thenReturn(sharePermissionId);
        when(share.getSharedBy()).thenReturn(sharedBy);
        when(share.getCreatedAt()).thenReturn(createdAt);
        when(share.getExpiresAt()).thenReturn(expiresAt);
        when(share.getMessage()).thenReturn("expired msg");
        when(shareRepository.lockByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.of(share));

        service.expireShare(shareId);

        // 시스템 트리거 — revoked_by=NULL 명시.
        verify(share).setRevokedAt(any());
        verify(share).setRevokedBy(null);
        verify(shareRepository).saveAndFlush(share);
        verify(permissionRepository).deleteById(sharePermissionId);
        verify(permissionService, never()).revokePermission(any(), any());

        ArgumentCaptor<ShareExpiredEvent> evCaptor = ArgumentCaptor.forClass(ShareExpiredEvent.class);
        verify(eventPublisher).publishEvent(evCaptor.capture());
        ShareExpiredEvent ev = evCaptor.getValue();
        assertThat(ev.shareId()).isEqualTo(shareId);
        assertThat(ev.fileId()).isEqualTo(shareFileId);
        assertThat(ev.folderId()).isNull();
        assertThat(ev.permissionId()).isEqualTo(sharePermissionId);
        assertThat(ev.originalSharedBy()).isEqualTo(sharedBy);
        assertThat(ev.originalCreatedAt()).isEqualTo(createdAt);
        assertThat(ev.originalExpiresAt()).isEqualTo(expiresAt);
        assertThat(ev.originalMessage()).isEqualTo("expired msg");

        // ShareRevokedEvent는 절대 발행하지 않음 — 의미론 분리.
        verify(eventPublisher, never()).publishEvent(any(ShareRevokedEvent.class));
    }

    @Test
    void expireShare_folderShare_publishesEventWithFolderIdAndNullFileId() {
        UUID shareId = UUID.randomUUID();
        UUID sharePermissionId = UUID.randomUUID();
        UUID shareFolderId = UUID.randomUUID();
        UUID sharedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-15T10:00:00Z");

        Share share = mock(Share.class);
        when(share.getFileId()).thenReturn(null);
        when(share.getFolderId()).thenReturn(shareFolderId);
        when(share.getPermissionId()).thenReturn(sharePermissionId);
        when(share.getSharedBy()).thenReturn(sharedBy);
        when(share.getCreatedAt()).thenReturn(createdAt);
        when(shareRepository.lockByIdAndRevokedAtIsNull(shareId)).thenReturn(Optional.of(share));

        service.expireShare(shareId);

        ArgumentCaptor<ShareExpiredEvent> evCaptor = ArgumentCaptor.forClass(ShareExpiredEvent.class);
        verify(eventPublisher).publishEvent(evCaptor.capture());
        ShareExpiredEvent ev = evCaptor.getValue();
        assertThat(ev.fileId()).isNull();
        assertThat(ev.folderId()).isEqualTo(shareFolderId);
        assertThat(ev.permissionId()).isEqualTo(sharePermissionId);
    }

    @Test
    void expireShare_throwsIllegalArgument_whenShareIdNull() {
        assertThatThrownBy(() -> service.expireShare(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
