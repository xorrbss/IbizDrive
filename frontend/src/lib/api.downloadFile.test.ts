import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * M-Download — api.downloadFile은 programmatic anchor 클릭으로 backend
 * `GET /api/files/{id}/download` (docs/02 §7.6.1) 트리거.
 *
 * - URL: `/api/files/{encodeURIComponent(id)}/download`
 * - cookie 인증은 same-origin GET → 브라우저가 자동 동봉 (별도 withCredentials 불요)
 * - RFC 5987 Content-Disposition은 backend가 처리 → 브라우저가 파일명 적용
 * - anchor `<a>` element는 body에 append → click() → remove
 *
 * 본 테스트는 anchor wire 계약(URL/click/cleanup) 검증에 한정.
 */

describe('api.downloadFile (anchor click wire)', () => {
  let anchor: HTMLAnchorElement
  let appendCalls: Node[] = []
  let removeCalls: Node[] = []

  beforeEach(() => {
    anchor = document.createElement('a')
    anchor.click = vi.fn()
    appendCalls = []
    removeCalls = []
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation(
      (tag: string) =>
        (tag === 'a' ? anchor : originalCreateElement(tag)) as HTMLElement,
    )
    vi.spyOn(document.body, 'appendChild').mockImplementation((node) => {
      appendCalls.push(node as Node)
      return node
    })
    vi.spyOn(document.body, 'removeChild').mockImplementation((node) => {
      removeCalls.push(node as Node)
      return node
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('anchor href는 /api/files/{id}/download', () => {
    api.downloadFile('file_x')
    expect(anchor.getAttribute('href')).toBe('/api/files/file_x/download')
  })

  it('id에 특수문자(/, 공백)가 있으면 encodeURIComponent 적용', () => {
    api.downloadFile('a / b c')
    expect(anchor.getAttribute('href')).toBe(
      '/api/files/a%20%2F%20b%20c/download',
    )
  })

  it('anchor를 body에 append → click() → remove (cleanup)', () => {
    api.downloadFile('file_x')
    expect(appendCalls).toContain(anchor)
    expect(anchor.click).toHaveBeenCalledTimes(1)
    expect(removeCalls).toContain(anchor)
  })
})
