package com.ibizdrive.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FileItem}.
 *
 * <p>A4.2 부분 진행 — 본 세션에서는 entity persistence와 가장 빈번한 lookup만 제공:
 * <ul>
 *   <li>{@link #findByIdAndDeletedAtIsNull(UUID)} — 단일 활성 파일 조회 (휴지통 제외)</li>
 *   <li>{@link #findByFolderIdAndDeletedAtIsNull(UUID)} — 폴더 내 파일 목록 (A4.5 list endpoint)</li>
 * </ul>
 *
 * <p>Pessimistic lock query / soft delete bulk update / 충돌 검사 query 등 mutation 보조 메서드는
 * A4.5(a4-crud) 세션에서 controller/service 작성과 함께 추가 — 본 세션은 contract 최소화.
 */
public interface FileRepository extends JpaRepository<FileItem, UUID> {

    Optional<FileItem> findByIdAndDeletedAtIsNull(UUID id);

    List<FileItem> findByFolderIdAndDeletedAtIsNull(UUID folderId);
}
