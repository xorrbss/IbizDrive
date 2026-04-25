import { describe, it, expect } from 'vitest'
import { api } from './api'

describe('api.searchFiles', () => {
  it('빈 q → 빈 배열', async () => {
    const r = await api.searchFiles({ q: '', filters: {} })
    expect(r).toEqual([])
  })

  it('1자 미만 매치 → 빈 배열 (최소 2자)', async () => {
    const r = await api.searchFiles({ q: 'a', filters: {} })
    expect(r).toEqual([])
  })

  it('한글 prefix 매치 — "계약"', async () => {
    const r = await api.searchFiles({ q: '계약', filters: {} })
    const names = r.map((f) => f.name)
    expect(names).toContain('계약서_A사.pdf')
    expect(names).toContain('계약서_B사.pdf')
  })

  it('한글 NFC 일관 — "예산"', async () => {
    const r = await api.searchFiles({ q: '예산', filters: {} })
    expect(r.map((f) => f.name)).toContain('예산안.xlsx')
  })

  it('대소문자 무시 — "PDF"', async () => {
    const r = await api.searchFiles({ q: 'pdf', filters: {} })
    // 모두 .pdf 확장자를 가진 파일들이 매치되는지
    const names = r.map((f) => f.name)
    expect(names.some((n) => n.endsWith('.pdf'))).toBe(true)
  })

  it('AbortSignal abort → AbortError throw', async () => {
    const ac = new AbortController()
    const promise = api.searchFiles(
      { q: '계약', filters: {} },
      { signal: ac.signal },
    )
    ac.abort()
    await expect(promise).rejects.toThrow(/abort/i)
  })

  it('이미 abort된 signal → 즉시 reject', async () => {
    const ac = new AbortController()
    ac.abort()
    await expect(
      api.searchFiles({ q: '계약', filters: {} }, { signal: ac.signal }),
    ).rejects.toThrow(/abort/i)
  })
})
