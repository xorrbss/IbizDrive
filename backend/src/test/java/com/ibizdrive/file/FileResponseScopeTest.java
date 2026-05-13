package com.ibizdrive.file;

import com.ibizdrive.file.dto.FileDto;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.folder.dto.FolderItemDto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FileDto#from(FileItem)} / {@link FolderItemDto#fromFile(FileItem)} scope 노출 단위 테스트
 * — Plan A Task 27 (team-centric pivot).
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.3, §5.5.
 * 파일 응답 DTO에 임베드된 {@code scope: { type, id }} 블록이 entity의 {@link FileItem#getScopeType()} /
 * {@link FileItem#getScopeId()}를 그대로 wire dbValue로 반영하는지 검증한다. 파일은 부모 폴더의
 * scope를 상속(Plan A Task 26)하므로 folder/file 응답이 동일한 scope discriminator를 노출.
 *
 * <p>순수 단위 테스트 — Spring context 없이 FileItem 인스턴스를 직접 만들고 {@code assignScope}
 * 후 DTO factory를 호출, AssertJ로 단언한다 (FileScopeTest와 동일 패턴).
 */
class FileResponseScopeTest {

    @Test
    void fileDto_includesDepartmentScope() {
        FileItem file = newFile();
        UUID scopeId = UUID.randomUUID();
        file.assignScope(ScopeType.DEPARTMENT, scopeId);

        FileDto dto = FileDto.from(file);

        assertThat(dto.scope()).isNotNull();
        assertThat(dto.scope().type()).isEqualTo("department");
        assertThat(dto.scope().id()).isEqualTo(scopeId);
    }

    @Test
    void fileDto_includesTeamScope() {
        FileItem file = newFile();
        UUID scopeId = UUID.randomUUID();
        file.assignScope(ScopeType.TEAM, scopeId);

        FileDto dto = FileDto.from(file);

        assertThat(dto.scope()).isNotNull();
        assertThat(dto.scope().type()).isEqualTo("team");
        assertThat(dto.scope().id()).isEqualTo(scopeId);
    }

    @Test
    void folderItemDto_fromFile_includesScope() {
        FileItem file = newFile();
        UUID scopeId = UUID.randomUUID();
        file.assignScope(ScopeType.TEAM, scopeId);

        FolderItemDto item = FolderItemDto.fromFile(file, null, null);

        assertThat(item.scope()).isNotNull();
        assertThat(item.scope().type()).isEqualTo("team");
        assertThat(item.scope().id()).isEqualTo(scopeId);
    }

    @Test
    void folderItemDto_fromFile_includesDepartmentScope() {
        FileItem file = newFile();
        UUID scopeId = UUID.randomUUID();
        file.assignScope(ScopeType.DEPARTMENT, scopeId);

        FolderItemDto item = FolderItemDto.fromFile(file, null, null);

        assertThat(item.scope()).isNotNull();
        assertThat(item.scope().type()).isEqualTo("department");
        assertThat(item.scope().id()).isEqualTo(scopeId);
    }

    private static FileItem newFile() {
        FileItem f = new FileItem();
        f.setId(UUID.randomUUID());
        f.setFolderId(UUID.randomUUID());
        f.setName("report.pdf");
        f.setNormalizedName("report.pdf");
        f.setOwnerId(UUID.randomUUID());
        f.setSizeBytes(1024L);
        f.setMimeType("application/pdf");
        return f;
    }
}
