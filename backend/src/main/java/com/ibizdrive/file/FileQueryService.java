package com.ibizdrive.file;

import com.ibizdrive.file.dto.FileDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Phase B P2 읽기 전용 service — frontend 파일 상세(RightPanel) wiring 용도.
 *
 * <p>{@link FolderQueryService}와 동일한 책임 분리 패턴: mutation은 {@link FileMutationService},
 * 본 service는 fetch + DTO 매핑만. Audit 미발행 (read-only는 audit 비대상 — docs/03 §4 노출 정책).
 *
 * <p>visibility/권한 게이트는 controller SpEL이 담당. service는 active(soft-delete 제외) 파일만 반환하며,
 * 부재/휴지통 파일은 모두 {@link FileNotFoundException}으로 통일하여 404 envelope으로 매핑된다.
 */
@Service
public class FileQueryService {

    private final FileRepository fileRepository;

    public FileQueryService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /**
     * 활성 파일 상세를 {@link FileDto}로 반환. 부재 또는 soft-delete 시 {@link FileNotFoundException}.
     */
    @Transactional(readOnly = true)
    public FileDto loadDetail(UUID id) {
        FileItem entity = fileRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new FileNotFoundException("file not found: " + id));
        return FileDto.from(entity);
    }
}
