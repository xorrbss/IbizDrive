import { describe, it, expect, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * P4 미리보기 (ADR #51) — previewFileUrl/openFilePreview wire 계약.
 * inline 허용 여부 판정은 backend 화이트리스트 책임 — 여기서는 URL/새 탭 옵션만 검증.
 */
describe('api.previewFileUrl / openFilePreview', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('previewFileUrl은 disposition=inline 쿼리를 포함', () => {
    expect(api.previewFileUrl('file_x')).toBe(
      '/api/files/file_x/download?disposition=inline',
    )
  })

  it('id 특수문자는 encodeURIComponent 적용', () => {
    expect(api.previewFileUrl('a / b')).toBe(
      '/api/files/a%20%2F%20b/download?disposition=inline',
    )
  })

  it('openFilePreview는 noopener,noreferrer 새 탭으로 연다', () => {
    const openSpy = vi
      .spyOn(window, 'open')
      .mockReturnValue(null as unknown as Window)
    api.openFilePreview('file_x')
    expect(openSpy).toHaveBeenCalledWith(
      '/api/files/file_x/download?disposition=inline',
      '_blank',
      'noopener,noreferrer',
    )
  })
})
