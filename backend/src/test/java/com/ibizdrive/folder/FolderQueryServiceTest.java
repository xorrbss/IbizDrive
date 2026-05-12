package com.ibizdrive.folder;

import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.dto.BreadcrumbCrumbDto;
import com.ibizdrive.folder.dto.FolderDetailResponse;
import com.ibizdrive.folder.dto.FolderNodeDto;
import com.ibizdrive.permission.PermissionRepository;
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
import static org.mockito.Mockito.when;

/**
 * Phase A — {@link FolderQueryService} unit test (Mockito).
 *
 * <p>커버리지:
 * <ul>
 *   <li>{@link FolderQueryService#loadTree()} — flat 리스트로부터 nested 트리 조립, 빈 입력</li>
 *   <li>{@link FolderQueryService#loadDetail(UUID)} — root/중첩 breadcrumb, not-found</li>
 * </ul>
 *
 * <p>repository는 mock — soft-delete 필터링은 repository 책임이므로 본 service test는
 * 트리 조립 / breadcrumb 워킹 로직만 검증.
 */
@ExtendWith(MockitoExtension.class)
class FolderQueryServiceTest {

    @Mock private FolderRepository folderRepository;
    @Mock private FileRepository fileRepository;
    @Mock private PermissionRepository permissionRepository;
    @InjectMocks private FolderQueryService service;

    private static Folder folder(UUID id, UUID parentId, String name, String slug) {
        Folder f = new Folder();
        f.setId(id);
        f.setParentId(parentId);
        f.setName(name);
        f.setSlug(slug);
        f.setNormalizedName(name);
        f.setOwnerId(UUID.randomUUID());
        f.setAuditLevel("standard");
        Instant now = Instant.parse("2026-05-01T00:00:00Z");
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        f.assignScope(com.ibizdrive.folder.ScopeType.DEPARTMENT, java.util.UUID.randomUUID());
        return f;
    }

    // ─────────────────── loadTree ───────────────────

    @Test
    void loadTree_emptyWhenNoFolders() {
        when(folderRepository.findAllByDeletedAtIsNull()).thenReturn(List.of());
        assertThat(service.loadTree()).isEmpty();
    }

    @Test
    void loadTree_assemblesNestedTreeFromFlatList() {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        when(folderRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(
            folder(r1, null, "영업팀", "영업팀"),
            folder(r2, null, "인사팀", "인사팀"),
            folder(c1, r1, "계약서", "계약서")
        ));

        List<FolderNodeDto> tree = service.loadTree();

        // 두 top-level 폴더, 첫 번째에 자식 1개
        assertThat(tree).hasSize(2);
        FolderNodeDto root1 = tree.stream().filter(n -> n.id().equals(r1)).findFirst().orElseThrow();
        FolderNodeDto root2 = tree.stream().filter(n -> n.id().equals(r2)).findFirst().orElseThrow();
        assertThat(root1.children()).hasSize(1);
        assertThat(root1.children().get(0).id()).isEqualTo(c1);
        assertThat(root1.children().get(0).children()).isEmpty();
        assertThat(root2.children()).isEmpty();
    }

    @Test
    void loadTree_orphanFolderWithMissingParentIsExcluded() {
        // parent_id가 가리키는 폴더가 결과에 없으면 (예: race) 트리에 포함시키지 않음 — defensive.
        UUID r1 = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(folderRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(
            folder(r1, null, "영업팀", "영업팀"),
            folder(orphan, missing, "고아", "고아")
        ));

        List<FolderNodeDto> tree = service.loadTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo(r1);
    }

    // ─────────────────── loadDetail ───────────────────

    @Test
    void loadDetail_rootFolderHasSingleCrumb() {
        UUID id = UUID.randomUUID();
        Folder root = folder(id, null, "공용", "공용");
        when(folderRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(root));

        FolderDetailResponse res = service.loadDetail(id);

        assertThat(res.folder().id()).isEqualTo(id);
        assertThat(res.breadcrumb()).hasSize(1);
        BreadcrumbCrumbDto only = res.breadcrumb().get(0);
        assertThat(only.id()).isEqualTo(id);
        assertThat(only.name()).isEqualTo("공용");
        assertThat(only.slug()).isEqualTo("공용");
    }

    @Test
    void loadDetail_nestedFolderHasFullChainFromRoot() {
        UUID rootId = UUID.randomUUID();
        UUID midId = UUID.randomUUID();
        UUID leafId = UUID.randomUUID();
        Folder root = folder(rootId, null, "회사", "회사");
        Folder mid = folder(midId, rootId, "영업팀", "영업팀");
        Folder leaf = folder(leafId, midId, "계약서", "계약서");
        when(folderRepository.findByIdAndDeletedAtIsNull(leafId)).thenReturn(Optional.of(leaf));
        when(folderRepository.findByIdAndDeletedAtIsNull(midId)).thenReturn(Optional.of(mid));
        when(folderRepository.findByIdAndDeletedAtIsNull(rootId)).thenReturn(Optional.of(root));

        FolderDetailResponse res = service.loadDetail(leafId);

        // 순서: root → mid → leaf
        assertThat(res.breadcrumb()).extracting(BreadcrumbCrumbDto::id)
            .containsExactly(rootId, midId, leafId);
        assertThat(res.breadcrumb()).extracting(BreadcrumbCrumbDto::name)
            .containsExactly("회사", "영업팀", "계약서");
    }

    @Test
    void loadDetail_throwsFolderNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(folderRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadDetail(id))
            .isInstanceOf(FolderNotFoundException.class);
    }
}
