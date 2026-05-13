package com.ibizdrive.file;

import com.ibizdrive.file.dto.FileDetailResponse;
import com.ibizdrive.file.dto.SubjectGrantBriefDto;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.dto.BreadcrumbCrumbDto;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.permission.dto.PermissionDto;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase B P2 + P_panel-A — {@link FileQueryService#loadDetail(UUID)} unit test (Mockito).
 *
 * <p>커버리지:
 * <ol>
 *   <li>200 — 활성 파일 + owner + 빈 sharedWith + folderPath chain 단일 노드</li>
 *   <li>200 — owner soft-delete 시 null fallback</li>
 *   <li>200 — sharedWith subjectName resolve (user/everyone 혼합)</li>
 *   <li>200 — folderPath 중첩 chain (root → parent → leaf)</li>
 *   <li>200 — folderPath soft-deleted 폴더 도달 시 chain break</li>
 *   <li>404 (없음) — repository empty</li>
 *   <li>404 (soft-deleted) — 동일 경로</li>
 * </ol>
 *
 * <p>권한 가드는 controller layer SpEL 책임 — 본 service test 비대상.
 */
@ExtendWith(MockitoExtension.class)
class FileQueryServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private UserRepository userRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private PermissionService permissionService;
    @InjectMocks private FileQueryService service;

    private static FileItem activeFile(UUID id, UUID folderId, UUID ownerId) {
        return FileTestFixtures.activeFile(id, folderId, ownerId, "보고서.pdf", 1234L,
            Instant.parse("2026-05-07T00:00:00Z"));
    }

    private static User user(UUID id, String name, String email) {
        return new User(id, email, name, "hash", com.ibizdrive.user.Role.MEMBER,
            true, false, java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }

    private static Folder folder(UUID id, UUID parentId, String name) {
        // Folder() 생성자는 protected (JPA용). cross-package 테스트에서는 mock으로 우회 —
        // buildFolderPath는 id/parentId/name/slug 4개 getter만 사용한다.
        Folder f = mock(Folder.class);
        lenient().when(f.getId()).thenReturn(id);
        lenient().when(f.getParentId()).thenReturn(parentId);
        lenient().when(f.getName()).thenReturn(name);
        lenient().when(f.getSlug()).thenReturn(name);
        return f;
    }

    @Test
    void loadDetail_returns_basicEnvelope_withOwnerAndPathSingleNode() {
        UUID id = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(fileRepository.findByIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.of(activeFile(id, folderId, ownerId)));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(user(ownerId, "홍길동", "h@test")));
        lenient().when(permissionService.listPermissions(any(), any())).thenReturn(List.of());
        Folder root = folder(folderId, null, "공용");
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(root));

        FileDetailResponse res = service.loadDetail(id);

        assertThat(res.file().id()).isEqualTo(id);
        assertThat(res.file().name()).isEqualTo("보고서.pdf");
        assertThat(res.owner()).isNotNull();
        assertThat(res.owner().displayName()).isEqualTo("홍길동");
        assertThat(res.owner().email()).isEqualTo("h@test");
        assertThat(res.sharedWith()).isEmpty();
        assertThat(res.folderPath()).extracting(BreadcrumbCrumbDto::name).containsExactly("공용");
    }

    @Test
    void loadDetail_owner_nullWhenUserSoftDeletedOrMissing() {
        // user_id is FK ON DELETE RESTRICT in schema; but if user soft-deleted, findById는 여전히 row를 반환할 수
        // 있다 (User entity에 soft delete column 존재). 본 케이스는 미존재(`findById empty`) 시 시나리오 — service는
        // 안전하게 null로 응답해 FE placeholder 처리.
        UUID id = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(fileRepository.findByIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.of(activeFile(id, folderId, ownerId)));
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());
        lenient().when(permissionService.listPermissions(any(), any())).thenReturn(List.of());
        Folder root = folder(folderId, null, "공용");
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(root));

        FileDetailResponse res = service.loadDetail(id);

        assertThat(res.owner()).isNull();
    }

    @Test
    void loadDetail_sharedWith_mapsPermissionDtosWithEveryoneRelabel() {
        // PermissionService.listPermissions의 결과를 SubjectGrantBriefDto로 매핑. user는 subjectName 통과,
        // everyone은 "전체" 라벨 치환.
        UUID id = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID userSubj = UUID.randomUUID();
        when(fileRepository.findByIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.of(activeFile(id, folderId, ownerId)));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(user(ownerId, "owner", "o@t")));
        Folder rootFolder2 = folder(folderId, null, "root");
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(rootFolder2));
        when(permissionService.listPermissions("file", id)).thenReturn(List.of(
            new PermissionDto(UUID.randomUUID(), "file", id, "user", userSubj,
                "read", UUID.randomUUID(), null, Instant.parse("2026-05-01T00:00:00Z"), "김영업"),
            new PermissionDto(UUID.randomUUID(), "file", id, "everyone", null,
                "read", UUID.randomUUID(), null, Instant.parse("2026-05-01T00:00:00Z"), null)
        ));

        FileDetailResponse res = service.loadDetail(id);

        assertThat(res.sharedWith()).hasSize(2);
        SubjectGrantBriefDto first = res.sharedWith().get(0);
        assertThat(first.subjectType()).isEqualTo("user");
        assertThat(first.subjectId()).isEqualTo(userSubj);
        assertThat(first.subjectName()).isEqualTo("김영업");
        assertThat(first.preset()).isEqualTo("read");

        SubjectGrantBriefDto second = res.sharedWith().get(1);
        assertThat(second.subjectType()).isEqualTo("everyone");
        assertThat(second.subjectId()).isNull();
        assertThat(second.subjectName()).isEqualTo("전체");  // everyone relabel
    }

    @Test
    void loadDetail_folderPath_walksUpToRootAndReturnsRootFirst() {
        // 체인: leaf(L) → mid(M) → root(R). 응답은 root → mid → leaf 순.
        UUID id = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID midId = UUID.randomUUID();
        UUID leafId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(fileRepository.findByIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.of(activeFile(id, leafId, ownerId)));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(user(ownerId, "owner", "o@t")));
        lenient().when(permissionService.listPermissions(any(), any())).thenReturn(List.of());
        Folder leaf = folder(leafId, midId, "계약서");
        Folder mid = folder(midId, rootId, "영업팀");
        Folder root3 = folder(rootId, null, "회사");
        when(folderRepository.findByIdAndDeletedAtIsNull(leafId)).thenReturn(Optional.of(leaf));
        when(folderRepository.findByIdAndDeletedAtIsNull(midId)).thenReturn(Optional.of(mid));
        when(folderRepository.findByIdAndDeletedAtIsNull(rootId)).thenReturn(Optional.of(root3));

        FileDetailResponse res = service.loadDetail(id);

        assertThat(res.folderPath()).extracting(BreadcrumbCrumbDto::name)
            .containsExactly("회사", "영업팀", "계약서");
        assertThat(res.folderPath()).extracting(BreadcrumbCrumbDto::id)
            .containsExactly(rootId, midId, leafId);
    }

    @Test
    void loadDetail_folderPath_breaksAtSoftDeletedAncestor() {
        // leaf → midSoftDeleted(empty Optional) — chain은 leaf 까지만, midDeleted/root는 미포함.
        UUID id = UUID.randomUUID();
        UUID leafId = UUID.randomUUID();
        UUID midId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(fileRepository.findByIdAndDeletedAtIsNull(id))
            .thenReturn(Optional.of(activeFile(id, leafId, ownerId)));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(user(ownerId, "owner", "o@t")));
        lenient().when(permissionService.listPermissions(any(), any())).thenReturn(List.of());
        Folder leaf2 = folder(leafId, midId, "계약서");
        when(folderRepository.findByIdAndDeletedAtIsNull(leafId)).thenReturn(Optional.of(leaf2));
        when(folderRepository.findByIdAndDeletedAtIsNull(midId)).thenReturn(Optional.empty());

        FileDetailResponse res = service.loadDetail(id);

        // Break: leaf만 포함 (mid부터는 chain 미포함). FE는 부분 경로를 placeholder 처리.
        assertThat(res.folderPath()).extracting(BreadcrumbCrumbDto::name)
            .containsExactly("계약서");
    }

    @Test
    void loadDetail_throwsFileNotFound_whenIdMissing() {
        UUID id = UUID.randomUUID();
        when(fileRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadDetail(id))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    void loadDetail_throwsFileNotFound_whenFileSoftDeleted() {
        UUID softDeletedId = UUID.randomUUID();
        when(fileRepository.findByIdAndDeletedAtIsNull(softDeletedId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadDetail(softDeletedId))
            .isInstanceOf(FileNotFoundException.class);
    }
}
