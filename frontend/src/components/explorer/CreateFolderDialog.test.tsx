import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { CreateFolderDialog } from './CreateFolderDialog'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { createFolder: vi.fn() },
}))

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('CreateFolderDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('open=false → 렌더 없음', () => {
    wrap(<CreateFolderDialog parentId="p1" open={false} onClose={() => {}} />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('open=true → 다이얼로그 + 입력 + 제출 버튼', () => {
    wrap(<CreateFolderDialog parentId="p1" open={true} onClose={() => {}} />)
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByLabelText(/폴더 이름/)).toBeTruthy()
    expect(screen.getByRole('button', { name: '생성' })).toBeTruthy()
  })

  it('빈 입력 제출 → 클라이언트 에러 인라인 노출, api 미호출', async () => {
    wrap(<CreateFolderDialog parentId="p1" open={true} onClose={() => {}} />)
    fireEvent.click(screen.getByRole('button', { name: '생성' }))
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeTruthy()
    })
    expect(api.createFolder).not.toHaveBeenCalled()
  })

  it('정상 입력 + 성공 → api 호출 + onClose 호출', async () => {
    ;(api.createFolder as ReturnType<typeof vi.fn>).mockResolvedValue({
      id: 'new', name: '새 폴더', parentId: 'p1',
    })
    const onClose = vi.fn()
    wrap(<CreateFolderDialog parentId="p1" open={true} onClose={onClose} />)
    fireEvent.change(screen.getByLabelText(/폴더 이름/), {
      target: { value: '새 폴더' },
    })
    fireEvent.click(screen.getByRole('button', { name: '생성' }))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(api.createFolder).toHaveBeenCalledWith('p1', '새 폴더')
  })

  it('409 RENAME_CONFLICT → 인라인 "같은 이름..." + 다이얼로그 유지', async () => {
    const err = Object.assign(new Error('409'), {
      status: 409, code: 'RENAME_CONFLICT',
    })
    ;(api.createFolder as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const onClose = vi.fn()
    wrap(<CreateFolderDialog parentId="p1" open={true} onClose={onClose} />)
    fireEvent.change(screen.getByLabelText(/폴더 이름/), {
      target: { value: '중복' },
    })
    fireEvent.click(screen.getByRole('button', { name: '생성' }))
    await waitFor(() => {
      expect(
        screen.getByRole('alert').textContent ?? '',
      ).toMatch(/같은 이름/)
    })
    expect(onClose).not.toHaveBeenCalled()
  })

  it('403 → 인라인 "권한..." + 유지', async () => {
    const err = Object.assign(new Error('403'), { status: 403 })
    ;(api.createFolder as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const onClose = vi.fn()
    wrap(<CreateFolderDialog parentId="p1" open={true} onClose={onClose} />)
    fireEvent.change(screen.getByLabelText(/폴더 이름/), {
      target: { value: 'X' },
    })
    fireEvent.click(screen.getByRole('button', { name: '생성' }))
    await waitFor(() => {
      expect(
        screen.getByRole('alert').textContent ?? '',
      ).toMatch(/권한/)
    })
    expect(onClose).not.toHaveBeenCalled()
  })

  it('취소 버튼 → onClose 호출, api 미호출', () => {
    const onClose = vi.fn()
    wrap(<CreateFolderDialog parentId="p1" open={true} onClose={onClose} />)
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(onClose).toHaveBeenCalled()
    expect(api.createFolder).not.toHaveBeenCalled()
  })
})
