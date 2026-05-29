import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useFolderUpload } from './useFolderUpload'
import { api } from '@/lib/api'
import type { FolderUploadPlan } from '@/lib/folderUpload'

/**
 * folder-upload P4 — 오케스트레이터.
 * getFolderChildren(병합 판단) + createFolder(없으면 생성) → path→folderId → 그룹 enqueue.
 * 백엔드/업로드 store 무변경 (docs/01 §9.6).
 */

vi.mock('@/lib/api', () => ({
  api: { getFolderChildren: vi.fn(), createFolder: vi.fn() },
}))

const { enqueue } = vi.hoisted(() => ({ enqueue: vi.fn() }))
vi.mock('@/hooks/useUpload', () => ({ useUpload: () => ({ enqueue }) }))

const getChildren = api.getFolderChildren as ReturnType<typeof vi.fn>
const createFolder = api.createFolder as ReturnType<typeof vi.fn>

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

function render() {
  const qc = new QueryClient()
  return renderHook(() => useFolderUpload(), { wrapper: makeWrapper(qc) })
}

const f = (name: string) => new File([''], name)

async function run(plan: FolderUploadPlan, base = 'base') {
  const { result } = render()
  let res!: Awaited<ReturnType<typeof result.current.uploadFolder>>
  await act(async () => {
    res = await result.current.uploadFolder(plan, base)
  })
  return res
}

describe('useFolderUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getChildren.mockResolvedValue([])
    createFolder.mockImplementation((parentId: string, name: string) =>
      Promise.resolve({ id: `${name}-id`, name, parentId }),
    )
  })

  it('신규 트리 생성 + 파일을 폴더별 그룹으로 enqueue', async () => {
    const plan: FolderUploadPlan = {
      entries: [
        { file: f('README.md'), pathSegments: ['proj'] },
        { file: f('index.ts'), pathSegments: ['proj', 'src'] },
      ],
      dirPaths: [['proj'], ['proj', 'src']],
    }
    const res = await run(plan)

    expect(createFolder).toHaveBeenCalledWith('base', 'proj')
    expect(createFolder).toHaveBeenCalledWith('proj-id', 'src')
    expect(res.createdFolders).toBe(2)
    expect(res.errors).toEqual([])
    // proj-id ← README, src-id ← index
    expect(enqueue).toHaveBeenCalledTimes(2)
    const calls = Object.fromEntries(
      enqueue.mock.calls.map((c) => [c[1], (c[0] as File[]).map((x) => x.name)]),
    )
    expect(calls['proj-id']).toEqual(['README.md'])
    expect(calls['src-id']).toEqual(['index.ts'])
  })

  it('같은 이름 폴더가 있으면 병합 — 중복 생성 안 함 (대소문자 무시)', async () => {
    getChildren.mockResolvedValue([{ id: 'existing-proj', name: 'PROJ' }])
    const plan: FolderUploadPlan = {
      entries: [{ file: f('a.txt'), pathSegments: ['proj'] }],
      dirPaths: [['proj']],
    }
    const res = await run(plan)

    expect(createFolder).not.toHaveBeenCalled()
    expect(res.createdFolders).toBe(0)
    expect(enqueue).toHaveBeenCalledWith([expect.objectContaining({ name: 'a.txt' })], 'existing-proj')
  })

  it('빈 폴더도 생성 (파일 없음)', async () => {
    const plan: FolderUploadPlan = {
      entries: [],
      dirPaths: [['proj'], ['proj', 'empty']],
    }
    const res = await run(plan)

    expect(createFolder).toHaveBeenCalledWith('base', 'proj')
    expect(createFolder).toHaveBeenCalledWith('proj-id', 'empty')
    expect(res.createdFolders).toBe(2)
    expect(res.uploadedGroups).toBe(0)
    expect(enqueue).not.toHaveBeenCalled()
  })

  it('createFolder 409(race) → 자식 재조회로 기존 id 병합', async () => {
    getChildren
      .mockResolvedValueOnce([]) // 최초 조회 — 없음 → 생성 시도
      .mockResolvedValueOnce([{ id: 'raced-id', name: 'proj' }]) // 409 후 재조회
    createFolder.mockRejectedValueOnce({ status: 409, code: 'RENAME_CONFLICT' })

    const plan: FolderUploadPlan = {
      entries: [{ file: f('a.txt'), pathSegments: ['proj'] }],
      dirPaths: [['proj']],
    }
    const res = await run(plan)

    expect(res.errors).toEqual([])
    expect(res.createdFolders).toBe(0)
    expect(enqueue).toHaveBeenCalledWith([expect.objectContaining({ name: 'a.txt' })], 'raced-id')
  })

  it('403 생성 실패 → 서브트리 스킵, 나머지(base 직속)는 계속', async () => {
    createFolder.mockRejectedValue({ status: 403, code: 'PERMISSION_DENIED' })
    const plan: FolderUploadPlan = {
      entries: [
        { file: f('root.txt'), pathSegments: [] },
        { file: f('a.txt'), pathSegments: ['proj', 'src'] },
      ],
      dirPaths: [['proj'], ['proj', 'src']],
    }
    const res = await run(plan)

    // base 직속 파일은 업로드
    expect(enqueue).toHaveBeenCalledTimes(1)
    expect(enqueue).toHaveBeenCalledWith([expect.objectContaining({ name: 'root.txt' })], 'base')
    // proj 생성 실패(403) + proj/src 스킵 + a.txt 스킵
    expect(res.createdFolders).toBe(0)
    expect(res.errors.length).toBeGreaterThanOrEqual(2)
    expect(res.errors.some((e) => e.path === 'proj' && e.message.includes('권한'))).toBe(true)
  })
})
