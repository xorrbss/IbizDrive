import type { QueryClient } from '@tanstack/react-query'
import type { SortKey } from '@/types/file'
import type { AuditLogFilters } from '@/types/audit'
import type { AdminTrashFilters } from '@/types/trash'

/**
 * TanStack Query 캐시 키 팩토리. (docs/01 §6.1)
 *
 * 핵심 규칙:
 * - 폴더별 파일 목록 키는 [...files(), 'list', folderId, sort, dir] 형태로 sort/dir까지 포함.
 *   따라서 정렬 변종 전체를 한 번에 무효화하려면 sort/dir 없이 prefix 매칭이 필요 → filesListPrefix 사용.
 * - 폴더 메타(detail)와 파일 메타(detail)는 별개 keyspace.
 * - 사이드바 트리는 Plan B folderChildren (lazy per-workspace). folderTree 키 제거됨.
 *
 * `as const`로 readonly tuple을 반환해야 invalidateQueries({ queryKey }) 매칭이 정확함.
 */
export const qk = {
  all: ['explorer'] as const,
  folders: () => [...qk.all, 'folders'] as const,
  folder: (id: string) => [...qk.folders(), 'detail', id] as const,
  effectivePermissions: () => [...qk.all, 'permissions', 'effective'] as const,
  /** 노드(폴더/파일)별 effective 권한 (M8 docs/01 §14.2). nodeId 없으면 effectivePermissions(전역). */
  permissions: (nodeId?: string) =>
    nodeId
      ? ([...qk.all, 'permissions', 'node', nodeId] as const)
      : qk.effectivePermissions(),
  /**
   * 리소스에 부여된 grant 목록 (M8.1 — `GET /api/{folders|files}/:id/permissions`).
   *
   * `permissions(nodeId)` 와 keyspace 분리 — `effective` (현재 사용자 9-flag) vs `resource` (리소스에
   * 부여된 grant 행) 는 의미가 다르므로 동일 nodeId 라도 별개 캐시. resourceType 을 키에 포함해
   * 폴더/파일 UUID 충돌 가능성도 방지 (V5 별도 시퀀스로 운용상 비충돌이지만 명시).
   */
  resourcePermissions: (resourceType: 'folder' | 'file', id: string) =>
    [...qk.all, 'permissions', 'resource', resourceType, id] as const,

  files: () => [...qk.all, 'files'] as const,
  /** sort/dir까지 포함된 정확한 단일 키. 직접 캐시 read/write 시에만 사용. */
  filesInFolder: (folderId: string, sort: SortKey, dir: 'asc' | 'desc') =>
    [...qk.files(), 'list', folderId, sort, dir] as const,
  /**
   * 폴더의 파일 목록 prefix 키 — sort/dir 변종 전체를 한 번에 무효화할 때 사용.
   * 원칙 #6 (서버가 진실)에 따라 mutation 후 invalidate는 거의 항상 prefix 매칭.
   */
  filesListPrefix: (folderId: string) => [...qk.files(), 'list', folderId] as const,
  fileDetail: (id: string) => [...qk.files(), 'detail', id] as const,
  /**
   * 파일 버전 리스트 (M-RP.1) — RightPanel `versions` 탭.
   * backend `GET /api/files/{fileId}/versions` 응답을 캐시. 동일 fileId의 detail/list와 keyspace 분리.
   * 무효화 시점: 새 version 업로드 후 (A15.4 upload result `resolution=new_version`),
   * version restore 후 (M-RP.2). detail/versions 둘 다 invalidate 필요.
   */
  fileVersions: (id: string) => [...qk.files(), 'versions', id] as const,
  /**
   * 파일 활동 타임라인 (M-RP.4) — RightPanel `activity` 탭.
   * backend `GET /api/admin/audit?targetType=file&targetId=<id>&page&pageSize` 응답 캐시.
   * page/pageSize 변경 시 자동 재요청 (audit page key 패턴 mirror).
   * 무효화: 본 트랙 mutation 없음 — append-only audit_log이므로 staleTime + refetch on focus로 충분.
   */
  fileActivity: (id: string, page: number, pageSize: number) =>
    [...qk.files(), 'activity', id, page, pageSize] as const,

  // ── 휴지통 (M9) ──
  trash: () => [...qk.all, 'trash'] as const,
  /**
   * 워크스페이스 범위별 휴지통 목록 키 (Plan E T6).
   * scopeType + scopeId 필수 — backend `GET /api/trash?scopeType&scopeId` 필수 파라미터와 1:1 대응.
   * invalidate는 `qk.trash()` prefix 매칭 사용 → afterDelete/afterRestore/afterPurge 영향 없음.
   */
  trashList: (scopeType: 'department' | 'team', scopeId: string) =>
    [...qk.trash(), 'list', scopeType, scopeId] as const,

  // ── 공유 (F4, docs/02 §7.9) ──
  shares: () => [...qk.all, 'shares'] as const,
  /** 내가 공유한 목록 (by-me). cursor는 키에 미포함 — useInfiniteQuery가 pageParam으로 관리. */
  sharesByMe: () => [...qk.shares(), 'by-me'] as const,
  /** 받은 공유 목록 (with-me). MVP는 subject_type='user' 매칭만. */
  sharesWithMe: () => [...qk.shares(), 'with-me'] as const,

  // ── 검색 (M11) ──
  search: () => [...qk.all, 'search'] as const,
  /**
   * 검색 결과 키 — normalized query + filters 객체.
   * normalized는 normalizeForSearch() 출력값 (NFC + lowercase + collapse).
   * filters는 빈 객체로 시작 (MVP), 추가 시그니처 호환 유지.
   */
  searchResults: (normalized: string, filters: Record<string, unknown>) =>
    [...qk.search(), 'results', normalized, filters] as const,

  // ── 저장 용량 (M15, mock) ──
  storageQuota: () => [...qk.all, 'storage', 'quota'] as const,

  // ── 사용자 검색 (F6, docs/02 §7.14, ADR #35) ──
  users: () => [...qk.all, 'users'] as const,
  /**
   * 사용자 검색 결과 키 — normalized query + limit. normalize는 `q.trim().toLowerCase()`
   * (A14 ADR #35 — `normalizeForSearch`의 NFC collapse는 user search에 부적합).
   * cursor 미지원이므로 key에도 cursor 미포함.
   */
  usersSearch: (normalized: string, limit: number) =>
    [...qk.users(), 'search', normalized, limit] as const,

  // ── 부서 검색 (A16, docs/02 §7.x, ADR #36) ──
  departments: () => [...qk.all, 'departments'] as const,
  /**
   * 부서 검색 결과 키 — normalized query + limit. usersSearch 1:1 답습.
   * normalize는 `q.trim().toLowerCase()`. cursor 미지원.
   */
  departmentsSearch: (normalized: string, limit: number) =>
    [...qk.departments(), 'search', normalized, limit] as const,

  // ── 감사 로그 (M12, mock) ──
  audit: () => [...qk.all, 'audit'] as const,
  /** 페이지/필터까지 포함된 정확한 단일 키. 필터 변경 시 자동 재요청. */
  auditLogs: (filters: AuditLogFilters, page: number, pageSize: number) =>
    [...qk.audit(), 'logs', filters, page, pageSize] as const,

  // ── 관리자 사용자 목록 (admin-user-mgmt + admin-user-search-update, docs/02 §7.4) ──
  adminUsers: () => [...qk.all, 'admin', 'users'] as const,
  /**
   * paginated admin user list — page/size/q까지 포함 정확한 단일 키. PATCH 후 prefix 매칭으로
   * 모든 변종 무효화 ({@link invalidations.afterAdminUserChanged}).
   *
   * <p>{@code q}는 trim 후 사용 — 빈 문자열은 정규화로 ''(전체) 키와 동일 취급.
   */
  adminUsersList: (page: number, size: number, q = '') =>
    [...qk.adminUsers(), 'list', page, size, q.trim()] as const,

  // ── 관리자 스토리지 overview (admin-storage-overview) ──
  /**
   * 단일 read-only summary — 페이지 진입 시 1회. invalidation 대상 mutation 없음
   * (audit_log row를 통한 마지막 orphan cleanup 메트릭만 노출이며 본 페이지에서 mutation 없음).
   */
  adminStorageOverview: () => [...qk.all, 'admin', 'storage', 'overview'] as const,

  // ── 관리자 부서 목록 (admin-department-crud, Wave 2 T4, docs/02 §7.x) ──
  adminDepartments: () => [...qk.all, 'admin', 'departments'] as const,
  /**
   * paginated admin department list — page/size/q까지 포함 정확한 단일 키. mutation 후
   * prefix 매칭으로 모든 page/size/q 변종 일괄 무효화
   * ({@link invalidations.afterAdminDepartmentChanged}).
   *
   * <p>q는 검색어 부분 일치(LIKE) — 빈 문자열은 "전체"로 키 분리. normalize는 q.trim().toLowerCase()
   * (T4와 A16 search 모두 동일 정책 — backend service가 다시 trim/lowercase하지만 캐시 키는
   * 정규화된 것을 써야 사용자 입력 변형이 동일 캐시를 가리킨다).
   */
  adminDepartmentsList: (page: number, size: number, q: string) =>
    [...qk.adminDepartments(), 'list', page, size, q] as const,

  // ── 관리자 시스템 (Wave 1 — T3, docs/02 §7.12) ──
  adminSystem: () => [...qk.all, 'admin', 'system'] as const,
  /**
   * 4 cron 잡 설정 스냅샷 (read-only). 본 트랙은 mutation 없음 — staleTime + refetch on focus로
   * 충분. 별도 invalidations 헬퍼 미정의.
   */
  adminSystemCron: () => [...qk.adminSystem(), 'cron'] as const,

  // ── admin 권한 매트릭스 (admin-permission-matrix, Wave 2 T5) ──
  adminPermissions: () => [...qk.all, 'admin', 'permissions'] as const,
  /**
   * paginated admin 권한 매트릭스 — read-only이므로 invalidations entry 없음.
   * filters 객체 자체를 키 일부로 사용 (직렬화 비용 < 캐시 분리 정확성).
   */
  adminPermissionsList: (filters: {
    subjectType?: string
    subjectId?: string
    resourceType?: string
    preset?: string
    q?: string
    page?: number
    size?: number
  }) => [...qk.adminPermissions(), 'list', filters] as const,

  // ── 관리자 글로벌 휴지통 (admin-global-trash, Wave 2 T9, docs/02 §7.x) ──
  adminTrash: () => [...qk.all, 'admin', 'trash'] as const,
  /**
   * cursor-paginated admin trash list — q/type/ownerId + cursor까지 포함 정확한 단일 키.
   * mutation(restore/purge) 후 prefix 매칭으로 모든 변종 일괄 무효화
   * ({@link invalidations.afterAdminTrashChanged}). cursor=null은 첫 페이지.
   */
  adminTrashList: (filters: AdminTrashFilters, cursor: string | null) =>
    [
      ...qk.adminTrash(),
      'list',
      filters.q ?? '',
      filters.type ?? null,
      filters.ownerId ?? null,
      filters.deletedFrom ?? null,
      filters.deletedTo ?? null,
      cursor ?? null,
    ] as const,
  /**
   * 휴지통 보존 정책 read-only viewer (wave2-trash-policy-viewer, Wave 2 T9 follow-up).
   * 단일 키 — 인자 없음. mutation 없음(현재는 read-only).
   */
  adminTrashPolicy: () => [...qk.adminTrash(), 'policy'] as const,

  // ── 관리자 대시보드 (admin-dashboard 트랙) ──
  adminDashboard: () => [...qk.all, 'admin', 'dashboard'] as const,

  // ── 인증 (auth-pages, ADR #41) ──
  auth: () => [...qk.all, 'auth'] as const,
  /**
   * 현재 로그인 사용자(`/api/auth/me`) 캐시 키. login/logout/signup 후 무효화.
   * 401(미인증)도 정상 캐시값(null)으로 다룬다 — useMe가 retry false 처리.
   */
  authMe: () => [...qk.auth(), 'me'] as const,

  // ── Workspaces (Plan B, spec §5.2) ──
  workspaces: {
    all: () => [...qk.all, 'workspaces'] as const,
    me: () => [...qk.all, 'workspaces', 'me'] as const,
  },

  /**
   * 사이드바 트리 lazy children — spec §4.5 §3.
   * `scopeType` + `scopeId`까지 포함해 동일 parentId가 부서/팀 간 우연 일치(매우 드물지만)에도 캐시 분리.
   * `parentId === rootFolderId` 인 호출이 워크스페이스 root 직속 자식 조회.
   */
  folderChildren: (scopeType: 'department' | 'team', scopeId: string, parentId: string) =>
    [...qk.all, 'folders', 'children', scopeType, scopeId, parentId] as const,

  // ── Teams (Plan B, spec §5.2 — POST /api/teams) ──
  teams: {
    all: () => [...qk.all, 'teams'] as const,
  },
} as const

// ─── 무효화 전략 헬퍼 ──────────────────────────────────────────────────────
//
// hooks/useMoveBulk·useDeleteBulk·useRenameFile에서 같은 invalidate 매트릭스가
// 반복되어 일원화. 새 mutation hook을 추가할 때는 가능하면 아래 헬퍼를 재사용.

export const invalidations = {
  /**
   * 파일/폴더 이동 후 무효화.
   * - source/target 폴더의 파일 목록 (모든 sort/dir 변종 prefix 매칭)
   * - folderChildren prefix 전체 (이동된 항목 중 폴더가 있을 수 있음 → 사이드바 트리 갱신)
   * - 각 이동된 id의 fileDetail (parent 변경)
   */
  afterFilesMoved(
    qc: QueryClient,
    opts: { sourceFolderId: string; targetFolderId: string; ids: string[] },
  ): Promise<void> {
    const { sourceFolderId, targetFolderId, ids } = opts
    return Promise.all([
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(sourceFolderId) }),
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(targetFolderId) }),
      qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] }),
      ...ids.map((id) => qc.invalidateQueries({ queryKey: qk.fileDetail(id) })),
    ]).then(() => undefined)
  },

  /**
   * 폴더 신규 생성 후 무효화 (folder-create-ui 트랙).
   * - parentId 폴더의 자식 목록 (`getFilesInFolder`는 폴더+파일 통합 listing)
   * - folderChildren prefix 전체 (사이드바 트리 갱신)
   * - folder(parentId) (breadcrumb / detail의 자식 카운트가 노출될 수 있어 보수적)
   *
   * 가상 root(`'root'`)도 그대로 호출 — `getFilesInFolder('root')`가 tree top-level
   * 합성 캐시를 사용하므로 `qk.filesListPrefix('root')` invalidate가 정합.
   */
  afterFolderCreated(
    qc: QueryClient,
    opts: { parentId: string },
  ): Promise<void> {
    const { parentId } = opts
    return Promise.all([
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(parentId) }),
      qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] }),
      qc.invalidateQueries({ queryKey: qk.folder(parentId) }),
    ]).then(() => undefined)
  },

  /**
   * 단일 항목 이름 변경 후 무효화.
   * - parentId 폴더의 파일 목록
   * - 해당 항목의 fileDetail
   * - 폴더인 경우 추가로 folderChildren prefix 전체 + folder(id)
   */
  afterRename(
    qc: QueryClient,
    opts: { id: string; parentId: string; isFolder: boolean },
  ): Promise<void> {
    const { id, parentId, isFolder } = opts
    const tasks = [
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(parentId) }),
      qc.invalidateQueries({ queryKey: qk.fileDetail(id) }),
    ]
    if (isFolder) {
      tasks.push(qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] }))
      tasks.push(qc.invalidateQueries({ queryKey: qk.folder(id) }))
    }
    return Promise.all(tasks).then(() => undefined)
  },

  /**
   * 휴지통 이동(soft delete) 후 무효화.
   * - 해당 폴더의 파일 목록 (제외됨 → 새로고침)
   * - 휴지통 라우트 (새 항목 표시)
   * - 검색 결과 (휴지통 항목 제외 — `qk.search` prefix 전체)
   * - folderChildren prefix 전체 (폴더 soft-delete 시 트리에서 제거; file-only 호출에도 보수적 무효화)
   */
  afterDelete(
    qc: QueryClient,
    opts: { folderId: string },
  ): Promise<void> {
    return Promise.all([
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(opts.folderId) }),
      qc.invalidateQueries({ queryKey: qk.trash() }),
      qc.invalidateQueries({ queryKey: qk.search() }),
      qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] }),
    ]).then(() => undefined)
  },

  /**
   * 휴지통 복원 후 무효화 (M9).
   * - 복원 대상의 originalParent 폴더 목록 (있다면)
   * - 휴지통 라우트 (해당 항목 사라짐)
   * - 검색 결과 (다시 노출 가능)
   * - folderChildren prefix 전체 (folder cascade restore 시 트리에 재등장; file-only 호출에도 보수적 무효화)
   * 호출자는 originalParent가 다양할 수 있으니 prefix 전체(`qk.files()`) 보수 무효화도 옵션으로 허용.
   */
  afterRestore(
    qc: QueryClient,
    opts: { folderIds?: string[] } = {},
  ): Promise<void> {
    const tasks: Promise<void>[] = [
      qc.invalidateQueries({ queryKey: qk.trash() }),
      qc.invalidateQueries({ queryKey: qk.search() }),
      qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] }),
    ]
    if (opts.folderIds && opts.folderIds.length > 0) {
      for (const fid of opts.folderIds) {
        tasks.push(qc.invalidateQueries({ queryKey: qk.filesListPrefix(fid) }))
      }
    } else {
      // 원본 폴더 모름 → 모든 files 목록 prefix 보수 무효화
      tasks.push(qc.invalidateQueries({ queryKey: qk.files() }))
    }
    return Promise.all(tasks).then(() => undefined)
  },

  /**
   * 영구 삭제(purge) 후 무효화 (M9).
   * - 휴지통 라우트만. 다른 keyspace는 이미 deletedAt!=null로 제외 중.
   */
  afterPurge(qc: QueryClient): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.trash() })
  },

  /**
   * 공유 생성 후 무효화 (F4, docs/02 §7.9).
   * - shares() prefix 단일 — by-me / with-me 모두 갱신.
   * - file `qk.permissions(fileId)` 캐시는 무효화 미발생 (같은 세션 자기→자기 share 케이스 없음, 실용적 불필요).
   */
  afterShareCreate(qc: QueryClient): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.shares() })
  },

  /**
   * 공유 revoke 후 무효화 (F4).
   * - shares() prefix 단일. by-me 목록에서 즉시 제거 + 받은 사람 with-me도 갱신.
   */
  afterShareRevoke(qc: QueryClient): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.shares() })
  },

  /**
   * 관리자 사용자 PATCH 후 무효화 (admin-user-mgmt).
   * - adminUsers() prefix 단일 — 모든 page/size 변종 일괄 갱신.
   * - 단일 사용자 상세 keyspace는 본 트랙 미도입 (목록만 사용).
   */
  afterAdminUserChanged(qc: QueryClient): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.adminUsers() })
  },

  /**
   * 관리자 부서 mutation 후 무효화 (admin-department-crud).
   *
   * <p>{@link afterAdminUserChanged}와 동형 — adminDepartments() prefix 단일로 모든
   * page/size/q 변종을 일괄 갱신. share picker 캐시({@code qk.departments()})는
   * 다른 keyspace이고 활성 목록에만 영향이 가므로 동일 prefix 무효화는 하지 않는다
   * (UX 영향: rename/deactivate가 picker에 즉시 반영되지 않을 수 있으나, picker는
   * staleTime + refetchOnWindowFocus로 충분 — admin 페이지에서만 부서 mutation 발생).
   */
  afterAdminDepartmentChanged(qc: QueryClient): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.adminDepartments() })
  },

  /**
   * 관리자 글로벌 휴지통 mutation 후 무효화 (admin-global-trash, Wave 2 T9).
   *
   * <p>adminTrash() prefix 단일 — 모든 q/type/ownerId/cursor 변종 일괄 갱신.
   * 일반 사용자 trash keyspace({@code qk.trash()})는 별도 — admin restore/purge가
   * 다른 사용자의 trash에 영향을 주므로 동일 prefix로 묶지 않음 (cross-user impact는
   * 사용자별 useQuery refetchOnWindowFocus + staleTime로 충분).
   */
  afterAdminTrashChanged(qc: QueryClient): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.adminTrash() })
  },

  /**
   * 팀 생성/멤버 변경 후 무효화 — Plan B Task 3.
   * - workspaces.me() (사이드바가 새 팀을 즉시 표시)
   */
  afterTeamChanged(qc: QueryClient): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.workspaces.me() })
  },
} as const
