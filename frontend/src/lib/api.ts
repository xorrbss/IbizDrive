import type { FolderNode, FolderDetail } from '@/types/folder'
import type { FileItem, SortKey } from '@/types/file'
import type { AuditLogEntry, AuditLogFilters, AuditLogPage } from '@/types/audit'
import type { Permission } from '@/types/permission'
import type { TrashItem, TrashItemType, TrashPage } from '@/types/trash'
import type { ShareCreateRequest, ShareDto, SharePage } from '@/types/share'
import { FakeXHR } from './fakeXhr'
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

  // M5: FakeXHR 반환. 실제 백엔드 도입 시 내부 구현만 XMLHttpRequest로 교체.
  uploadFile(params: {
    file: File
    folderId: string
    resolution?: 'new_version' | 'rename'
    newName?: string
  }): FakeXHR {
    const effectiveName = params.newName ?? params.file.name
    // 충돌 검사: resolution이 없고 동일 폴더에 같은 이름이 있으면 409 시뮬레이션
    // (resolution='new_version'|'rename'은 이미 사용자가 해결 방법을 선택한 상태이므로 스킵)
    const conflictExisting = !params.resolution
      ? MOCK_FILES.find(
          (f) => f.parentId === params.folderId && f.name === effectiveName,
        )
      : null
    const xhr = new FakeXHR({
      simulateConflict: conflictExisting
        ? { fileId: conflictExisting.id, fileName: conflictExisting.name }
        : null,
      // 200 성공 시 MOCK_FILES에 반영 (실제 서버의 커밋 단계를 시뮬레이션)
      onServerSuccess: () => {
        const now = new Date().toISOString()
        if (params.resolution === 'new_version') {
          const existing = MOCK_FILES.find(
            (f) => f.parentId === params.folderId && f.name === effectiveName,
          )
          if (existing) {
            existing.size = params.file.size
            existing.updatedAt = now
            existing.updatedBy = '나'
            existing.mimeType = params.file.type || existing.mimeType
            return
          }
        }
        MOCK_FILES.push({
          id: `file_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          name: effectiveName,
          type: 'file',
          mimeType: params.file.type || null,
          size: params.file.size,
          updatedAt: now,
          updatedBy: '나',
          parentId: params.folderId,
        })
      },
    })
    const form = new FormData()
    const fileToSend = params.newName
      ? new File([params.file], params.newName, { type: params.file.type })
      : params.file
    form.append('file', fileToSend)
    form.append('folderId', params.folderId)
    if (params.resolution) form.append('resolution', params.resolution)

    const url = `/files/upload?folderId=${encodeURIComponent(params.folderId)}${
      params.resolution ? `&resolution=${params.resolution}` : ''
    }`
    xhr.open('POST', url)
    xhr.send(form)
    return xhr
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
  async createShares(fileId: string, req: ShareCreateRequest): Promise<ShareDto[]> {
    const res = await fetch(`/api/files/${encodeURIComponent(fileId)}/share`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    })
    if (!res.ok) {
      throw await buildApiError(res, `createShares failed: ${res.status}`)
    }
    const data = (await res.json()) as { shares: ShareDto[] }
    return data.shares
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
 */
async function buildApiError(res: Response, fallbackMessage: string): Promise<Error> {
  const err = new Error(fallbackMessage) as Error & { status: number; code?: string }
  err.status = res.status
  try {
    const body = (await res.json()) as { error?: { code?: string } }
    if (body?.error?.code) err.code = body.error.code
  } catch {
    // 본문이 없거나 JSON이 아니면 status만으로 충분 (audit 패턴 일관)
  }
  return err
}

