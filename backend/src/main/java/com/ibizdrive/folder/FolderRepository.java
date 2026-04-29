package com.ibizdrive.folder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Folder}.
 *
 * <p>A4.5(a4-folder-entity) — A4-data PR #6에서 deferred 처리된 entity layer를 닫는 contract 최소화 세션:
 * <ul>
 *   <li>{@link #findByIdAndDeletedAtIsNull(UUID)} — 단일 활성 폴더 조회 (휴지통 제외)</li>
 *   <li>{@link #findByParentIdAndDeletedAtIsNull(UUID)} — 직계 자식 폴더 목록 (A4.7 tree endpoint base)</li>
 * </ul>
 *
 * <p>Pessimistic lock query / soft delete bulk update / 충돌 검사 query / 재귀 ancestor walker는
 * A4.6(FolderMutationService) 세션에서 controller/service 작성과 함께 추가 — 본 세션은 contract 최소화
 * (FileRepository와 동일 정책).
 *
 * <p>Soft delete된 폴더는 명시적으로 {@code DeletedAtIsNotNull} 메서드를 호출하거나 native query를
 * 사용해야 한다. {@code findById}는 휴지통 폴더도 반환하므로 application 레벨에서 사용하지 말 것.
 */
public interface FolderRepository extends JpaRepository<Folder, UUID> {

    Optional<Folder> findByIdAndDeletedAtIsNull(UUID id);

    List<Folder> findByParentIdAndDeletedAtIsNull(UUID parentId);
}
