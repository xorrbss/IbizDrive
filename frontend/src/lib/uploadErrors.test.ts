import { describe, it, expect } from 'vitest'
import { classifyError, type UploadErrorKind } from './uploadErrors'

describe('classifyError', () => {
  const cases: Array<[number, UploadErrorKind]> = [
    [0, 'network'],
    [403, 'permission'],
    [413, 'quota'],
    [409, 'conflict'],
    [500, 'server'],
    [503, 'server'],
    [418, 'server'],
  ]

  for (const [status, kind] of cases) {
    it(`status ${status} → ${kind}`, () => {
      expect(classifyError({ status }).kind).toBe(kind)
    })
  }

  it('unknown status는 상태 코드를 메시지에 포함', () => {
    expect(classifyError({ status: 418 }).message).toContain('418')
  })
})
