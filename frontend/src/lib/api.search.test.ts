import { describe, it, expect } from 'vitest'
import { api } from './api'

describe('api.searchFiles', () => {
  it('이름에 normalize 매칭되는 파일 반환', async () => {
    const { items } = await api.searchFiles({ q: '계약', filters: {} })
    const names = items.map((f) => f.name)
    // MOCK_FILES에 '계약서_A사.pdf', '계약서_B사.pdf' 존재
    expect(names).toEqual(expect.arrayContaining(['계약서_A사.pdf', '계약서_B사.pdf']))
  })

  it('대소문자/공백 무시 매칭 (normalizeForSearch 적용)', async () => {
    // MOCK_FILES의 '제안서_2026.pdf'에서 'pdf' 검색 매칭
    const { items } = await api.searchFiles({ q: 'pdf', filters: {} })
    expect(items.length).toBeGreaterThan(0)
    expect(items.every((f) => f.name.toLowerCase().includes('pdf'))).toBe(true)
  })

  it('매칭 없으면 빈 배열', async () => {
    const { items } = await api.searchFiles({ q: 'zzznotexist', filters: {} })
    expect(items).toEqual([])
  })

  it('abort signal 시 AbortError로 reject', async () => {
    const ctrl = new AbortController()
    const promise = api.searchFiles({ q: '계약', filters: {} }, { signal: ctrl.signal })
    ctrl.abort()
    await expect(promise).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('이미 abort된 signal로 호출 시 즉시 reject', async () => {
    const ctrl = new AbortController()
    ctrl.abort()
    await expect(
      api.searchFiles({ q: '계약', filters: {} }, { signal: ctrl.signal }),
    ).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('빈 q는 즉시 빈 결과 (방어)', async () => {
    const { items } = await api.searchFiles({ q: '', filters: {} })
    expect(items).toEqual([])
  })
})
