import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

/**
 * api.move — POST /api/{folders|files}/{id}/move/preview および
 *            POST /api/{folders|files}/{id}/move (allowCrossScope: true) 계약 검증.
 *
 * fetch-mock 패턴은 api.moveFiles.test.ts와 동일.
 */

import {
  previewFolderMove,
  previewFileMove,
  crossWorkspaceMoveFolder,
  crossWorkspaceMoveFile,
} from './api.move'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const mockPreviewResponse = {
  itemCount: 3,
  removedPermissions: [
    { id: 'p1', subjectType: 'USER', subjectId: 'u1', preset: 'edit' },
  ],
  revokedShares: [
    { id: 's1', resourceType: 'folder', resourceId: 'f1', sharedBy: 'u2' },
  ],
  targetMembershipDefaults: ['READ', 'DOWNLOAD'],
  nameConflict: null,
}

describe('previewFolderMove', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /api/folders/{id}/move/preview 호출 후 MovePreviewResponse 반환', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(mockPreviewResponse))

    const result = await previewFolderMove('folder_a', {
      destinationFolderId: 'folder_b',
    })

    expect(result).toEqual(mockPreviewResponse)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/folder_a/move/preview')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      destinationFolderId: 'folder_b',
    })
  })

  it('서버 4xx → ApiError throw', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'TARGET_NOT_FOUND', message: '없음' } }, 404),
    )

    await expect(
      previewFolderMove('folder_x', { destinationFolderId: 'no_such' }),
    ).rejects.toMatchObject({ status: 404, code: 'TARGET_NOT_FOUND' })
  })
})

describe('previewFileMove', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /api/files/{id}/move/preview 호출 후 MovePreviewResponse 반환', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(mockPreviewResponse))

    const result = await previewFileMove('file_a', {
      destinationFolderId: 'folder_b',
    })

    expect(result).toEqual(mockPreviewResponse)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_a/move/preview')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      destinationFolderId: 'folder_b',
    })
  })

  it('서버 4xx → ApiError throw', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'ERR_CROSS_SCOPE_MOVE', message: '스코프' } }, 409),
    )

    await expect(
      previewFileMove('file_x', { destinationFolderId: 'other_scope' }),
    ).rejects.toMatchObject({ status: 409, code: 'ERR_CROSS_SCOPE_MOVE' })
  })
})

describe('crossWorkspaceMoveFolder', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /api/folders/{id}/move allowCrossScope:true 호출, folder envelope 반환', async () => {
    const folderPayload = {
      folder: { id: 'folder_a', name: 'Sales', parentId: 'folder_b', slug: 'sales' },
    }
    fetchMock.mockResolvedValueOnce(jsonResponse(folderPayload))

    const result = await crossWorkspaceMoveFolder('folder_a', {
      targetParentId: 'folder_b',
      allowCrossScope: true,
    })

    expect(result).toEqual(folderPayload)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/folder_a/move')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    const body = JSON.parse((init as RequestInit).body as string)
    expect(body).toMatchObject({ targetParentId: 'folder_b', allowCrossScope: true })
  })

  it('서버 409 NAME_CONFLICT → ApiError throw', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'NAME_CONFLICT', message: '중복' } }, 409),
    )

    await expect(
      crossWorkspaceMoveFolder('folder_a', {
        targetParentId: 'folder_b',
        allowCrossScope: true,
      }),
    ).rejects.toMatchObject({ status: 409, code: 'NAME_CONFLICT' })
  })
})

describe('crossWorkspaceMoveFile', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /api/files/{id}/move allowCrossScope:true 호출, file envelope 반환', async () => {
    const filePayload = {
      file: {
        id: 'file_a',
        name: 'contract.pdf',
        parentId: 'folder_b',
        mimeType: 'application/pdf',
        size: 1024,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
        deletedAt: null,
        versions: 1,
        scope: null,
      },
    }
    fetchMock.mockResolvedValueOnce(jsonResponse(filePayload))

    const result = await crossWorkspaceMoveFile('file_a', {
      targetFolderId: 'folder_b',
      allowCrossScope: true,
    })

    expect(result).toEqual(filePayload)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_a/move')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    const body = JSON.parse((init as RequestInit).body as string)
    expect(body).toMatchObject({ targetFolderId: 'folder_b', allowCrossScope: true })
  })

  it('서버 4xx → ApiError throw', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'PERMISSION_DENIED', message: '권한없음' } }, 403),
    )

    await expect(
      crossWorkspaceMoveFile('file_x', {
        targetFolderId: 'folder_b',
        allowCrossScope: true,
      }),
    ).rejects.toMatchObject({ status: 403, code: 'PERMISSION_DENIED' })
  })
})
