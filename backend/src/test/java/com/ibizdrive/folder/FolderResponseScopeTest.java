package com.ibizdrive.folder;

import com.ibizdrive.folder.dto.FolderDto;
import com.ibizdrive.folder.dto.FolderItemDto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FolderDto#from(Folder)} / {@link FolderItemDto#fromFolder(Folder)} scope 노출 단위 테스트
 * — Plan A Task 27 (team-centric pivot).
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.3, §5.5.
 * 응답 DTO에 임베드된 {@code scope: { type, id }} 블록이 entity의 {@link Folder#getScopeType()} /
 * {@link Folder#getScopeId()}를 그대로 wire dbValue로 반영하는지 검증한다.
 *
 * <p>workspaceName은 본 Plan A 범위에서 deferred — 본 테스트는 type/id만 확인.
 *
 * <p>순수 단위 테스트 — Spring context 없이 Folder 인스턴스를 직접 만들고 {@code assignScope}
 * 후 DTO factory를 호출, AssertJ로 단언한다 (FolderScopeTest와 동일 패턴).
 */
class FolderResponseScopeTest {

    @Test
    void folderDto_includesDepartmentScope() {
        Folder folder = newFolder();
        UUID scopeId = UUID.randomUUID();
        folder.assignScope(ScopeType.DEPARTMENT, scopeId);

        FolderDto dto = FolderDto.from(folder);

        assertThat(dto.scope()).isNotNull();
        assertThat(dto.scope().type()).isEqualTo("department");
        assertThat(dto.scope().id()).isEqualTo(scopeId);
    }

    @Test
    void folderDto_includesTeamScope() {
        Folder folder = newFolder();
        UUID scopeId = UUID.randomUUID();
        folder.assignScope(ScopeType.TEAM, scopeId);

        FolderDto dto = FolderDto.from(folder);

        assertThat(dto.scope()).isNotNull();
        assertThat(dto.scope().type()).isEqualTo("team");
        assertThat(dto.scope().id()).isEqualTo(scopeId);
    }

    @Test
    void folderItemDto_fromFolder_includesScope() {
        Folder folder = newFolder();
        UUID scopeId = UUID.randomUUID();
        folder.assignScope(ScopeType.TEAM, scopeId);

        FolderItemDto item = FolderItemDto.fromFolder(folder);

        assertThat(item.scope()).isNotNull();
        assertThat(item.scope().type()).isEqualTo("team");
        assertThat(item.scope().id()).isEqualTo(scopeId);
    }

    @Test
    void folderItemDto_fromFolder_includesDepartmentScope() {
        Folder folder = newFolder();
        UUID scopeId = UUID.randomUUID();
        folder.assignScope(ScopeType.DEPARTMENT, scopeId);

        FolderItemDto item = FolderItemDto.fromFolder(folder);

        assertThat(item.scope()).isNotNull();
        assertThat(item.scope().type()).isEqualTo("department");
        assertThat(item.scope().id()).isEqualTo(scopeId);
    }

    private static Folder newFolder() {
        Folder f = new Folder();
        f.setId(UUID.randomUUID());
        f.setName("docs");
        f.setNormalizedName("docs");
        f.setSlug("docs");
        f.setOwnerId(UUID.randomUUID());
        f.setAuditLevel("standard");
        return f;
    }
}
