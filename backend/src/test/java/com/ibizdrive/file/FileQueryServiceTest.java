package com.ibizdrive.file;

import com.ibizdrive.file.dto.FileDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Phase B P2 — {@link FileQueryService#loadDetail(UUID)} unit test (Mockito).
 *
 * <p>커버리지 (3 case):
 * <ol>
 *   <li>200 — 활성 파일을 {@link FileDto}로 매핑하여 반환.</li>
 *   <li>404 (없음) — repository가 {@link Optional#empty()}일 때 {@link FileNotFoundException}.</li>
 *   <li>404 (soft-deleted) — {@code findByIdAndDeletedAtIsNull}이 비어 있어 동일 처리.
 *       repository 메서드 시그니처가 active만 반환하므로 case 2와 동일 경로지만 의도 분리 검증.</li>
 * </ol>
 *
 * <p>권한 가드는 controller layer SpEL 책임 — 본 service test 비대상 ({@link FolderQueryServiceTest}와 동일 정책).
 */
@ExtendWith(MockitoExtension.class)
class FileQueryServiceTest {

    @Mock private FileRepository fileRepository;
    @InjectMocks private FileQueryService service;

    @Test
    void loadDetail_returnsFileDto_whenActiveFileExists() {
        UUID id = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        FileItem entity = FileTestFixtures.activeFile(id, folderId, ownerId, "보고서.pdf", 1234L,
            Instant.parse("2026-05-07T00:00:00Z"));
        when(fileRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(entity));

        FileDto dto = service.loadDetail(id);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.folderId()).isEqualTo(folderId);
        assertThat(dto.name()).isEqualTo("보고서.pdf");
        assertThat(dto.sizeBytes()).isEqualTo(1234L);
        assertThat(dto.ownerId()).isEqualTo(ownerId);
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
        // findByIdAndDeletedAtIsNull은 active(deletedAt=null)만 반환 — soft-deleted 시 동일하게 empty.
        // case 2와 시그니처상 동일 경로지만, 휴지통 파일 대상 호출 시에도 404임을 명시 검증.
        UUID softDeletedId = UUID.randomUUID();
        when(fileRepository.findByIdAndDeletedAtIsNull(softDeletedId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadDetail(softDeletedId))
            .isInstanceOf(FileNotFoundException.class);
    }
}
