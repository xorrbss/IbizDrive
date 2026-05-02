import type { FolderNode, FolderDetail } from '@/types/folder'
import type { FileItem, SortKey } from '@/types/file'
import type { AuditLogEntry, AuditLogFilters, AuditLogPage } from '@/types/audit'
import type { Permission } from '@/types/permission'
import type { TrashItem, TrashItemType, TrashPage } from '@/types/trash'
import type { FileVersionDto } from '@/types/version'
import type { ShareCreateRequest, ShareDto, SharePage } from '@/types/share'
import type { UserSummary } from '@/types/user'
import type { DepartmentSummary } from '@/types/department'
import type { AuthSession, LoginParams, SignupParams } from '@/types/auth'
import { findNode, containsNode } from './folderTreeUtils'
import { normalizedNameForDedup } from './normalize'

// MOCK DATA — 실제 API 붙이면 제거
const MOCK_TREE: FolderNode = {
  id: 'root',
  parentId: null,
  name: '내 드라이브',
  slug: '',
  children: [
    {
      id: 'folder_sales',
      parentId: 'root',
      name: '영업팀',
      slug: '영업팀',
      children: [
        {
          id: 'folder_contracts',
          parentId: 'folder_sales',
          name: '계약서',
          slug: '계약서',
        },
      ],
    },
    {
      id: 'folder_hr',
      parentId: 'root',
      name: '인사팀',
      slug: '인사팀',
    },
  ],
}

const MOCK_FILES: FileItem[] = [
  {
    id: 'file_proposal',
    name: '제안서_2026.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 2_400_000,
    updatedAt: '2026-04-20T09:00:00Z',
    updatedBy: '김영수',
    parentId: 'root',
  },
  {
    id: 'file_budget',
    name: '예산안.xlsx',
    type: 'file',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    size: 580_000,
    updatedAt: '2026-04-18T14:30:00Z',
    updatedBy: '이지은',
    parentId: 'root',
  },
  {
    id: 'file_minutes',
    name: '회의록_0415.docx',
    type: 'file',
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    size: 120_000,
    updatedAt: '2026-04-15T11:00:00Z',
    updatedBy: '박준형',
    parentId: 'root',
  },
  {
    id: 'file_contract_a',
    name: '계약서_A사.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 3_100_000,
    updatedAt: '2026-04-22T16:00:00Z',
    updatedBy: '김영수',
    parentId: 'folder_contracts',
  },
  {
    id: 'file_contract_b',
    name: '계약서_B사.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 2_800_000,
    updatedAt: '2026-04-21T10:00:00Z',
    updatedBy: '이지은',
    parentId: 'folder_contracts',
  },
  {
    id: 'folder_sales',
    name: '영업팀',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-19T08:00:00Z',
    updatedBy: '관리자',
    parentId: 'root',
  },
  {
    id: 'folder_hr',
    name: '인사팀',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-10T08:00:00Z',
    updatedBy: '관리자',
    parentId: 'root',
  },
]

function findNodeAndPath(
  node: FolderNode,
  id: string,
  path: FolderNode[] = []
): FolderNode[] | null {
  const next = [...path, node]
  if (node.id === id) return next
  for (const c of node.children ?? []) {
    const r = findNodeAndPath(c, id, next)
    if (r) return r
  }
  return null
}

function detachFromTree(tree: FolderNode, nodeId: string): FolderNode | null {
  if (!tree.children) return null
  const idx = tree.children.findIndex((c) => c.id === nodeId)
  if (idx !== -1) {
    const [removed] = tree.children.splice(idx, 1)
    return removed
  }
  for (const c of tree.children) {
    const r = detachFromTree(c, nodeId)
    if (r) return r
  }
  return null
}

function relocateInTree(
  tree: FolderNode,
  nodeId: string,
  newParentId: string,
): void {
  const node = detachFromTree(tree, nodeId)
  if (!node) return // MOCK_FILES에만 있는 파일은 트리 작업 불필요
  const newParent = findNode(tree, newParentId)
  if (!newParent) {
    // 호출 전 newParent 존재를 보장해야 함. 도달 시 일관성 깨진 상태.
    throw { status: 500, code: 'TREE_RELOCATE_FAILED' }
  }
  newParent.children = [...(newParent.children ?? []), node]
  node.parentId = newParentId
}

export const api = {
  async getFolderTree(): Promise<FolderNode> {
    await new Promise((r) => setTimeout(r, 100))
    return MOCK_TREE
  },

  async getFolder(id: string): Promise<FolderDetail> {
    await new Promise((r) => setTimeout(r, 100))
    const chain = findNodeAndPath(MOCK_TREE, id)
    if (!chain) throw { status: 404, code: 'NOT_FOUND' }
    const self = chain[chain.length - 1]
    const slugPath = chain.slice(1).map((n) => n.slug) // root 제외
    return {
      id: self.id,
      name: self.name,
      slugPath,
      breadcrumb: chain.map((n, i) => ({
        id: n.id,
        name: n.name,
        slugPath: chain.slice(1, i + 1).map((x) => x.slug),
      })),
      parentId: self.parentId,
    }
  },

  async getFilesInFolder(
    folderId: string,
    sort: SortKey = 'name',
    dir: 'asc' | 'desc' = 'asc'
  ): Promise<FileItem[]> {
    await new Promise((r) => setTimeout(r, 150))
    const items = MOCK_FILES.filter((f) => f.parentId === folderId)
    return items.sort((a, b) => {
      let cmp = 0
      if (sort === 'name') {
        cmp = a.name.localeCompare(b.name, 'ko')
      } else if (sort === 'updatedAt') {
        cmp = a.updatedAt.localeCompare(b.updatedAt)
      } else if (sort === 'size') {
        cmp = (a.size ?? 0) - (b.size ?? 0)
      }
      return dir === 'asc' ? cmp : -cmp
    })
  },

  async getFileDetail(id: string): Promise<FileItem> {
    await new Promise((r) => setTimeout(r, 100))
    const found = MOCK_FILES.find((f) => f.id === id)
    if (!found) throw { status: 404, code: 'NOT_FOUND' }
    return found
  },

  /**
   * M-RP.1 — 파일 버전 리스트 (docs/02 §7.6, A5.2 endpoint).
   *
   * backend `GET /api/files/{fileId}/versions` 직접 호출. 응답 envelope `{ versions: [...] }` 풀어서
   * 배열만 반환 (호출부 `useFileVersions`가 `data` 자체를 list로 다루도록).
   *
   * 정렬은 backend가 versionNumber DESC로 보장 (FileVersionRepository.findByFileIdOrderByVersionNumberDesc).
   * 권한 가드는 backend `@PreAuthorize hasPermission(#fileId, 'file', 'READ')` — 비READ는 403.
   *
   * 에러: 비-OK 응답은 status 필드 가진 Error throw (getEffectivePermissions와 동일 패턴 — QueryCache.onError 분기).
   */
  async listFileVersions(fileId: string): Promise<FileVersionDto[]> {
    const res = await fetch(
      `/api/files/${encodeURIComponent(fileId)}/versions`,
      {
        method: 'GET',
        credentials: 'include',
        headers: { Accept: 'application/json' },
      },
    )
    if (!res.ok) {
      const err = new Error(
        `listFileVersions fetch failed: ${res.status}`,
      ) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const data = (await res.json()) as { versions: FileVersionDto[] }
    return data.versions
  },

  /**
   * M-RP.2.1 — 특정 version 핀 다운로드 (docs/02 §7.6, ADR #39).
   *
   * `downloadFile`과 동일한 anchor click 패턴 — fetch+Blob 미사용 이유는 §M-Download 주석 참고
   * (cookie 자동 동봉, RFC 5987 헤더 처리, 큰 파일 메모리 미적재).
   *
   * 권한은 backend `hasPermission(#fileId, 'file', 'READ')`. version은 backend가 cross-file 가드
   * (`version.fileId != path fileId` → 404)를 service 레이어에서 재검증.
   */
  downloadVersion(fileId: string, versionId: string): void {
    const a = document.createElement('a')
    a.href = `/api/files/${encodeURIComponent(fileId)}/versions/${encodeURIComponent(versionId)}/download`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  },

  /**
   * M-RP.2.2 — 특정 version을 file의 current로 재지정 (옵션 A, ADR #39).
   *
   * backend `POST /api/files/{fileId}/versions/{versionId}/restore` — 200 envelope `{ file: FileDto }`.
   * 멱등: 이미 current인 version을 다시 호출해도 200 (audit emit 없음). 호출자(useRestoreVersion)는
   * 본 함수 resolve 후 `qk.fileDetail(fileId)` + `qk.fileVersions(fileId)` invalidate.
   *
   * 응답 본문(FileDto)은 mutation 결과의 신선한 상태이지만 invalidate 후 재요청으로 충분하므로
   * 반환값을 void로 단순화 (KISS — caller에 envelope 형 변환 부담 회피).
   *
   * 권한은 backend `hasPermission(#fileId, 'file', 'EDIT')` — READ만 보유한 사용자는 403.
   */
  async restoreVersion(fileId: string, versionId: string): Promise<void> {
    const res = await fetch(
      `/api/files/${encodeURIComponent(fileId)}/versions/${encodeURIComponent(versionId)}/restore`,
      {
        method: 'POST',
        credentials: 'include',
        headers: { Accept: 'application/json' },
      },
    )
    if (!res.ok) {
      const err = new Error(
        `restoreVersion failed: ${res.status}`,
      ) as Error & { status: number }
      err.status = res.status
      throw err
    }
  },

  // M9.1 — 단건 soft delete (휴지통 이동). 호출자(useDeleteBulk)는 selection 단위로
  // file/folder를 판별해 본 함수와 softDeleteFolder를 분기 호출한다 (backend는 bulk endpoint
  // 미제공, ADR #32 §운영 노트). 응답은 204 NO_CONTENT라 본문 무시.
  async softDeleteFile(id: string): Promise<void> {
    const res = await fetch(`/api/files/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      credentials: 'include',
    })
    if (!res.ok) {
      const err = new Error(`softDeleteFile failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
  },

  async softDeleteFolder(id: string): Promise<void> {
    const res = await fetch(`/api/folders/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      credentials: 'include',
    })
    if (!res.ok) {
      const err = new Error(`softDeleteFolder failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
  },

  async moveFiles(
    ids: string[],
    targetFolderId: string,
  ): Promise<{ movedIds: string[] }> {
    await new Promise((r) => setTimeout(r, 400))

    // 1) 타겟 폴더 존재 검증
    const targetExists = !!findNode(MOCK_TREE, targetFolderId)
    if (!targetExists) throw { status: 404, code: 'TARGET_NOT_FOUND' }

    // 2) self / descendant 검증 (원칙 #10 — UI와 독립적인 서버 재검증)
    for (const id of ids) {
      if (id === targetFolderId) {
        throw { status: 400, code: 'MOVE_INTO_SELF' }
      }
      const node = findNode(MOCK_TREE, id)
      if (node && containsNode(node, targetFolderId)) {
        throw { status: 400, code: 'MOVE_INTO_DESCENDANT' }
      }
    }

    // 3) 적용 — MOCK_FILES.parentId 갱신 + 폴더면 MOCK_TREE에서 재배치
    for (const id of ids) {
      const fileIdx = MOCK_FILES.findIndex((f) => f.id === id)
      if (fileIdx !== -1) {
        MOCK_FILES[fileIdx].parentId = targetFolderId
      }
      relocateInTree(MOCK_TREE, id, targetFolderId)
    }

    return { movedIds: ids }
  },

  async renameFile(id: string, newName: string): Promise<FileItem> {
    await new Promise((r) => setTimeout(r, 200))

    const trimmed = newName.trim()
    if (trimmed.length === 0) {
      throw { status: 400, code: 'VALIDATION_ERROR' }
    }

    const target = MOCK_FILES.find((f) => f.id === id)
    if (!target) throw { status: 404, code: 'NOT_FOUND' }

    // 같은 부모 내 normalized 중복 검사 (자기 자신 제외)
    const normalized = normalizedNameForDedup(trimmed)
    const conflict = MOCK_FILES.find(
      (f) =>
        f.id !== id &&
        f.parentId === target.parentId &&
        normalizedNameForDedup(f.name) === normalized,
    )
    if (conflict) throw { status: 409, code: 'RENAME_CONFLICT' }

    target.name = trimmed
    target.updatedAt = new Date().toISOString()

    // 폴더면 MOCK_TREE의 노드 이름도 갱신
    if (target.type === 'folder') {
      const node = findNode(MOCK_TREE, id)
      if (node) {
        node.name = trimmed
        node.slug = trimmed
      }
    }

    return target
  },

  /**
   * A15 — 파일 업로드 (`POST /api/files`, multipart). docs/02 §6.1 + §7.6.
   *
   * 실 {@link XMLHttpRequest}를 반환 — useUpload가 progress/onload/onerror/abort 인터페이스로
   * 사용. 응답 status 분기는 호출자(useUpload) 책임:
   * - 201 Created → 신규 파일 INSERT
   * - 200 OK     → resolution=new_version으로 기존 파일에 새 version 추가
   * - 409        → ApiError envelope `{ error: { code: 'RENAME_CONFLICT', ... } }`. 호출자는
   *                conflict 상태로 전환 후 사용자에게 resolution 재요청 (UploadConflictDialog).
   * - 403/413/5xx → uploadErrors.ts classifyError 매핑
   *
   * 세션 쿠키 전송을 위해 {@code withCredentials = true}.
   */
  uploadFile(params: {
    file: File
    folderId: string
    resolution?: 'new_version' | 'rename'
    newName?: string
  }): XMLHttpRequest {
    const xhr = new XMLHttpRequest()
    const form = new FormData()
    const fileToSend = params.newName
      ? new File([params.file], params.newName, { type: params.file.type })
      : params.file
    form.append('file', fileToSend)
    form.append('folderId', params.folderId)
    if (params.resolution) form.append('resolution', params.resolution)

    xhr.open('POST', '/api/files')
    xhr.withCredentials = true
    xhr.send(form)
    return xhr
  },

  /**
   * M-Download — backend `GET /api/files/{id}/download` (docs/02 §7.6.1) 트리거.
   *
   * Programmatic anchor click 패턴 — fetch+Blob 대비 이점:
   * - cookie 인증은 same-origin GET이라 브라우저가 자동 동봉 (별도 withCredentials 불요)
   * - RFC 5987 `Content-Disposition: attachment; filename*=UTF-8''...`을 backend가
   *   처리하므로 브라우저가 파일명/저장경로 자동 적용
   * - 100MB까지의 파일을 메모리에 적재하지 않고 스트림 → 디스크
   * - 진행률은 브라우저 다운로드 매니저 책임 → UI 추가 없음 (fire-and-forget)
   *
   * 권한은 backend `hasPermission(#id, 'file', 'READ')` (ADR #36 — DOWNLOAD enum 미도입).
   */
  downloadFile(id: string): void {
    const a = document.createElement('a')
    a.href = `/api/files/${encodeURIComponent(id)}/download`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  },

  /**
   * M11 / F1.1 검색 — 백엔드 GET /api/search 직접 호출 (ADR #33, docs/02 §7.8).
   *
   * 호출자(useSearch)는 이미 normalizeForSearch + 최소 2자 게이트를 통과한 query를 넘김.
   * 1자 이하/빈 query는 방어적으로 즉시 빈 결과 (useQuery enabled=false 보조).
   *
   * filters는 현재 backend 미지원 (ADR #33 — type/mime/owner 등은 후속 트랙). 시그니처는
   * 호출부 drift 회피 위해 보존.
   *
   * 응답 SearchPage{items,nextCursor,totalEstimate}를 FileItem[]로 매핑. file은
   * folderId→parentId, sizeBytes→size; folder는 parentId 그대로(null→'' root). updatedBy는
   * backend 미반환이므로 ''.
   *
   * 에러 envelope: 비-OK 응답은 status 필드 가진 Error throw → QueryCache.onError 401/403 분기.
   * AbortSignal은 fetch에 그대로 전달 (DOMException AbortError → useQuery cancellation).
   */
  async searchFiles(
    params: { q: string; filters: Record<string, unknown> },
    options: { signal?: AbortSignal } = {},
  ): Promise<{ items: FileItem[] }> {
    const { signal } = options
    const q = params.q
    if (!q || q.length < 2) return { items: [] }

    const qs = new URLSearchParams({ q })

    const res = await fetch(`/api/search?${qs.toString()}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
      signal,
    })
    if (!res.ok) {
      const err = new Error(`search fetch failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const page = (await res.json()) as {
      items: Array<
        | {
            type: 'file'
            id: string
            name: string
            folderId: string
            sizeBytes: number | null
            mimeType: string | null
            updatedAt: string
          }
        | {
            type: 'folder'
            id: string
            name: string
            parentId: string | null
            updatedAt: string
          }
      >
      nextCursor: string | null
      totalEstimate: number
    }
    const items: FileItem[] = page.items.map((dto) => {
      if (dto.type === 'folder') {
        return {
          id: dto.id,
          name: dto.name,
          type: 'folder',
          mimeType: null,
          size: null,
          updatedAt: dto.updatedAt,
          updatedBy: '',
          parentId: dto.parentId ?? '',
        }
      }
      return {
        id: dto.id,
        name: dto.name,
        type: 'file',
        mimeType: dto.mimeType,
        size: dto.sizeBytes,
        updatedAt: dto.updatedAt,
        updatedBy: '',
        parentId: dto.folderId,
      }
    })
    return { items }
  },

  /**
   * F6 — 사용자 검색 (A14 / docs/02 §7.14, ADR #35).
   *
   * `GET /api/users/search?q=&limit=`을 fetch.
   * - q.length < 2면 backend 라운드트립 없이 `{items:[]}` 반환 (`useUserSearch.enabled`와 이중 안전).
   * - 호출자(useUserSearch)는 이미 trim+lowercase + 최소 2자 게이트를 통과한 query를 넘김.
   * - limit default 20, backend가 1~50 cap 강제.
   * - 응답 `{items: UserSummary[]}`를 그대로 반환 — 매핑 불요(record 1:1 wire).
   * - 에러 envelope: 비-OK 응답은 status 필드 가진 Error throw → QueryCache.onError 401/403 분기.
   * - AbortSignal은 fetch에 그대로 전달 (DOMException AbortError → useQuery cancellation).
   */
  async searchUsers(
    params: { q: string; limit?: number },
    options: { signal?: AbortSignal } = {},
  ): Promise<{ items: UserSummary[] }> {
    const { signal } = options
    const q = params.q
    if (!q || q.length < 2) return { items: [] }
    const limit = params.limit ?? 20

    const qs = new URLSearchParams({ q, limit: String(limit) })
    const res = await fetch(`/api/users/search?${qs.toString()}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
      signal,
    })
    if (!res.ok) {
      const err = new Error(`users search fetch failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
    return (await res.json()) as { items: UserSummary[] }
  },

  /**
   * A16 — 부서 검색 (docs/02 §7.x, ADR #36).
   *
   * `GET /api/departments/search?q=&limit=`을 fetch. `searchUsers` 1:1 답습 (F6 패턴).
   * - q.length < 2면 backend 라운드트립 없이 `{items:[]}` 반환 (`useDepartmentSearch.enabled`와 이중 안전).
   * - 호출자(useDepartmentSearch)는 이미 trim+lowercase + 최소 2자 게이트를 통과한 query를 넘김.
   * - limit default 20, backend가 1~50 cap 강제.
   * - 응답 `{items: DepartmentSummary[]}`를 그대로 반환 — 매핑 불요(record 1:1 wire).
   * - 에러 envelope: 비-OK 응답은 status 필드 가진 Error throw → QueryCache.onError 401/403 분기.
   * - AbortSignal은 fetch에 그대로 전달 (DOMException AbortError → useQuery cancellation).
   */
  async searchDepartments(
    params: { q: string; limit?: number },
    options: { signal?: AbortSignal } = {},
  ): Promise<{ items: DepartmentSummary[] }> {
    const { signal } = options
    const q = params.q
    if (!q || q.length < 2) return { items: [] }
    const limit = params.limit ?? 20

    const qs = new URLSearchParams({ q, limit: String(limit) })
    const res = await fetch(`/api/departments/search?${qs.toString()}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
      signal,
    })
    if (!res.ok) {
      const err = new Error(
        `departments search fetch failed: ${res.status}`,
      ) as Error & { status: number }
      err.status = res.status
      throw err
    }
    return (await res.json()) as { items: DepartmentSummary[] }
  },

  /**
   * M8 — 사용자의 노드별 effective 권한 (docs/01 §14.2 + docs/02 §7.10 + docs/03 §3.1).
   *
   * F2.1 — backend GET /api/me/effective-permissions 직접 호출 (A11 endpoint).
   * 응답 shape `{ permissions: Permission[] }`을 그대로 반환. role∪resource grant 합산
   * /ADMIN early return / PURGE 제외 평가는 backend IbizDrivePermissionEvaluator가 책임.
   *
   * 에러 envelope: 비-OK 응답은 status 필드 가진 Error throw → QueryCache.onError 401/403 분기.
   */
  async getEffectivePermissions(nodeId?: string): Promise<Permission[]> {
    const qs = nodeId ? `?${new URLSearchParams({ nodeId }).toString()}` : ''
    const res = await fetch(`/api/me/effective-permissions${qs}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(
        `getEffectivePermissions fetch failed: ${res.status}`,
      ) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const data = (await res.json()) as { permissions: Permission[] }
    return data.permissions
  },

  /**
   * M15 — 저장 용량 (docs/01 §18 row 15 — StorageBar).
   *
   * 백엔드 미존재. mock은 75% 사용 placeholder. 실제 quota API 신설 시 본 mock만 fetch로 교체.
   */
  async getStorageQuota(): Promise<{ usedBytes: number; totalBytes: number }> {
    await new Promise((r) => setTimeout(r, 80))
    const totalBytes = 50 * 1024 * 1024 * 1024 // 50 GB
    const usedBytes = Math.round(totalBytes * 0.75) // 75% placeholder
    return { usedBytes, totalBytes }
  },

  /**
   * M-RP.4.2 — 특정 파일의 활동 타임라인 (RightPanel `activity` 탭).
   *
   * backend `GET /api/admin/audit?targetType=file&targetId=<id>&page&pageSize` (ADR #40 RP-2).
   * 권한 분기는 backend AuditQueryService:
   * - ADMIN/AUDITOR: 전체 actor 이벤트
   * - MEMBER + 호출자가 file에 READ 보유: actor 제한 우회 → 모든 사용자의 액션 노출
   * - MEMBER + READ 미보유: 기존 actor=self 정책 (자기 액션만 또는 빈 페이지)
   *
   * 응답 매핑은 `getAuditLogs`와 동일 — actorId/actorName null 폴백 + AuditLogPage shape.
   * MVP 스코프: 페이지 1만 (더보기 v1.x — `useFileActivity`에 옵션 추가 시 hook 인자만 확장).
   */
  async listFileActivity(
    fileId: string,
    page = 1,
    pageSize = 20,
  ): Promise<AuditLogPage> {
    const params = new URLSearchParams()
    params.set('targetType', 'file')
    params.set('targetId', fileId)
    params.set('page', String(page))
    params.set('pageSize', String(pageSize))

    const res = await fetch(`/api/admin/audit?${params.toString()}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(`listFileActivity failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const raw = (await res.json()) as {
      entries: Array<{
        id: string
        occurredAt: string
        eventType: AuditLogEntry['eventType']
        actorId: string | null
        actorName: string | null
        resourceType: AuditLogEntry['resourceType']
        resourceId: string | null
        resourceName: string | null
        ip: string | null
        metadata: Record<string, unknown> | null
      }>
      total: number
      page: number
      pageSize: number
    }
    const entries: AuditLogEntry[] = raw.entries.map((e) => ({
      id: e.id,
      occurredAt: e.occurredAt,
      eventType: e.eventType,
      actorId: e.actorId ?? 'system',
      actorName: e.actorName ?? 'system',
      resourceType: e.resourceType,
      resourceId: e.resourceId,
      resourceName: e.resourceName,
      ip: e.ip,
      metadata: e.metadata,
    }))
    return { entries, total: raw.total, page: raw.page, pageSize: raw.pageSize }
  },

  async getAuditLogs(
    filters: AuditLogFilters = {},
    page = 1,
    pageSize = 20,
  ): Promise<AuditLogPage> {
    // A2.6 — 백엔드 GET /api/admin/audit 직접 호출 (ADR #24, docs/02 §7.12).
    // 동일 origin 가정: prod는 reverse proxy, dev는 next.config rewrites로 8080 backend 포워딩.
    // 세션 쿠키(SPRING_SESSION) 전송을 위해 credentials: 'include'.
    const params = new URLSearchParams()
    if (filters.fromDate) params.set('fromDate', filters.fromDate)
    if (filters.toDate) params.set('toDate', filters.toDate)
    if (filters.actorQuery && filters.actorQuery.trim()) {
      params.set('actorQuery', filters.actorQuery.trim())
    }
    if (filters.eventType) params.set('eventType', filters.eventType)
    params.set('page', String(page))
    params.set('pageSize', String(pageSize))

    const res = await fetch(`/api/admin/audit?${params.toString()}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      // QueryCache.onError가 status로 401/403 분기 — 동일 형태 유지
      const err = new Error(`audit fetch failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const raw = (await res.json()) as {
      entries: Array<{
        id: string
        occurredAt: string
        eventType: AuditLogEntry['eventType']
        actorId: string | null
        actorName: string | null
        resourceType: AuditLogEntry['resourceType']
        resourceId: string | null
        resourceName: string | null
        ip: string | null
        metadata: Record<string, unknown> | null
      }>
      total: number
      page: number
      pageSize: number
    }
    // 백엔드 actorId/actorName은 null 가능(시스템 이벤트, 미존재 사용자) → 프론트 string 계약 폴백.
    const entries: AuditLogEntry[] = raw.entries.map((e) => ({
      id: e.id,
      occurredAt: e.occurredAt,
      eventType: e.eventType,
      actorId: e.actorId ?? 'system',
      actorName: e.actorName ?? 'system',
      resourceType: e.resourceType,
      resourceId: e.resourceId,
      resourceName: e.resourceName,
      ip: e.ip,
      metadata: e.metadata,
    }))
    return { entries, total: raw.total, page: raw.page, pageSize: raw.pageSize }
  },

  // ──────────────────────────────────────────────────────────────────
  // M9.1 — 휴지통 (docs/02 §7.11, ADR #32)
  // backend: com.ibizdrive.trash.TrashController + Per-resource restore.
  // ──────────────────────────────────────────────────────────────────

  /**
   * 휴지통 목록 조회. backend는 사용자 DELETE 권한 후처리 결과만 반환 (ADR #32).
   * cursor는 직전 응답의 nextCursor를 그대로 echo back. type 미지정 = file+folder 양쪽.
   */
  async getTrash(opts: { cursor?: string; type?: TrashItemType } = {}): Promise<TrashPage> {
    const params = new URLSearchParams()
    if (opts.cursor) params.set('cursor', opts.cursor)
    if (opts.type) params.set('type', opts.type)
    const qs = params.toString()
    const url = qs ? `/api/trash?${qs}` : '/api/trash'
    const res = await fetch(url, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(`getTrash failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const raw = (await res.json()) as {
      items: Array<{
        id: string
        name: string
        type: TrashItemType
        deletedAt: string
        purgeAfter: string
        // backend record는 NON_NULL 직렬화라 root였던 폴더는 키 자체가 없을 수 있다.
        originalParentId?: string | null
      }>
      // 마지막 페이지에서는 NON_NULL로 키 생략될 수 있다.
      nextCursor?: string | null
    }
    const items: TrashItem[] = raw.items.map((it) => ({
      id: it.id,
      name: it.name,
      type: it.type,
      deletedAt: it.deletedAt,
      purgeAfter: it.purgeAfter,
      originalParentId: it.originalParentId ?? null,
    }))
    return { items, nextCursor: raw.nextCursor ?? null }
  },

  /**
   * 휴지통 파일 복원. 409 RESTORE_CONFLICT는 backend envelope { error: { code, message, details } }
   * 를 파싱해 err.code='RESTORE_CONFLICT'로 surface — UX layer가 RenameDialog 분기 가능.
   */
  async restoreFile(id: string): Promise<void> {
    const res = await fetch(`/api/files/${encodeURIComponent(id)}/restore`, {
      method: 'POST',
      credentials: 'include',
    })
    if (!res.ok) {
      throw await buildApiError(res, `restoreFile failed: ${res.status}`)
    }
  },

  /**
   * 휴지통 폴더 복원. 409 시 envelope code 동일하게 'RESTORE_CONFLICT'로 throw.
   */
  async restoreFolder(id: string): Promise<void> {
    const res = await fetch(`/api/folders/${encodeURIComponent(id)}/restore`, {
      method: 'POST',
      credentials: 'include',
    })
    if (!res.ok) {
      throw await buildApiError(res, `restoreFolder failed: ${res.status}`)
    }
  },

  /**
   * 휴지통 항목 영구 삭제 (ADMIN-only). 비-ADMIN은 backend 403 폴백 — 프론트 가드는 UX용.
   * 응답 204 NO_CONTENT.
   */
  async purgeTrashItem(type: TrashItemType, id: string): Promise<void> {
    const res = await fetch(
      `/api/trash/${encodeURIComponent(type)}/${encodeURIComponent(id)}`,
      {
        method: 'DELETE',
        credentials: 'include',
      },
    )
    if (!res.ok) {
      const err = new Error(`purgeTrashItem failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
  },

  /**
   * 파일 공유 생성 — F4 (docs/02 §7.9, ADR #34).
   * 한 번의 호출로 multi-subject grant. backend는 단일 트랜잭션 + 부분 성공 없음 (UNIQUE 위반 시 전체 rollback).
   * 에러 envelope { error: { code, message, details } } → buildApiError로 status/code 매핑.
   *   400 BAD_REQUEST       — subjects empty / message > 1000 / expiresAt past / subject_id ↔ everyone 위반
   *   403 PERMISSION_DENIED — SHARE 권한 미보유
   *   404 NOT_FOUND         — file 미존재 / soft-deleted
   *   409 PERMISSION_CONFLICT — 동일 file × subject 중복 grant
   */
  async createFileShares(fileId: string, req: ShareCreateRequest): Promise<ShareDto[]> {
    return postShareCreate(`/api/files/${encodeURIComponent(fileId)}/share`, req, 'createFileShares')
  },

  /**
   * 폴더 공유 생성 — F5 / A12 (docs/02 §7.9, ADR #34).
   * file 변형과 동일 envelope ({@code { shares: ShareDto[] }}). 응답 row는 `folderId` NOT NULL,
   * `fileId` NULL (V6 XOR CHECK). 에러 매핑 동일.
   */
  async createFolderShares(folderId: string, req: ShareCreateRequest): Promise<ShareDto[]> {
    return postShareCreate(`/api/folders/${encodeURIComponent(folderId)}/share`, req, 'createFolderShares')
  },

  /**
   * 공유 revoke — F4. backend `canRevoke = sharedBy == me || role == ADMIN`.
   * 응답 204 NO_CONTENT. 이미 revoked된 share는 404로 폴백 (멱등).
   */
  async revokeShare(shareId: string): Promise<void> {
    const res = await fetch(`/api/shares/${encodeURIComponent(shareId)}`, {
      method: 'DELETE',
      credentials: 'include',
    })
    if (!res.ok) {
      throw await buildApiError(res, `revokeShare failed: ${res.status}`)
    }
  },

  /**
   * 내가 공유한 목록 — F4. cursor/limit 둘 다 optional. backend가 nextCursor를 echo하지 않으면 null 폴백.
   */
  async listSharesByMe(opts: { cursor?: string; limit?: number } = {}): Promise<SharePage> {
    return fetchSharePage('/api/shares/by-me', opts)
  },

  /**
   * 받은 공유 목록 — F4 (MVP: subject_type='user' 매칭만).
   * department/role/everyone 으로 받은 share는 별도 트랙 (ADR #34 backlog).
   */
  async listSharesWithMe(opts: { cursor?: string; limit?: number } = {}): Promise<SharePage> {
    return fetchSharePage('/api/shares/with-me', opts)
  },

  // ── 인증 (auth-pages, ADR #41) ──────────────────────────────────────────

  /**
   * `POST /api/auth/signup` (ADR #41). 회원가입 + 자동 세션 발급. CSRF 토큰 미필요
   * (SecurityConfig가 ignoringRequestMatchers로 면제).
   *
   * 응답 body는 login과 동일 shape ({@link AuthSession}). 첫 user는 backend에서 ADMIN으로 결정.
   * 409 DUPLICATE_EMAIL / 400 VALIDATION_ERROR는 buildApiError로 status+code 부여 throw.
   */
  async signup(params: SignupParams): Promise<AuthSession> {
    const res = await fetch('/api/auth/signup', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(params),
    })
    if (!res.ok) {
      throw await buildApiError(res, `signup failed: ${res.status}`)
    }
    return (await res.json()) as AuthSession
  },

  /**
   * `POST /api/auth/login`. CSRF 토큰 필수 — 사전 발급(`/api/auth/csrf` 쿠키 GET) 후 헤더 동봉.
   * `ensureCsrfToken`이 캐시된 쿠키가 없으면 자동 부트스트랩.
   *
   * 401 UNAUTHORIZED/INVALID_CREDENTIALS, 423 ACCOUNT_LOCKED는 status+code로 분기 가능.
   */
  async login(params: LoginParams): Promise<AuthSession> {
    const csrf = await ensureCsrfToken()
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify(params),
    })
    if (!res.ok) {
      throw await buildApiError(res, `login failed: ${res.status}`)
    }
    return (await res.json()) as AuthSession
  },

  /**
   * `POST /api/auth/logout`. 204 NO_CONTENT, SESSION 쿠키 만료. 미인증(401) 시에도 호출자는
   * 폴백 처리하지 않고 그대로 throw — UI 측에서 어쨌든 로그인 화면 redirect.
   */
  async logout(): Promise<void> {
    const csrf = await ensureCsrfToken()
    const res = await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-CSRF-TOKEN': csrf },
    })
    if (!res.ok) {
      throw await buildApiError(res, `logout failed: ${res.status}`)
    }
  },

  /**
   * `GET /api/auth/me`. 인증된 사용자 정보 조회. 401(미인증)은 caller가 분기 가능하도록
   * status 부여 throw — `useMe`가 catch → null 반환.
   */
  async me(): Promise<AuthSession> {
    const res = await fetch('/api/auth/me', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `me failed: ${res.status}`)
    }
    return (await res.json()) as AuthSession
  },
}

/**
 * POST /api/{files|folders}/:id/share 공통 fetch — body JSON + 201 응답 envelope `{ shares: ShareDto[] }` 매핑.
 * F5에서 file/folder 두 변형이 라우트만 다르고 형상이 동일하므로 helper로 추출.
 */
async function postShareCreate(
  path: string,
  req: ShareCreateRequest,
  errorLabel: string,
): Promise<ShareDto[]> {
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) {
    throw await buildApiError(res, `${errorLabel} failed: ${res.status}`)
  }
  const data = (await res.json()) as { shares: ShareDto[] }
  return data.shares
}

/**
 * GET /api/shares/{by-me|with-me} 공통 fetch — cursor/limit 쿼리 + nextCursor 폴백.
 */
async function fetchSharePage(
  path: string,
  opts: { cursor?: string; limit?: number },
): Promise<SharePage> {
  const params = new URLSearchParams()
  if (opts.cursor) params.set('cursor', opts.cursor)
  if (opts.limit !== undefined) params.set('limit', String(opts.limit))
  const qs = params.toString()
  const url = qs ? `${path}?${qs}` : path
  const res = await fetch(url, { credentials: 'include' })
  if (!res.ok) {
    throw await buildApiError(res, `${path} failed: ${res.status}`)
  }
  const raw = (await res.json()) as {
    items: ShareDto[]
    nextCursor?: string | null
  }
  return { items: raw.items, nextCursor: raw.nextCursor ?? null }
}

/**
 * backend ApiError envelope { error: { code, message, details } } 를 안전 파싱해
 * Error에 status/code를 부여한다. JSON 파싱 실패 시 status만 부여 — UX layer가 status로 분기 가능.
 *
 * <p>인증 endpoint(docs/02 §7.4)는 flat shape `{ code, reason, retryAfterSec, details }`을 사용 —
 * envelope.error.code 미존재 시 root code를 폴백으로 사용 (login 401 INVALID_CREDENTIALS,
 * signup 409 DUPLICATE_EMAIL, 400 VALIDATION_ERROR).
 */
async function buildApiError(res: Response, fallbackMessage: string): Promise<Error> {
  const err = new Error(fallbackMessage) as Error & {
    status: number
    code?: string
    reason?: string
  }
  err.status = res.status
  try {
    const body = (await res.json()) as {
      error?: { code?: string }
      code?: string
      reason?: string
    }
    if (body?.error?.code) err.code = body.error.code
    else if (body?.code) err.code = body.code
    if (body?.reason) err.reason = body.reason
  } catch {
    // 본문이 없거나 JSON이 아니면 status만으로 충분 (audit 패턴 일관)
  }
  return err
}

/**
 * CSRF token cookie(`XSRF-TOKEN`) 조회. 없으면 backend `/api/auth/csrf` GET으로 부트스트랩.
 * Spring Security `CookieCsrfTokenRepository.withHttpOnlyFalse()`가 발급하는 쿠키 (HttpOnly=false)이므로
 * `document.cookie`로 읽을 수 있다. login/logout 등 mutation 직전 1회 호출.
 *
 * SSR 컨텍스트(typeof document === 'undefined')에서는 빈 문자열 반환 — 호출은 항상 클라이언트
 * 컴포넌트에서 발생하므로 실용 영향 없음.
 */
async function ensureCsrfToken(): Promise<string> {
  if (typeof document === 'undefined') return ''
  let token = readCookie('XSRF-TOKEN')
  if (!token) {
    await fetch('/api/auth/csrf', { method: 'GET', credentials: 'include' })
    token = readCookie('XSRF-TOKEN')
  }
  return token ?? ''
}

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]+)`))
  return match ? decodeURIComponent(match[1]) : null
}

