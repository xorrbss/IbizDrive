package com.ibizdrive.permission;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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
     * Plan D — cross-workspace move preview/transaction에서 subtree id 집합에 대한 active grant 일괄 조회.
     *
     * <p>{@code resourceIds}가 비어있으면 빈 리스트 반환(IN(...) empty 회피).
     * 만료(expires_at <= NOW())는 제외 — preview에 표시할 가치 없음, 실제 move 시 cron이 곧 정리.
     */
    @Query(value = """
        SELECT p.id, p.resource_type, p.resource_id, p.subject_type, p.subject_id,
               p.preset, p.granted_by, p.expires_at, p.created_at
        FROM permissions p
        WHERE p.resource_type = :resourceType
          AND p.resource_id IN (:resourceIds)
          AND (p.expires_at IS NULL OR p.expires_at > NOW())
        """, nativeQuery = true)
    List<PermissionRow> findActiveByResourceIn(
        @Param("resourceType") String resourceType,
        @Param("resourceIds") java.util.Collection<UUID> resourceIds
    );

    /**
     * P2c — {@code GET /api/folders/{id}/items} 응답의 {@code shareCount} 배지 wiring.
     *
     * <p>주어진 resource id 집합에 대해 active grant 수를 resource별로 group count. 상속 미적용 —
     * direct grant만 (FolderItemDto.shareCount 계약: "이 리소스 자체에 부여된 explicit 공유 수").
     *
     * <p>중복 grant는 {@code idx_permissions_unique} (V5 line 147)로 DB 차원 차단되므로
     * COUNT(*) ≡ COUNT(DISTINCT subject). 만료 행({@code expires_at <= NOW()})은 제외 —
     * {@link #findEffective} 와 동일 정책 (cron 정리 전 일시 잔존만 차이).
     *
     * <p>{@code resourceIds.isEmpty()}는 호출부 책임 (Spring Data가 IN()을 invalid SQL로 변환). 결과
     * 행은 count가 1 이상인 resource만 포함 — 0건 항목은 호출자가 Map miss → null로 처리.
     *
     * @param resourceType {@code "folder"} 또는 {@code "file"}.
     * @param resourceIds 비어있지 않은 id 집합.
     * @return {@code Object[]{UUID resourceId, Long count}} 형식의 row list — 0건 항목은 미포함.
     */
    @Query(value = """
        SELECT p.resource_id, COUNT(*)
        FROM permissions p
        WHERE p.resource_type = :resourceType
          AND p.resource_id IN (:resourceIds)
          AND (p.expires_at IS NULL OR p.expires_at > NOW())
        GROUP BY p.resource_id
        """, nativeQuery = true)
    List<Object[]> countActiveByResources(
        @Param("resourceType") String resourceType,
        @Param("resourceIds") java.util.Collection<UUID> resourceIds
    );

    /**
     * Plan D — subtree 내 모든 명시 grant 일괄 hard-delete (트랜잭션 안에서 호출).
     *
     * <p>{@code resourceIds}가 비어있으면 0 반환.
     */
    @Modifying
    @Query(value = "DELETE FROM permissions WHERE resource_type = :resourceType AND resource_id IN (:resourceIds)",
           nativeQuery = true)
    int deleteByResourceIn(
        @Param("resourceType") String resourceType,
        @Param("resourceIds") java.util.Collection<UUID> resourceIds
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

    /**
     * Wave 2 T5 — admin global permission matrix viewer.
     *
     * <p>{@code GET /api/admin/permissions} 의 service 단({@code AdminPermissionService}) 진입점.
     * 모든 grant 행을 admin 시점에서 filter+pagination 가능하게 노출.
     *
     * <p>filter 파라미터(모두 nullable, NULL 시 무시):
     * <ul>
     *   <li>{@code subjectType} — {@code user|department|role|everyone} 중 하나.</li>
     *   <li>{@code subjectId} — UUID. {@code subjectType} 와 함께 사용. {@code role}/{@code everyone}
     *       경우 호출자가 의미적 정합성 책임(controller layer 검증).</li>
     *   <li>{@code resourceType} — {@code folder|file}.</li>
     *   <li>{@code preset} — {@code read|upload|edit|admin}.</li>
     *   <li>{@code qLike} — 이미 {@code lower(...) + LIKE escape + '%' wrap} 처리된 패턴
     *       (예: {@code "%foo%"}). NULL 이면 q 검색 미적용. user.display_name / departments.name /
     *       folders.name / files.name 4개 컬럼 OR 매칭. JOIN 은 LEFT JOIN 이므로 매칭되지 않는 type 의 grant 도
     *       후보로 통과 — q 가 있으면 한 번이라도 매칭되어야 결과에 포함된다.</li>
     * </ul>
     *
     * <p>JOIN 정책:
     * <ul>
     *   <li>{@code LEFT JOIN users}: subject_type='user' 일 때만 매칭. 다른 type 은 NULL — q 매칭 비참여.</li>
     *   <li>{@code LEFT JOIN departments}: subject_type='department' 일 때만 매칭.</li>
     *   <li>{@code LEFT JOIN folders}: resource_type='folder' 일 때만 매칭. soft-delete row 도 표시 위해
     *       {@code deleted_at IS NULL} 필터 미적용 — admin 매트릭스는 dangling grant 도 가시화 (cron 정리 전).</li>
     *   <li>{@code LEFT JOIN files}: resource_type='file' 동형.</li>
     * </ul>
     *
     * <p>정렬: {@code ORDER BY p.created_at DESC, p.id DESC} — pagination tie-break 안정성.
     * 만료 row 도 포함 (isExpired 는 service 에서 derive).
     *
     * <p>본 메서드는 {@link PermissionRow} 엔티티만 반환 — name resolution 은 service 가 batch 처리 (N+1
     * 회피, {@link PermissionService#listPermissions} 의 user/dept name map 패턴 답습).
     *
     * @param subjectType  nullable filter.
     * @param subjectId    nullable filter (subjectType 과 짝).
     * @param resourceType nullable filter.
     * @param preset       nullable filter.
     * @param qLike        nullable LIKE 패턴 (호출자가 lower + escape + wrap).
     * @param pageable     page/size + (호출자가 정렬을 override 하지 않는 한) 본 SQL 의 ORDER BY 가 사용됨.
     */
    /**
     * User Home Dashboard — 본인이 직접 USER subject 로 받은 active grant 목록 (created_at DESC).
     *
     * <p>인증 사용자 본인의 직접 권한만 노출 — department/role/everyone indirect grant 는 v1.1 (집계 기준
     * 정의 필요). {@code expires_at <= NOW()} 만료 row 는 제외 (대시보드 진입 사용성, evaluator 정책 동형).
     *
     * <p>정렬: {@code created_at DESC, id DESC} — 가장 최근 받은 공유가 최상단 + tie-break stable.
     *
     * <p>리소스 이름/granter 이름/folderPath 는 service 단에서 N+1 회피 batch 처리 (admin matrix 패턴 답습).
     */
    @Query(value = """
        SELECT p.id, p.resource_type, p.resource_id, p.subject_type, p.subject_id,
               p.preset, p.granted_by, p.expires_at, p.created_at
        FROM permissions p
        WHERE p.subject_type = 'user'
          AND p.subject_id = CAST(:userId AS uuid)
          AND (p.expires_at IS NULL OR p.expires_at > NOW())
        ORDER BY p.created_at DESC, p.id DESC
        """, nativeQuery = true)
    List<PermissionRow> findGrantsToUserPaged(
        @Param("userId") UUID userId,
        Pageable pageable
    );

    @Query(
        value = """
            SELECT p.id, p.resource_type, p.resource_id, p.subject_type, p.subject_id,
                   p.preset, p.granted_by, p.expires_at, p.created_at
            FROM permissions p
            LEFT JOIN users u
              ON p.subject_type = 'user' AND p.subject_id = u.id
            LEFT JOIN departments d
              ON p.subject_type = 'department' AND p.subject_id = d.id
            LEFT JOIN folders f
              ON p.resource_type = 'folder' AND p.resource_id = f.id
            LEFT JOIN files fi
              ON p.resource_type = 'file' AND p.resource_id = fi.id
            WHERE (CAST(:subjectType AS varchar) IS NULL OR p.subject_type = CAST(:subjectType AS varchar))
              AND (CAST(:subjectId AS uuid) IS NULL OR p.subject_id = CAST(:subjectId AS uuid))
              AND (CAST(:resourceType AS varchar) IS NULL OR p.resource_type = CAST(:resourceType AS varchar))
              AND (CAST(:preset AS varchar) IS NULL OR p.preset = CAST(:preset AS varchar))
              AND (
                CAST(:qLike AS varchar) IS NULL
                OR LOWER(u.display_name) LIKE CAST(:qLike AS varchar) ESCAPE '\\'
                OR LOWER(d.name)         LIKE CAST(:qLike AS varchar) ESCAPE '\\'
                OR LOWER(f.name)         LIKE CAST(:qLike AS varchar) ESCAPE '\\'
                OR LOWER(fi.name)        LIKE CAST(:qLike AS varchar) ESCAPE '\\'
              )
            ORDER BY p.created_at DESC, p.id DESC
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM permissions p
            LEFT JOIN users u
              ON p.subject_type = 'user' AND p.subject_id = u.id
            LEFT JOIN departments d
              ON p.subject_type = 'department' AND p.subject_id = d.id
            LEFT JOIN folders f
              ON p.resource_type = 'folder' AND p.resource_id = f.id
            LEFT JOIN files fi
              ON p.resource_type = 'file' AND p.resource_id = fi.id
            WHERE (CAST(:subjectType AS varchar) IS NULL OR p.subject_type = CAST(:subjectType AS varchar))
              AND (CAST(:subjectId AS uuid) IS NULL OR p.subject_id = CAST(:subjectId AS uuid))
              AND (CAST(:resourceType AS varchar) IS NULL OR p.resource_type = CAST(:resourceType AS varchar))
              AND (CAST(:preset AS varchar) IS NULL OR p.preset = CAST(:preset AS varchar))
              AND (
                CAST(:qLike AS varchar) IS NULL
                OR LOWER(u.display_name) LIKE CAST(:qLike AS varchar) ESCAPE '\\'
                OR LOWER(d.name)         LIKE CAST(:qLike AS varchar) ESCAPE '\\'
                OR LOWER(f.name)         LIKE CAST(:qLike AS varchar) ESCAPE '\\'
                OR LOWER(fi.name)        LIKE CAST(:qLike AS varchar) ESCAPE '\\'
              )
            """,
        nativeQuery = true)
    Page<PermissionRow> findAllForAdminPageable(
        @Param("subjectType") String subjectType,
        @Param("subjectId") UUID subjectId,
        @Param("resourceType") String resourceType,
        @Param("preset") String preset,
        @Param("qLike") String qLike,
        Pageable pageable
    );
}
