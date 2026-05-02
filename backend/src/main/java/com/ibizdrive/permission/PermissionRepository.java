package com.ibizdrive.permission;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PermissionRow}.
 *
 * <p>핵심 메서드 {@link #findEffective(UUID, String, UUID)}: 사용자가 주어진 리소스에 대해 보유한
 * <em>유효</em> grant 모두를 반환 (ADR #28 — grant 우선 lookup, 명시 deny 처리 없음).
 *
 * <p>"유효"의 정의 (A4 MVP 범위):
 * <ol>
 *   <li><strong>상속</strong>: file이 속한 폴더와 그 모든 조상 폴더(루트까지)에서 부여된 grant + file 자체에
 *       부여된 grant. 폴더 리소스의 경우는 폴더 자체와 조상.</li>
 *   <li><strong>subject 매칭</strong>: ({@code subject_type='user'} AND {@code subject_id=:userId})
 *       OR ({@code subject_type='everyone'})
 *       OR ({@code subject_type='department'} AND {@code subject_id} = 사용자의 직속 {@code users.department_id}).
 *       후자는 A16/ADR #36에서 활성화 — flat dept matching (LTREE 트리는 v1.x 이월). role 멤버십은 v1.x 이월
 *       (ADR #36 — schema impedance, role 변경 grant 무효화 위험).</li>
 *   <li><strong>만료</strong>: {@code expires_at IS NULL} OR {@code expires_at > NOW()}.</li>
 *   <li><strong>soft delete</strong>: 삭제된 폴더({@code folders.deleted_at IS NOT NULL})는 조상
 *       체인에서 제외 — 휴지통에 들어간 폴더는 권한 상속 경로에서 끊김.</li>
 * </ol>
 *
 * <p>구현은 PostgreSQL recursive CTE — 폴더 트리를 자식→부모로 거슬러 올라가며 권한 후보 노드 집합을
 * 만들고 권한 행과 join. 본 동작은 {@link com.ibizdrive.folder.V5MigrationIT}와 동일한
 * Testcontainers 기반 단위 테스트({@code PermissionRepositoryTest})에서 검증.
 *
 * <p>A4.3에서 {@code IbizDrivePermissionEvaluator} 내부가 본 메서드를 호출하도록 교체된다 — 본 세션
 * (A4-data 트랙)은 repository 계약만 제공.
 */
public interface PermissionRepository extends JpaRepository<PermissionRow, UUID> {

    /**
     * 주어진 사용자 × 리소스 조합에 대해 적용 가능한 활성 grant 행 목록.
     *
     * @param userId       사용자 식별자 (subject_type='user' 매칭). everyone grant는 userId와 무관하게 포함.
     * @param resourceType {@code "folder"} 또는 {@code "file"}.
     * @param resourceId   리소스 식별자. file이면 그 파일의 folder_id에서 시작해 조상 추적.
     * @return 매칭되는 {@link PermissionRow} 목록 (만료/soft-delete 제외).
     */
    @Query(value = """
        WITH RECURSIVE start_folder AS (
            SELECT CASE
                WHEN :resourceType = 'file' THEN
                    (SELECT folder_id FROM files
                     WHERE id = CAST(:resourceId AS uuid) AND deleted_at IS NULL)
                ELSE CAST(:resourceId AS uuid)
            END AS id
        ),
        ancestors AS (
            SELECT f.id, f.parent_id
            FROM folders f, start_folder s
            WHERE f.id = s.id AND f.deleted_at IS NULL

            UNION ALL

            SELECT f.id, f.parent_id
            FROM folders f
            INNER JOIN ancestors a ON f.id = a.parent_id
            WHERE f.deleted_at IS NULL
        ),
        candidates AS (
            -- file 리소스의 경우 file 자체에 부여된 grant도 후보에 포함
            SELECT CAST(:resourceId AS uuid) AS id, 'file'::text AS rt
            WHERE :resourceType = 'file'

            UNION ALL

            SELECT id, 'folder'::text AS rt FROM ancestors
        )
        SELECT p.id, p.resource_type, p.resource_id, p.subject_type, p.subject_id,
               p.preset, p.granted_by, p.expires_at, p.created_at
        FROM permissions p
        INNER JOIN candidates c
          ON p.resource_id = c.id AND p.resource_type = c.rt
        WHERE
            (
              (p.subject_type = 'user' AND p.subject_id = CAST(:userId AS uuid))
              OR p.subject_type = 'everyone'
              OR (
                p.subject_type = 'department'
                AND p.subject_id = (
                    SELECT department_id FROM users
                    WHERE id = CAST(:userId AS uuid)
                      AND deleted_at IS NULL
                      AND is_active = TRUE
                )
              )
            )
            AND (p.expires_at IS NULL OR p.expires_at > NOW())
        """, nativeQuery = true)
    List<PermissionRow> findEffective(
        @Param("userId") UUID userId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId
    );

    /**
     * M8.0 — resource-level grant 목록 조회. {@code GET /api/{resource}/{id}/permissions} 의 service 단
     * 진입점({@link PermissionService#listPermissions})이 사용한다.
     *
     * <p>정렬은 created_at ASC, id ASC — 등시각 row 발생 시 stable 한 결과 (audit-style oldest-first).
     * 페이지네이션 미적용 — MVP 단일 리소스의 grant 수가 적다는 가정 (docs/02 §7.10).
     *
     * <p>{@code expires_at <= NOW()} 만료 row 도 포함하여 반환한다 — admin UI 가 cron 제거 전 만료-pending
     * row 를 표시할 수 있도록. 평가({@link #findEffective})와 다른 정책. cron 활성 환경이면 자연스럽게 정리됨.
     */
    List<PermissionRow> findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc(
        String resourceType, UUID resourceId);

    /**
     * Pessimistic write lock on a permission row by PK — {@link PermissionService#expirePermission}이
     * cron 만료 처리 진입 시점에 행 잠금 (CLAUDE.md §3 원칙 7 동형, {@code ShareRepository.lockByIdAndRevokedAtIsNull}
     * 패턴).
     *
     * <p>{@code permissions}는 hard delete를 사용하므로 두 번째 인스턴스가 동시에 같은 id를 처리하려 하면
     * lock 미스 → {@link Optional#empty()}. 호출자({@code expirePermission})는 이를 race-safe하게
     * {@link com.ibizdrive.common.error.ResourceNotFoundException}로 변환하고, job 레벨에서 swallow.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PermissionRow p WHERE p.id = :id")
    Optional<PermissionRow> lockById(@Param("id") UUID id);

    /**
     * {@code permissions-expired-cron} 후보 스캔 — {@code expires_at IS NOT NULL AND expires_at <= :now}.
     * 결과는 oldest-first 순 (동일 batch 내 다중 만료의 처리 안정성, {@code ShareRepository.findExpiredActiveIds}
     * 동형).
     *
     * <p>{@link PermissionExpirationJob}이 batch-size 한도로 호출 → 각 id에 대해 별도 트랜잭션의
     * {@link PermissionService#expirePermission}에서 다시 lock을 획득. 본 쿼리는 lock 미사용 (스냅샷 조회).
     *
     * @param now   기준 시각 (보통 {@link java.time.Instant#now()}).
     * @param limit batch-size 상한 — JPA Pageable로 LIMIT 매핑.
     */
    @Query("SELECT p.id FROM PermissionRow p "
         + "WHERE p.expiresAt IS NOT NULL AND p.expiresAt <= :now "
         + "ORDER BY p.expiresAt ASC, p.id ASC")
    List<UUID> findExpiredActiveIds(
        @Param("now") Instant now,
        Pageable limit
    );
}
