/**
 * api.move — Plan D Task 24 REST helpers.
 *
 * 두 엔드포인트 그룹을 래핑:
 *   - POST /api/folders/{id}/move/preview
 *   - POST /api/files/{id}/move/preview
 *   - POST /api/folders/{id}/move { allowCrossScope: true, ... }
 *   - POST /api/files/{id}/move   { allowCrossScope: true, ... }
 *
 * fetch 패턴은 api.ts moveItem과 동일 (CSRF + credentials: include + buildApiError).
 */

import type { Permission } from '@/types/permission'

// ─── DTO Types ───────────────────────────────────────────────────────────────
// KISS: 별도 types/movePreview.ts 금지, 여기에 인라인.

export interface PermissionRef {
  id: string
  subjectType: string
  subjectId: string
  preset: string
}

export interface ShareRef {
  id: string
  resourceType: string | null
  resourceId: string | null
  sharedBy: string
}

export interface MovePreviewResponse {
  itemCount: number
  removedPermissions: PermissionRef[]
  revokedShares: ShareRef[]
  targetMembershipDefaults: Permission[]
  nameConflict: string | null
}

export interface MovePreviewRequest {
  destinationFolderId: string
}

export interface CrossWorkspaceMoveFolderBody {
  targetParentId: string
  allowCrossScope: true
}

export interface CrossWorkspaceMoveFileBody {
  targetFolderId: string
  allowCrossScope: true
}

// ─── Internal helpers (duplicated from api.ts — no shared module) ─────────────

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]+)`))
  return match ? decodeURIComponent(match[1]) : null
}

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
    // 본문이 없거나 JSON이 아니면 status만으로 충분
  }
  return err
}

function csrfHeaders(): Record<string, string> {
  const csrf = readCookie('XSRF-TOKEN') ?? ''
  return {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    'X-CSRF-TOKEN': csrf,
  }
}

// ─── API Functions ────────────────────────────────────────────────────────────

/**
 * POST /api/folders/{folderId}/move/preview
 * cross-workspace 이동 전 영향 범위 미리보기.
 */
export async function previewFolderMove(
  folderId: string,
  body: MovePreviewRequest,
): Promise<MovePreviewResponse> {
  const res = await fetch(`/api/folders/${encodeURIComponent(folderId)}/move/preview`, {
    method: 'POST',
    credentials: 'include',
    headers: csrfHeaders(),
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    throw await buildApiError(res, `previewFolderMove failed: ${res.status}`)
  }
  return res.json() as Promise<MovePreviewResponse>
}

/**
 * POST /api/files/{fileId}/move/preview
 * cross-workspace 이동 전 영향 범위 미리보기.
 */
export async function previewFileMove(
  fileId: string,
  body: MovePreviewRequest,
): Promise<MovePreviewResponse> {
  const res = await fetch(`/api/files/${encodeURIComponent(fileId)}/move/preview`, {
    method: 'POST',
    credentials: 'include',
    headers: csrfHeaders(),
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    throw await buildApiError(res, `previewFileMove failed: ${res.status}`)
  }
  return res.json() as Promise<MovePreviewResponse>
}

/**
 * POST /api/folders/{folderId}/move { allowCrossScope: true, ... }
 * cross-workspace 폴더 이동 실행. 응답은 { folder } envelope.
 */
export async function crossWorkspaceMoveFolder(
  folderId: string,
  body: CrossWorkspaceMoveFolderBody,
): Promise<{ folder: import('@/types/folder').FolderDetail }> {
  const res = await fetch(`/api/folders/${encodeURIComponent(folderId)}/move`, {
    method: 'POST',
    credentials: 'include',
    headers: csrfHeaders(),
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    throw await buildApiError(res, `crossWorkspaceMoveFolder failed: ${res.status}`)
  }
  return res.json() as Promise<{ folder: import('@/types/folder').FolderDetail }>
}

/**
 * POST /api/files/{fileId}/move { allowCrossScope: true, ... }
 * cross-workspace 파일 이동 실행. 응답은 { file } envelope.
 */
export async function crossWorkspaceMoveFile(
  fileId: string,
  body: CrossWorkspaceMoveFileBody,
): Promise<{ file: import('@/types/file').FileItem }> {
  const res = await fetch(`/api/files/${encodeURIComponent(fileId)}/move`, {
    method: 'POST',
    credentials: 'include',
    headers: csrfHeaders(),
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    throw await buildApiError(res, `crossWorkspaceMoveFile failed: ${res.status}`)
  }
  return res.json() as Promise<{ file: import('@/types/file').FileItem }>
}
