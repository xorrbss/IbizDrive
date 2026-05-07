import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * api.renameFile — fetch-mock 계약 검증.
 *
 * <p>T6 closure(2026-05-07)에서 내장 모의 데이터 제거되며 describe.skip 처리됨.
 * 본 파일은 표준 fetch-mock 패턴(`api.adminStorage.test.ts` mirror)으로 재작성.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.renameFile', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('빈 이름 → VALIDATION_ERROR (fetch 미호출)', async () => {
    await expect(api.renameFile('file_budget', '   ')).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
    })
    expect(fetchMock.mock.calls.length).toBe(0)
  })

  it('파일 이름 변경 성공', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          file: {
            id: 'file_proposal',
            folderId: 'root',
            name: '제안서_v2.pdf',
            ownerId: 'user_a',
            sizeBytes: 1024,
            mimeType: 'application/pdf',
            updatedAt: '2026-05-07T12:00:00Z',
          },
        },
        200,
      ),
    )

    const result = await api.renameFile('file_proposal', '제안서_v2.pdf')

    expect(result.name).toBe('제안서_v2.pdf')
    expect(result.type).toBe('file')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_proposal')
    expect(init).toMatchObject({
      method: 'PATCH',
      credentials: 'include',
    })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      name: '제안서_v2.pdf',
    })
  })

  // Phase B(real-fetch tree refetch + cache invalidation 통합) 의존, 본 트랙 외.
  // TODO(Phase B): backend `PATCH /api/folders/{id}` 응답 + tree 재조회 mock으로 재작성.
  it.skip('폴더 이름 변경 시 tree에도 반영 (Phase B 재작성 대기)', async () => {
    // Phase B에서 활성화
  })

  it('중복 이름 → RENAME_CONFLICT (409)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'RENAME_CONFLICT', message: '중복' } }, 409),
    )

    await expect(
      api.renameFile('file_minutes', '예산안.xlsx'),
    ).rejects.toMatchObject({
      status: 409,
      code: 'RENAME_CONFLICT',
    })
  })

  it('존재하지 않는 id → NOT_FOUND (404)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'NOT_FOUND', message: '미존재' } }, 404),
    )

    await expect(api.renameFile('nonexistent', 'x.txt')).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
    })
  })

  it('자기 자신 이름으로 변경은 허용 (200 OK 통과)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          file: {
            id: 'file_contract_a',
            folderId: 'folder_contracts',
            name: '계약서_A.pdf',
            ownerId: 'user_a',
            sizeBytes: 2048,
            mimeType: 'application/pdf',
            updatedAt: '2026-05-07T12:00:00Z',
          },
        },
        200,
      ),
    )

    const result = await api.renameFile('file_contract_a', '계약서_A.pdf')

    expect(result.name).toBe('계약서_A.pdf')
    expect(fetchMock.mock.calls.length).toBe(1)
  })
})
