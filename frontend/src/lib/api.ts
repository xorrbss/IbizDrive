import type { BreadcrumbItem, FolderDetail } from '@/types/folder'
import type { FileItem, SortKey } from '@/types/file'
import type { AuditLogEntry, AuditLogFilters, AuditLogPage } from '@/types/audit'
import type {
  AdminPermissionFilters,
  AdminPermissionPage,
  Permission,
  PermissionListItem,
} from '@/types/permission'
import type {
  AdminTrashFilters,
  AdminTrashPage,
  TrashItem,
  TrashItemType,
  TrashPage,
} from '@/types/trash'
import type { FileVersionDto } from '@/types/version'
import type { ShareCreateRequest, ShareDto, SharePage } from '@/types/share'
import type { UserSummary } from '@/types/user'
import type {
  AdminDepartmentCreateBody,
  AdminDepartmentPage,
  AdminDepartmentPatchBody,
  AdminDepartmentSummary,
  DepartmentSummary,
} from '@/types/department'
import type { AuthSession, LoginParams, SignupParams } from '@/types/auth'
import type { CronJobsResponse } from '@/types/system'
import type { AdminStorageOverviewResponse } from '@/types/admin-storage'
import type { AdminDashboardSummaryResponse } from '@/types/admin'
import type { WorkspaceMeResponse } from '@/types/workspace'
import type { TeamCreateRequest, TeamResponse } from '@/types/team'
import { normalizeFileName } from '@/lib/normalize'

export const api = {
  /**
   * Phase A — backend `GET /api/folders/{id}` 호출. 응답의 단일-segment breadcrumb을
   * 누적 slugPath 형태로 prefix-scan하여 frontend `BreadcrumbItem` 계약 (slugPath: string[])에
   * 맞춘다. 가상 root({@code id='root'})는 backend 호출 없이 합성 응답으로 처리.
   */
  async getFolder(id: string): Promise<FolderDetail> {
    if (id === 'root') {
      return {
        id: 'root',
        name: '내 드라이브',
        slugPath: [],
        breadcrumb: [{ id: 'root', name: '내 드라이브', slugPath: [] }],
        parentId: null,
      }
    }
    const res = await fetch(`/api/folders/${encodeURIComponent(id)}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(
        `getFolder fetch failed: ${res.status}`,
      ) as Error & { status: number; code?: string }
      err.status = res.status
      if (res.status === 404) err.code = 'NOT_FOUND'
      throw err
    }
    const body = (await res.json()) as {
      folder: { id: string; name: string }
      breadcrumb: { id: string; name: string; slug: string }[]
    }
    // 누적 slug — backend는 단일 segment만 보내므로 prefix scan으로 조립.
    const slugPath = body.breadcrumb.map((c) => c.slug)
    // 가상 root를 breadcrumb 앞에 합성 — frontend 라우팅이 root crumb을 기대.
    const breadcrumb: BreadcrumbItem[] = [
      { id: 'root', name: '내 드라이브', slugPath: [] },
      ...body.breadcrumb.map((c, i) => ({
        id: c.id,
        name: c.name,
        slugPath: slugPath.slice(0, i + 1),
      })),
    ]
    const parentBcrumb = body.breadcrumb[body.breadcrumb.length - 2]
    return {
      id: body.folder.id,
      name: body.folder.name,
      slugPath,
      breadcrumb,
      parentId: parentBcrumb ? parentBcrumb.id : 'root',
    }
  },

  /**
   * spec §5.2 — 사이드바 첫 fetch + permission cache 진입.
   * backend `WorkspaceMeResponse` 1:1. `department` 키는 Jackson @JsonInclude(NON_NULL) — null 시 응답에서 omit되므로 일괄 `?? null` 보정.
   */
  async getWorkspacesMe(): Promise<WorkspaceMeResponse> {
    const res = await fetch('/api/workspaces/me', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(
        `getWorkspacesMe fetch failed: ${res.status}`,
      ) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const body = (await res.json()) as Partial<WorkspaceMeResponse>
    return {
      department: body.department ?? null,
      teams: body.teams ?? [],
    }
  },

  /**
   * Plan B Task 25 — 팀 생성 ({@code POST /api/teams}).
   * backend {@code TeamController.create} → {@code TeamResponse}.
   * CSRF double-submit — mutation endpoints 공통 정책 (createFolder 참조).
   */
  async createTeam(req: TeamCreateRequest): Promise<TeamResponse> {
    const csrf = readCookie('XSRF-TOKEN')
    const res = await fetch('/api/teams', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(csrf ? { 'X-CSRF-TOKEN': csrf } : {}),
      },
      body: JSON.stringify(req),
    })
    if (!res.ok) {
      throw await buildApiError(res, `createTeam failed: ${res.status}`)
    }
    return (await res.json()) as TeamResponse
  },

  /**
   * Plan B — 폴더 자식(폴더+파일) listing.
   *
   * 가상 root 분기 제거 (Task 12). 모든 호출부는 실제 workspace root folder UUID를 전달한다.
   *
   * {@code GET /api/folders/{id}/items?sort=&dir=} 호출. backend가 폴더-먼저 → 파일 그룹 정렬 적용.
   * 응답 envelope의 {@code FolderItemDto}({type:'folder'|'file', size:Long?|null, mimeType:String?|null,
   * updatedBy:UUID?})를 frontend {@link FileItem} 1:1 mapping. backend는 {@code @JsonInclude(NON_NULL)}이므로
   * 폴더의 size/mimeType 키 자체가 응답에 없을 수 있다 — 매핑 시 null 보정.
   */
  async getFilesInFolder(
    folderId: string,
    sort: SortKey = 'name',
    dir: 'asc' | 'desc' = 'asc',
  ): Promise<FileItem[]> {
    const url =
      `/api/folders/${encodeURIComponent(folderId)}/items` +
      `?sort=${encodeURIComponent(sort.toUpperCase())}` +
      `&dir=${encodeURIComponent(dir.toUpperCase())}`
    const res = await fetch(url, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `getFilesInFolder fetch failed: ${res.status}`)
    }
    const body = (await res.json()) as {
      items: Array<{
        id: string
        type: 'folder' | 'file'
        name: string
        mimeType?: string | null
        size?: number | null
        updatedAt: string
        updatedBy?: string | null
        parentId?: string | null
      }>
    }
    return body.items.map((it) => ({
      id: it.id,
      name: it.name,
      type: it.type,
      mimeType: it.mimeType ?? null,
      size: it.size ?? null,
      updatedAt: it.updatedAt,
      updatedBy: it.updatedBy ?? '',
      parentId: it.parentId ?? folderId,
    }))
  },

  /**
   * Plan B 사이드바 트리 lazy children — folder-only.
   * GET /api/folders/{id}/items 호출 후 type='folder'만 필터링.
   * `slug`는 `normalizeFileName(name)`으로 생성 — 기존 buildCanonicalPath 동치.
   */
  async getFolderChildren(parentId: string): Promise<{
    id: string
    name: string
    slug: string
    parentId: string
  }[]> {
    const url = `/api/folders/${encodeURIComponent(parentId)}/items?sort=NAME&dir=ASC`
    const res = await fetch(url, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(`getFolderChildren failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const body = (await res.json()) as {
      items: Array<{ id: string; type: 'folder' | 'file'; name: string }>
    }
    return body.items
      .filter((it) => it.type === 'folder')
      .map((it) => ({
        id: it.id,
        name: it.name,
        slug: normalizeFileName(it.name),
        parentId,
      }))
  },

  /**
   * Phase B P3 — 파일 상세 (RightPanel meta 탭).
   *
   * <p>{@code GET /api/files/{id}} 응답 envelope `{ file: FileDto }`. frontend {@link FileItem}으로 mapping.
   * 활성 파일만 (backend service 정책) — 부재/soft-delete 모두 404.
   */
  async getFileDetail(id: string): Promise<FileItem> {
    const res = await fetch(`/api/files/${encodeURIComponent(id)}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `getFileDetail fetch failed: ${res.status}`)
    }
    const body = (await res.json()) as {
      file: {
        id: string
        folderId: string
        name: string
        ownerId: string
        sizeBytes: number
        mimeType?: string | null
        currentVersionId?: string | null
        createdAt: string
        updatedAt: string
      }
    }
    const f = body.file
    return {
      id: f.id,
      name: f.name,
      type: 'file',
      mimeType: f.mimeType ?? null,
      size: f.sizeBytes,
      updatedAt: f.updatedAt,
      updatedBy: f.ownerId,
      parentId: f.folderId,
    }
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
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(
      `/api/files/${encodeURIComponent(fileId)}/versions/${encodeURIComponent(versionId)}/restore`,
      {
        method: 'POST',
        credentials: 'include',
        headers: { Accept: 'application/json', 'X-CSRF-TOKEN': csrf },
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
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(`/api/files/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: { 'X-CSRF-TOKEN': csrf },
    })
    if (!res.ok) {
      const err = new Error(`softDeleteFile failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
  },

  async softDeleteFolder(id: string): Promise<void> {
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(`/api/folders/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: { 'X-CSRF-TOKEN': csrf },
    })
    if (!res.ok) {
      const err = new Error(`softDeleteFolder failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
  },

  /**
   * Phase B P3 — 단건 이동(file/folder 분기). useMoveBulk이 fanout으로 호출.
   *
   * <p>backend는 단건 endpoint만 제공 ({@code POST /api/{files|folders}/{id}/move}). all-or-nothing은 v1.x bulk endpoint.
   * MOVE_INTO_SELF/MOVE_INTO_DESCENDANT는 backend FolderMutationService/FileMutationService가 검증 → 400 envelope.
   */
  async moveItem(
    id: string,
    type: 'file' | 'folder',
    targetFolderId: string,
  ): Promise<void> {
    const url =
      type === 'folder'
        ? `/api/folders/${encodeURIComponent(id)}/move`
        : `/api/files/${encodeURIComponent(id)}/move`
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify({ targetFolderId }),
    })
    if (!res.ok) {
      throw await buildApiError(res, `moveItem failed: ${res.status}`)
    }
  },

  /**
   * Phase B P3 — 묶음 이동 fanout. backend 단건 endpoint를 {@link Promise.all}로 병렬 호출.
   *
   * <p>signature 변경 (이전 ids: string[] → items: {id, type}[]) — useDeleteBulk 패턴 답습. 첫 rejection이 전체
   * 결정 (부분 성공 정밀 처리는 v1.x bulk endpoint).
   */
  async moveFiles(
    items: Array<{ id: string; type: 'file' | 'folder' }>,
    targetFolderId: string,
  ): Promise<{ movedIds: string[] }> {
    await Promise.all(items.map((it) => this.moveItem(it.id, it.type, targetFolderId)))
    return { movedIds: items.map((it) => it.id) }
  },

  /**
   * Phase B P3 — 이름 변경 (file/folder 분기).
   *
   * <p>backend {@code PATCH /api/{files|folders}/{id}} 호출. body는 {@code { name }}. 응답은 {@code { file|folder }}
   * envelope. frontend는 {@link FileItem} 형태로 정규화하여 반환.
   *
   * <p>{@code isFolder} param은 caller(useRenameFile)가 selection의 type 정보로 명시 — backend는 키 분리(folder/file
   * UUID 시퀀스 별도)이므로 유추 불가.
   */
  async renameFile(
    id: string,
    newName: string,
    isFolder = false,
  ): Promise<FileItem> {
    const trimmed = newName.trim()
    if (trimmed.length === 0) {
      // server validation으로 충분하지만 UX(즉시 inline error)를 위한 client-side 가드 유지.
      throw { status: 400, code: 'VALIDATION_ERROR' }
    }

    const url = isFolder
      ? `/api/folders/${encodeURIComponent(id)}`
      : `/api/files/${encodeURIComponent(id)}`
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(url, {
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify({ name: trimmed }),
    })
    if (!res.ok) {
      throw await buildApiError(res, `renameFile failed: ${res.status}`)
    }

    const body = (await res.json()) as
      | { file: { id: string; folderId: string; name: string; ownerId: string; sizeBytes: number; mimeType?: string | null; updatedAt: string } }
      | { folder: { id: string; parentId?: string | null; name: string; ownerId?: string; updatedAt: string } }

    if (isFolder) {
      const f = (body as { folder: { id: string; parentId?: string | null; name: string; ownerId?: string; updatedAt: string } }).folder
      return {
        id: f.id,
        name: f.name,
        type: 'folder',
        mimeType: null,
        size: null,
        updatedAt: f.updatedAt,
        updatedBy: f.ownerId ?? '',
        parentId: f.parentId ?? 'root',
      }
    }
    const f = (body as { file: { id: string; folderId: string; name: string; ownerId: string; sizeBytes: number; mimeType?: string | null; updatedAt: string } }).file
    return {
      id: f.id,
      name: f.name,
      type: 'file',
      mimeType: f.mimeType ?? null,
      size: f.sizeBytes,
      updatedAt: f.updatedAt,
      updatedBy: f.ownerId,
      parentId: f.folderId,
    }
  },

  /**
   * Phase B P3 — 폴더 생성 ({@code POST /api/folders}). body는 {@code { parentId, name }}.
   * 응답은 {@code { folder: FolderDto }}. 가상 root에서 생성하면 caller가 parentId를 실제 root UUID로 변환.
   * (v1.x ADR 미정 — 현재 호출부 미존재로 backend root UUID 정책 결정 시 재검토.)
   */
  async createFolder(parentId: string, name: string): Promise<{ id: string; name: string; parentId: string | null }> {
    // CSRF double-submit — 누락 시 backend SecurityFilter가 PermissionEvaluator 도달 전 403.
    // 다른 mutation들과 동일 패턴(rename/move/share 등). 운영자가 ADMIN role임에도
    // "폴더를 만들 권한이 없습니다"로 표시되던 회귀 (UI는 403을 권한 메시지로 매핑).
    const csrf = readCookie('XSRF-TOKEN')
    const res = await fetch('/api/folders', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(csrf ? { 'X-CSRF-TOKEN': csrf } : {}),
      },
      body: JSON.stringify({ parentId, name: name.trim() }),
    })
    if (!res.ok) {
      throw await buildApiError(res, `createFolder failed: ${res.status}`)
    }
    const body = (await res.json()) as {
      folder: { id: string; parentId?: string | null; name: string }
    }
    return {
      id: body.folder.id,
      name: body.folder.name,
      parentId: body.folder.parentId ?? null,
    }
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
    // CSRF 토큰: 로그인 후 XSRF-TOKEN 쿠키가 항상 존재. 타 mutation들과 동일 정책 (login/logout 참고).
    // 누락 시 Spring CSRF 필터가 403 반환 → uploadErrors가 "권한 없음"으로 오해 매핑되는 회귀 방지.
    const csrf = readCookie('XSRF-TOKEN')
    if (csrf) xhr.setRequestHeader('X-CSRF-Token', csrf)
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
   * M8.1 — 리소스에 부여된 grant 목록 (docs/02 §7.10 — `GET /api/{folders|files}/:id/permissions`).
   *
   * BE 가드: `@PreAuthorize hasPermission(#id, #resource, 'PERMISSION_ADMIN')` — 관리자만 호출 가능.
   * 응답 envelope `{ items: PermissionListItem[] }` 의 `items` 만 풀어 반환 (페이지네이션 없음 — BE
   * 가 단일 리소스 grant 수가 작다고 가정). 정렬은 BE 가 created_at ASC, id ASC 보장.
   *
   * `subjectName` 은 BE 가 user/department batch resolve 한 표시명 (A16 ShareDto 동형). soft-delete /
   * everyone / 미해결은 null — 컴포넌트 측 fallback.
   *
   * 에러: 비-OK 응답은 status 필드 가진 Error throw (getEffectivePermissions 와 동일 패턴 —
   * QueryCache.onError 401/403/404 분기).
   *
   * @param resourceType 'folder' | 'file' — URL 경로에서 복수형 ('folders'/'files') 으로 변환.
   *   BE controller `normalizeResourceType` 이 양쪽 형태를 흡수하나, FE 는 컨벤션상 복수형 사용.
   */
  async listResourcePermissions(
    resourceType: 'folder' | 'file',
    id: string,
  ): Promise<PermissionListItem[]> {
    const res = await fetch(`/api/${resourceType}s/${id}/permissions`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(
        `listResourcePermissions fetch failed: ${res.status}`,
      ) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const data = (await res.json()) as { items: PermissionListItem[] }
    return data.items
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

  /**
   * 감사 로그 server-side export URL 빌더 (Wave 1 — T2, audit-export-json 트랙으로 format 추가).
   *
   * <p>실제 다운로드는 anchor element의 `href`/`download`로 트리거 — fetch가 아닌 브라우저
   * navigation을 사용해야 Content-Disposition을 그대로 인식한다. 호출 측은 본 URL을
   * `<a href>`에 그대로 넣고 click한다.
   *
   * <p>backend `GET /api/admin/audit/export` 가드: `@PreAuthorize(AUDITOR or ADMIN)` — MEMBER는 403.
   * `format`은 `'csv'`, `'json'`, 또는 `'ndjson'` (audit-ndjson 트랙으로 추가). backend에서 그 외
   * 값은 400 BAD_REQUEST. 본 함수는 URL만 빌드하므로 권한 가드는 backend가 단독 책임
   * (UX는 호출 측 페이지가 분기).
   */
  getAuditLogsExportUrl(
    filters: AuditLogFilters = {},
    format: 'csv' | 'json' | 'ndjson' = 'csv'
  ): string {
    const params = new URLSearchParams()
    if (filters.fromDate) params.set('fromDate', filters.fromDate)
    if (filters.toDate) params.set('toDate', filters.toDate)
    if (filters.actorQuery && filters.actorQuery.trim()) {
      params.set('actorQuery', filters.actorQuery.trim())
    }
    if (filters.eventType) params.set('eventType', filters.eventType)
    params.set('format', format)
    return `/api/admin/audit/export?${params.toString()}`
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
   * 휴지통 파일 복원. opts.newName 미지정 시 원본 이름 그대로 복원, 충돌 시 envelope code
   * 'RESTORE_CONFLICT' (UX layer 가 RestoreConflictDialog 분기). opts.newName 지정 시 정규화 후
   * 새 이름으로 복원, 충돌 시 'RENAME_CONFLICT' (다이얼로그 inline alert).
   */
  async restoreFile(id: string, opts?: { newName?: string }): Promise<void> {
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(`/api/files/${encodeURIComponent(id)}/restore`, {
      method: 'POST',
      credentials: 'include',
      headers:
        opts?.newName !== undefined
          ? { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf }
          : { 'X-CSRF-TOKEN': csrf },
      ...(opts?.newName !== undefined
        ? { body: JSON.stringify({ name: opts.newName }) }
        : {}),
    })
    if (!res.ok) {
      throw await buildApiError(res, `restoreFile failed: ${res.status}`)
    }
  },

  /**
   * 휴지통 폴더 복원. opts.newName 동일 시맨틱 (RESTORE_CONFLICT vs RENAME_CONFLICT 분기는 file 과 동일).
   */
  async restoreFolder(id: string, opts?: { newName?: string }): Promise<void> {
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(`/api/folders/${encodeURIComponent(id)}/restore`, {
      method: 'POST',
      credentials: 'include',
      headers:
        opts?.newName !== undefined
          ? { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf }
          : { 'X-CSRF-TOKEN': csrf },
      ...(opts?.newName !== undefined
        ? { body: JSON.stringify({ name: opts.newName }) }
        : {}),
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
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(
      `/api/trash/${encodeURIComponent(type)}/${encodeURIComponent(id)}`,
      {
        method: 'DELETE',
        credentials: 'include',
        headers: { 'X-CSRF-TOKEN': csrf },
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
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(`/api/shares/${encodeURIComponent(shareId)}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: { 'X-CSRF-TOKEN': csrf },
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

  // ── 비밀번호 재설정/변경 (a1.5) ───────────────────────────────────────────

  /**
   * `POST /api/auth/password/forgot`. anti-enumeration — 가입/미가입 무관 200 동일 응답.
   * SecurityConfig가 ignoringRequestMatchers로 CSRF 면제 (signup 패턴 동일).
   */
  async passwordForgot(email: string): Promise<{ message: string }> {
    const res = await fetch('/api/auth/password/forgot', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ email }),
    })
    if (!res.ok) {
      throw await buildApiError(res, `passwordForgot failed: ${res.status}`)
    }
    return (await res.json()) as { message: string }
  },

  /**
   * `POST /api/auth/password/reset`. 토큰 + 새 비밀번호. 토큰 무효 시 400 INVALID_TOKEN.
   * CSRF 면제 (signup/forgot 동일 정책).
   */
  async passwordReset(token: string, newPassword: string): Promise<{ message: string }> {
    const res = await fetch('/api/auth/password/reset', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ token, newPassword }),
    })
    if (!res.ok) {
      throw await buildApiError(res, `passwordReset failed: ${res.status}`)
    }
    return (await res.json()) as { message: string }
  },

  /**
   * `POST /api/auth/password/change`. 인증 + CSRF 필수. 현재 PW 미일치 시 401 INVALID_CREDENTIALS.
   * 성공 시 다른 모든 세션이 invalidate되며 현재 세션은 유지된다.
   */
  async passwordChange(currentPassword: string, newPassword: string): Promise<{ message: string }> {
    const csrf = await ensureCsrfToken()
    const res = await fetch('/api/auth/password/change', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify({ currentPassword, newPassword }),
    })
    if (!res.ok) {
      throw await buildApiError(res, `passwordChange failed: ${res.status}`)
    }
    return (await res.json()) as { message: string }
  },

  // ── Admin invite (m-admin-entry-rewrite, ADR #21) ──────────────────────

  /**
   * `POST /api/admin/users` (m-admin-entry-rewrite P6/P7, docs/02 §7.4).
   *
   * <p>관리자가 신규 user를 초대. backend는 임시 PW 16자 자동 생성 + email 발송 + audit emit
   * (admin.user.created). 응답에는 임시 PW가 포함되지 않는다 (docs/03 §2.8).
   *
   * <p>에러:
   * <ul>
   *   <li>400 VALIDATION_ERROR — email 형식/displayName blank/role null</li>
   *   <li>401 — 미인증</li>
   *   <li>403 — 인증되었으나 ROLE_ADMIN 부재 ({@code @PreAuthorize})</li>
   *   <li>409 CONFLICT/DUPLICATE_EMAIL — 동일 email 활성 사용자 존재</li>
   * </ul>
   * 호출부({@code useAdminInviteUser})가 status/code로 분기.
   */
  async adminInviteUser(params: AdminInviteUserParams): Promise<AdminInvitedUser> {
    const csrf = await ensureCsrfToken()
    const res = await fetch('/api/admin/users', {
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
      throw await buildApiError(res, `adminInviteUser failed: ${res.status}`)
    }
    return (await res.json()) as AdminInvitedUser
  },

  /**
   * `GET /api/admin/users` (admin-user-mgmt + admin-user-search-update, docs/02 §7.4) — 사용자 목록.
   *
   * <p>backend는 Spring `Page<T>` 직렬화를 그대로 노출 — `{content[], totalElements, totalPages,
   * number, size}`. 프론트는 본 형태의 부분집합만 type-narrow ({@link AdminUserPage}).
   *
   * <p>{@code q}는 옵션 — email/displayName 부분 매칭(case-insensitive, LIKE escape는 backend).
   * 빈 문자열/undefined면 query string에서 생략 — backend가 전체 목록 분기.
   */
  async adminListUsers(page = 0, size = 50, q?: string): Promise<AdminUserPage> {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (q && q.trim().length > 0) {
      params.set('q', q.trim())
    }
    const url = `/api/admin/users?${params.toString()}`
    const res = await fetch(url, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminListUsers failed: ${res.status}`)
    }
    return (await res.json()) as AdminUserPage
  },

  /**
   * `PATCH /api/admin/users/:id` (admin-user-mgmt + admin-user-search-update, docs/02 §7.4)
   * — role/active/displayName 변경.
   *
   * <p>{@code role}, {@code isActive}, {@code displayName} 모두 옵션이지만 최소 하나는 채워야 한다
   * (모두 비면 backend가 400 VALIDATION_ERROR). {@code isActive=true}는 reactivate, {@code false}는
   * deactivate(제재). 응답은 적용 후 사용자 스냅샷.
   */
  async adminUpdateUser(id: string, body: AdminUserPatchBody): Promise<AdminUserSummary> {
    const csrf = await ensureCsrfToken()
    const res = await fetch(`/api/admin/users/${id}`, {
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify(body),
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminUpdateUser failed: ${res.status}`)
    }
    return (await res.json()) as AdminUserSummary
  },

  // ── Admin departments (admin-department-crud, Wave 2 T4, docs/02 §7.x) ────

  /**
   * `GET /api/admin/departments` — admin 부서 목록 (admin-department-crud).
   *
   * <p>backend는 Spring {@code Page<T>} 직렬화를 그대로 노출 — {@link AdminDepartmentPage}로
   * 부분집합 type-narrow. {@code q}는 부분 일치 검색어 — null/빈 문자열은 "전체"로 backend가 해석.
   *
   * <p>호출자({@code useAdminDepartments})는 q를 trim+lowercase 후 전달 — backend service도 동일
   * normalize를 다시 적용하지만 캐시 키 일관성을 위해 호출 측에서 먼저 처리.
   */
  async adminListDepartments(
    page = 0,
    size = 50,
    q?: string,
  ): Promise<AdminDepartmentPage> {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('size', String(size))
    if (q && q.trim().length > 0) params.set('q', q.trim())
    const res = await fetch(`/api/admin/departments?${params.toString()}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminListDepartments failed: ${res.status}`)
    }
    return (await res.json()) as AdminDepartmentPage
  },

  /**
   * `POST /api/admin/departments` — admin 부서 신규 생성 (admin-department-crud).
   *
   * <p>200 OK + body는 {@link AdminDepartmentSummary} (controller가 default 200 — m-admin invite와
   * 다른 점은 {@code @ResponseStatus(CREATED)} 미부착). 에러 매핑:
   * <ul>
   *   <li>400 VALIDATION_ERROR — name blank/길이 초과</li>
   *   <li>403 — ROLE_ADMIN 부재</li>
   *   <li>409 DEPARTMENT_CONFLICT — 같은 name 활성 부서 존재</li>
   * </ul>
   */
  async adminCreateDepartment(
    body: AdminDepartmentCreateBody,
  ): Promise<AdminDepartmentSummary> {
    const csrf = await ensureCsrfToken()
    const res = await fetch('/api/admin/departments', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify(body),
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminCreateDepartment failed: ${res.status}`)
    }
    return (await res.json()) as AdminDepartmentSummary
  },

  /**
   * `PATCH /api/admin/departments/:id` — admin 부서 부분 수정 (admin-department-crud).
   *
   * <p>{@code body}는 {@code {name?, isActive?}} — 둘 다 비어있으면 backend가 400.
   * {@code isActive=false} → deactivate, {@code true} → reactivate, {@code name} → rename.
   * 둘 다 보내면 rename → activate/deactivate 순으로 적용 (backend controller 일관 처리).
   *
   * <p>에러:
   * <ul>
   *   <li>400 VALIDATION_ERROR — 빈 body / name 길이 초과</li>
   *   <li>403 — ROLE_ADMIN 부재</li>
   *   <li>404 NOT_FOUND — 부서 미존재</li>
   *   <li>409 DEPARTMENT_CONFLICT — rename/reactivate 시 같은 name 활성 부서 존재</li>
   * </ul>
   */
  async adminUpdateDepartment(
    id: string,
    body: AdminDepartmentPatchBody,
  ): Promise<AdminDepartmentSummary> {
    const csrf = await ensureCsrfToken()
    const res = await fetch(`/api/admin/departments/${id}`, {
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify(body),
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminUpdateDepartment failed: ${res.status}`)
    }
    return (await res.json()) as AdminDepartmentSummary
  },

  /**
   * `GET /api/admin/system/cron` (Wave 1 — T3, docs/02 §7.12).
   *
   * <p>4개 운영 cron 잡(`purge.expired`, `share.expire`, `permission.expire`,
   * `storage.orphan.cleanup`)의 현재 설정 스냅샷을 read-only로 조회. 변경(toggle/edit)은
   * v1.x deferred — application.yml 수정 + 재기동이 유일한 변경 경로.
   *
   * <p>backend `@PreAuthorize("hasRole('ADMIN')")` — AUDITOR 미허용. 401/403는
   * {@code buildApiError}로 status 매핑된 ApiError throw.
   */
  async adminGetCronStatus(): Promise<CronJobsResponse> {
    const res = await fetch('/api/admin/system/cron', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminGetCronStatus failed: ${res.status}`)
    }
    return (await res.json()) as CronJobsResponse
  },

  /**
   * `PUT /api/admin/system/cron/{key}` — admin-cron-policy-toggle. ADMIN-only.
   *
   * <p>요청 body: `{ enabled: boolean }`. 응답 204 No Content. 400은 unknown key 또는
   * body 형식 오류, 401/403는 인증/권한 실패. 보안 가드는 backend
   * `@PreAuthorize("hasRole('ADMIN')")` (진실 출처) — 프론트 hook 가드는 UX용.
   */
  async adminToggleCron(key: string, enabled: boolean): Promise<void> {
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(`/api/admin/system/cron/${encodeURIComponent(key)}`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf },
      body: JSON.stringify({ enabled }),
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminToggleCron failed: ${res.status}`)
    }
  },

  /**
   * `GET /api/admin/dashboard/summary` (admin-dashboard 트랙) — KPI envelope.
   *
   * <p>read-only — CSRF 헤더 없음. backend가 6 derived count + 1 SUM + 1 audit native COUNT를
   * 단일 응답으로 묶어 반환한다 ({@link AdminDashboardSummaryResponse}). 호출자는 envelope 그대로
   * 받아 `summary.*` 경로로 KPI에 접근. retry는 react-query 측에서 false (admin 화면은 즉시 노출 우선).
   *
   * <p>403은 ROLE_ADMIN 부재(또는 비admin이 직접 URL 진입), 401은 미인증. 두 경우 모두 hook
   * staleTime + retry false로 즉시 에러 노출. 본 메서드는 라우팅/렌더 정책을 가지지 않는다.
   */
  async adminGetDashboardSummary(): Promise<AdminDashboardSummaryResponse> {
    const res = await fetch('/api/admin/dashboard/summary', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminGetDashboardSummary failed: ${res.status}`)
    }
    return (await res.json()) as AdminDashboardSummaryResponse
  },

  /**
   * `GET /api/admin/permissions` — admin 권한 매트릭스 (Wave 2 T5).
   *
   * <p>read-only — CSRF 헤더 불필요. 빈 값(undefined / 빈 문자열) filter는 query에서 제외.
   * 에러는 {@link buildApiError}가 status/code 매핑 후 throw — 401(미인증), 403(MEMBER), 400(filter 검증).
   */
  async adminListPermissions(
    filters: AdminPermissionFilters = {},
  ): Promise<AdminPermissionPage> {
    const params = new URLSearchParams()
    params.set('page', String(filters.page ?? 0))
    params.set('size', String(filters.size ?? 20))
    if (filters.subjectType) params.set('subjectType', filters.subjectType)
    if (filters.subjectId && filters.subjectId.trim().length > 0) {
      params.set('subjectId', filters.subjectId.trim())
    }
    if (filters.resourceType) params.set('resourceType', filters.resourceType)
    if (filters.preset) params.set('preset', filters.preset)
    if (filters.q && filters.q.trim().length > 0) params.set('q', filters.q.trim())
    const res = await fetch(`/api/admin/permissions?${params.toString()}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminListPermissions failed: ${res.status}`)
    }
    return (await res.json()) as AdminPermissionPage
  },

  /**
   * `DELETE /api/permissions/{permissionId}` — 단일 grant 철회 (admin-permission-revoke,
   * Wave 2 T5 follow-up).
   *
   * <p>backend는 기존 resource-level endpoint(`PermissionController#revoke`)가
   * `@permissionService.canRevokePermission`로 가드 — ROLE.ADMIN은 통과한다. 본 호출은
   * `/admin/permissions` viewer에서 운영자가 직접 row 단위로 삭제하는 경로이며 별도 admin
   * 전용 endpoint를 만들지 않는다 (KISS). audit emit(`PERMISSION_REVOKED`)은 backend가 발행.
   *
   * <p>CSRF 헤더 필수 (다른 mutation 동형). 응답 204(No Content). 4xx envelope은
   * {@link buildApiError}가 status/code 매핑 후 throw — 401(미인증), 403(non-ADMIN),
   * 404(row 미존재 — 이미 삭제된 race도 동일).
   */
  async adminRevokePermission(permissionId: string): Promise<void> {
    const csrf = await ensureCsrfToken()
    const res = await fetch(`/api/permissions/${encodeURIComponent(permissionId)}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: { 'X-CSRF-TOKEN': csrf },
    })
    if (!res.ok) {
      throw await buildApiError(res, `adminRevokePermission failed: ${res.status}`)
    }
  },
}

/**
 * Admin invite 입력. backend `AdminInviteUserRequest` (docs/02 §7.4) 1:1 mirror.
 *
 * <p>role은 'MEMBER' | 'AUDITOR' | 'ADMIN' — backend Role enum 동형. 임시 PW는 backend가 생성.
 */
export interface AdminInviteUserParams {
  email: string
  displayName: string
  role: 'MEMBER' | 'AUDITOR' | 'ADMIN'
}

/**
 * Admin invite 응답. backend `AdminInviteUserResponse` (docs/02 §7.4) 1:1 mirror.
 * <b>tempPassword 필드 부재</b> — 임시 PW는 invite 이메일 본문으로만 전달 (docs/03 §2.8).
 */
export interface AdminInvitedUser {
  id: string
  email: string
  displayName: string
  role: 'MEMBER' | 'AUDITOR' | 'ADMIN'
  mustChangePassword: boolean
}

/**
 * Admin user list 항목. backend `AdminUserSummaryResponse` 1:1 mirror — admin-user-mgmt.
 *
 * <p>{@code AdminInvitedUser}와 분리: 후자는 invite 응답 의미(`mustChangePassword` 강조)이고
 * 본 type은 list 항목 의미(`isActive`/`createdAt`/`lastLoginAt` 스냅샷). 합치지 않는 이유는
 * 두 용도가 시간이 지나며 다른 방향으로 진화할 수 있기 때문.
 */
export interface AdminUserSummary {
  id: string
  email: string
  displayName: string
  role: 'MEMBER' | 'AUDITOR' | 'ADMIN'
  isActive: boolean
  createdAt: string
  lastLoginAt: string | null
}

/**
 * Spring `Page<AdminUserSummaryResponse>` 직렬화 모양 — admin-user-mgmt.
 *
 * <p>전체 필드는 사용 안 함. UI에 필요한 부분만 type narrow. {@code totalElements}와
 * {@code number}/{@code size}로 페이지네이션 컨트롤 구성.
 */
export interface AdminUserPage {
  content: AdminUserSummary[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * Admin user PATCH body — admin-user-mgmt + admin-user-search-update.
 * backend `AdminUserPatchRequest` 1:1 mirror.
 *
 * <p>모두 optional이지만 호출자는 최소 하나는 채워야 한다. {@code isActive=true} reactivate,
 * {@code false} deactivate. {@code displayName}은 trim 후 1-100자.
 */
export interface AdminUserPatchBody {
  role?: 'MEMBER' | 'AUDITOR' | 'ADMIN'
  isActive?: boolean
  displayName?: string
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
  const csrf = readCookie('XSRF-TOKEN') ?? ''
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf },
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

// ───────────────────────────────────────────────────────────────────────────
// admin-storage-overview — `GET /api/admin/storage/overview` (read-only).
//
// 본 트랙은 신설 함수를 파일 끝에 추가해 다른 admin 트랙과의 머지 충돌 면적을
// 최소화한다. `api` 객체에 메서드를 끼우는 대신 standalone export.
// 타입은 `@/types/admin-storage`에서 import (re-export로 호출자 import 단순화).
// ───────────────────────────────────────────────────────────────────────────

export type {
  AdminStorageOverview,
  AdminStorageOrphanCleanupSummary,
  AdminStorageOverviewResponse,
} from '@/types/admin-storage'

// ───────────────────────────────────────────────────────────────────────────
// admin-global-trash (Wave 2 T9) — `GET /api/admin/trash` (read-only).
//
// 신설 함수를 파일 끝에 추가해 다른 admin 트랙과의 머지 충돌 면적을 최소화한다.
// `api` 객체에 메서드를 끼우는 대신 standalone export — admin-storage 트랙과 동형.
// ───────────────────────────────────────────────────────────────────────────

export type {
  AdminTrashItem,
  AdminTrashFilters,
  AdminTrashPage,
  AdminTrashBulkAction,
  AdminTrashBulkItem,
  AdminTrashBulkRequest,
  AdminTrashBulkResponse,
} from '@/types/trash'

/**
 * Wave 2 T9 — admin global trash listing.
 *
 * <p>빈 filter 값은 query string에서 제거. 401/403/400 envelope을 {@link buildApiError}로 throw.
 * cursor 는 backend opaque base64 — 그대로 echo back.
 */
export async function adminListTrash(
  filters: AdminTrashFilters,
  cursor: string | null,
): Promise<AdminTrashPage> {
  const params = new URLSearchParams()
  if (filters.q && filters.q.trim()) params.set('q', filters.q.trim())
  if (filters.type) params.set('type', filters.type)
  if (filters.ownerId) params.set('ownerId', filters.ownerId)
  if (filters.deletedFrom) params.set('deletedFrom', filters.deletedFrom)
  if (filters.deletedTo) params.set('deletedTo', filters.deletedTo)
  if (cursor) params.set('cursor', cursor)

  const qs = params.toString()
  const url = `/api/admin/trash${qs ? `?${qs}` : ''}`

  const res = await fetch(url, { method: 'GET', credentials: 'include' })
  if (!res.ok) {
    throw await buildApiError(res, `adminListTrash failed: ${res.status}`)
  }
  return (await res.json()) as AdminTrashPage
}

/**
 * Wave 2 T9 follow-up — admin bulk restore/purge (spec §3).
 *
 * <p>{@code action}은 `restore` 또는 `purge`. {@code items}는 1..200개. 응답은 항상 200
 * (부분 실패 허용); cap/action 검증 실패만 400. 401/403/400 envelope을 {@link buildApiError}로
 * throw — 호출자는 200일 때만 receive하고 `failed[]` 배열을 토스트로 노출한다.
 */
export async function adminBulkTrash(
  action: import('@/types/trash').AdminTrashBulkAction,
  items: import('@/types/trash').AdminTrashBulkItem[],
): Promise<import('@/types/trash').AdminTrashBulkResponse> {
  const csrf = readCookie('XSRF-TOKEN') ?? ''
  const res = await fetch('/api/admin/trash/bulk', {
    method: 'POST',
    credentials: 'include',
    headers: { 'content-type': 'application/json', 'X-CSRF-TOKEN': csrf },
    body: JSON.stringify({ action, items }),
  })
  if (!res.ok) {
    throw await buildApiError(res, `adminBulkTrash failed: ${res.status}`)
  }
  return (await res.json()) as import('@/types/trash').AdminTrashBulkResponse
}

/**
 * `/admin/trash/policy` — 휴지통 보존 정책 read-only viewer (Wave 2 T9 follow-up,
 * wave2-trash-policy-viewer). ADMIN role 전용. 401/403/200 외 에러 코드 없음.
 *
 * <p>현재 `retentionDays` 단일 필드. cron 운영 상태는 `/admin/system` 별도. mutation은
 * v1.x deferred (`@ConfigurationProperties` 부팅 바인딩).
 */
export async function getAdminTrashPolicy(): Promise<import('@/types/admin-trash-policy').AdminTrashPolicy> {
  const res = await fetch('/api/admin/trash/policy', {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!res.ok) {
    throw await buildApiError(res, `getAdminTrashPolicy failed: ${res.status}`)
  }
  return (await res.json()) as import('@/types/admin-trash-policy').AdminTrashPolicy
}

/**
 * 시스템 전체 스토리지 overview — ADMIN role 전용. 401/403/200 외 에러 코드 없음 (read-only).
 *
 * <p>응답 envelope: `{ overview: { totalFiles, totalVersions, totalBytes, trashedFiles,
 * trashedBytes, orphanCleanup? } }`. `orphanCleanup`은 audit_log에 `storage.orphan.cleaned`
 * 0건이면 `null`.
 */
export async function getAdminStorageOverview(): Promise<AdminStorageOverviewResponse> {
  const res = await fetch('/api/admin/storage/overview', {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!res.ok) {
    throw await buildApiError(res, `getAdminStorageOverview failed: ${res.status}`)
  }
  return (await res.json()) as AdminStorageOverviewResponse
}

