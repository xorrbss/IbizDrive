import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * api.moveFiles — fetch-mock 계약 검증 (Promise.all fanout).
 *
 * <p>T6 closure(2026-05-07)에서 내장 모의 데이터 제거되며 describe.skip 처리됨.
 * 본 파일은 표준 fetch-mock 패턴으로 재작성. moveItem void 반환에 대응해 mock은 204 No Content.
 * 첫 rejection이 전체 결정 (api.ts:354 — `Promise.all` 의도).
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.moveFiles', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('자기 자신으로 이동 시 MOVE_INTO_SELF 던진다', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'MOVE_INTO_SELF', message: '자기 자신' } }, 400),
    )

    await expect(
      api.moveFiles([{ id: 'folder_sales', type: 'folder' }], 'folder_sales'),
    ).rejects.toMatchObject({ status: 400, code: 'MOVE_INTO_SELF' })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/folder_sales/move')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
  })

  it('후손 폴더로 이동 시 MOVE_INTO_DESCENDANT 던진다', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'MOVE_INTO_DESCENDANT', message: '후손' } }, 400),
    )

    await expect(
      api.moveFiles(
        [{ id: 'folder_sales', type: 'folder' }],
        'folder_contracts',
      ),
    ).rejects.toMatchObject({ status: 400, code: 'MOVE_INTO_DESCENDANT' })
  })

  it('타겟 폴더가 없으면 TARGET_NOT_FOUND', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'TARGET_NOT_FOUND', message: '미존재' } }, 404),
    )

    await expect(
      api.moveFiles(
        [{ id: 'file_proposal', type: 'file' }],
        'nonexistent_folder',
      ),
    ).rejects.toMatchObject({ status: 404, code: 'TARGET_NOT_FOUND' })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_proposal/move')
  })

  it('파일을 다른 폴더로 이동시킨다 (parentId 갱신)', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    const result = await api.moveFiles(
      [{ id: 'file_contract_a', type: 'file' }],
      'root',
    )

    expect(result).toEqual({ movedIds: ['file_contract_a'] })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_contract_a/move')
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      targetFolderId: 'root',
    })
  })

  it('movedIds를 반환한다 (단건)', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    const result = await api.moveFiles(
      [{ id: 'file_minutes', type: 'file' }],
      'folder_hr',
    )

    expect(result).toEqual({ movedIds: ['file_minutes'] })
    expect(fetchMock.mock.calls.length).toBe(1)
  })

  // 멀티-아이템 fanout: api.moveFiles는 items.map을 Promise.all로 묶음 (api.ts:354).
  // 단건 case들로는 N개 fetch가 실제로 발생하는지 검증 불가 — 이 책임은 api 레이어 소관.
  it('멀티 아이템은 Promise.all로 N개 fetch fanout', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    const result = await api.moveFiles(
      [
        { id: 'a', type: 'file' },
        { id: 'b', type: 'file' },
      ],
      'dst',
    )

    expect(result).toEqual({ movedIds: ['a', 'b'] })
    expect(fetchMock.mock.calls.length).toBe(2)
    // Promise.all은 호출 순서를 보장하지 않으므로 URL 집합으로 단언.
    const urls = fetchMock.mock.calls.map((c) => c[0]).sort()
    expect(urls).toEqual(['/api/files/a/move', '/api/files/b/move'])
  })
})
