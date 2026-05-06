import type { QueryClient } from '@tanstack/react-query'
import type { SortKey } from '@/types/file'
import type { AuditLogFilters } from '@/types/audit'

/**
 * TanStack Query 캐시 키 팩토리. (docs/01 §6.1)
 *
 * 핵심 규칙:
 * - 폴더별 파일 목록 키는 [...files(), 'list', folderId, sort, dir] 형태로 sort/dir까지 포함.
 *   따라서 정렬 변종 전체를 한 번에 무효화하려면 sort/dir 없이 prefix 매칭이 필요 → filesListPrefix 사용.
 * - 폴더 메타(detail)와 파일 메타(detail)는 별개 keyspace.
 * - folderTree는 사이드바 트리 1개. 폴더 rename/move 시 무효화.
 *
 * `as const`로 readonly tuple을 반환해야 invalidateQueries({ queryKey }) 매칭이 정확함.
 */
export const qk = {
  all: ['explorer'] as const,
  folders: () => [...qk.all, 'folders'] as const,
  folderTree: () => [...qk.folders(), 'tree'] as const,
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
  trashList: () => [...qk.trash(), 'list'] as const,

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

  // ── 인증 (auth-pages, ADR #41) ──
  auth: () => [...qk.all, 'auth'] as const,
  /**
   * 현재 로그인 사용자(`/api/auth/me`) 캐시 키. login/logout/signup 후 무효화.
   * 401(미인증)도 정상 캐시값(null)으로 다룬다 — useMe가 retry false 처리.
   */
  authMe: () => [...qk.auth(), 'me'] as const,
} as const

// ─── 무효화 전략 헬퍼 ──────────────────────────────────────────────────────
//
// hooks/useMoveBulk·useDeleteBulk·useRenameFile에서 같은 invalidate 매트릭스가
// 반복되어 일원화. 새 mutation hook을 추가할 때는 가능하면 아래 헬퍼를 재사용.

export const invalidations = {
  /**
   * 파일/폴더 이동 후 무효화.
   * - source/target 폴더의 파일 목록 (모든 sort/dir 변종 prefix 매칭)
   * - folderTree (이동된 항목 중 폴더가 있을 수 있음 → 보수적으로 항상 무효화)
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
      qc.invalidateQueries({ queryKey: qk.folderTree() }),
      ...ids.map((id) => qc.invalidateQueries({ queryKey: qk.fileDetail(id) })),
    ]).then(() => undefined)
  },

  /**
   * 단일 항목 이름 변경 후 무효화.
   * - parentId 폴더의 파일 목록
   * - 해당 항목의 fileDetail
   * - 폴더인 경우 추가로 folderTree + folder(id)
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
      tasks.push(qc.invalidateQueries({ queryKey: qk.folderTree() }))
      tasks.push(qc.invalidateQueries({ queryKey: qk.folder(id) }))
    }
    return Promise.all(tasks).then(() => undefined)
  },

  /**
   * 휴지통 이동(soft delete) 후 무효화.
   * - 해당 폴더의 파일 목록 (제외됨 → 새로고침)
   * - 휴지통 라우트 (새 항목 표시)
   * - 검색 결과 (휴지통 항목 제외 — `qk.search` prefix 전체)
   * - folderTree (폴더 soft-delete 시 트리에서 제거; file-only 호출에도 보수적 무효화)
   */
  afterDelete(
    qc: QueryClient,
    opts: { folderId: string },
  ): Promise<void> {
    return Promise.all([
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(opts.folderId) }),
      qc.invalidateQueries({ queryKey: qk.trash() }),
      qc.invalidateQueries({ queryKey: qk.search() }),
      qc.invalidateQueries({ queryKey: qk.folderTree() }),
    ]).then(() => undefined)
  },

  /**
   * 휴지통 복원 후 무효화 (M9).
   * - 복원 대상의 originalParent 폴더 목록 (있다면)
   * - 휴지통 라우트 (해당 항목 사라짐)
   * - 검색 결과 (다시 노출 가능)
   * - folderTree (folder cascade restore 시 트리에 재등장; file-only 호출에도 보수적 무효화)
   * 호출자는 originalParent가 다양할 수 있으니 prefix 전체(`qk.files()`) 보수 무효화도 옵션으로 허용.
   */
  afterRestore(
    qc: QueryClient,
    opts: { folderIds?: string[] } = {},
  ): Promise<void> {
    const tasks: Promise<void>[] = [
      qc.invalidateQueries({ queryKey: qk.trash() }),
      qc.invalidateQueries({ queryKey: qk.search() }),
      qc.invalidateQueries({ queryKey: qk.folderTree() }),
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
} as const
