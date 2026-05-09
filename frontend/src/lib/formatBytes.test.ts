import { describe, it, expect } from 'vitest'
import { formatBytes } from './formatBytes'

describe('formatBytes', () => {
  it('< 1KB 는 정수 + " B" 표기', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(1)).toBe('1 B')
    expect(formatBytes(1023)).toBe('1023 B')
  })

  it('1KB 이상 1MB 미만은 정수 KB', () => {
    expect(formatBytes(1024)).toBe('1 KB')
    expect(formatBytes(1024 * 1023)).toBe('1023 KB')
  })

  it('1MB 이상 1GB 미만은 정수 MB', () => {
    expect(formatBytes(1024 * 1024)).toBe('1 MB')
    expect(formatBytes(10 * 1024 * 1024)).toBe('10 MB')
    expect(formatBytes(1024 * 1024 * 1023)).toBe('1023 MB')
  })

  it('1GB 이상 1TB 미만은 소수점 1자리 GB', () => {
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1.0 GB')
    expect(formatBytes(1.5 * 1024 * 1024 * 1024)).toBe('1.5 GB')
    expect(formatBytes(2 * 1024 * 1024 * 1024)).toBe('2.0 GB')
    // 1TB - 1 byte = 여전히 GB 영역 (1023.99... GB → 1024.0 GB).
    expect(formatBytes(1024 * 1024 * 1024 * 1024 - 1)).toBe('1024.0 GB')
  })

  it('1TB 이상은 소수점 1자리 TB', () => {
    expect(formatBytes(1024 * 1024 * 1024 * 1024)).toBe('1.0 TB')
    expect(formatBytes(1.5 * 1024 * 1024 * 1024 * 1024)).toBe('1.5 TB')
    // v1.x storage.usedBytes 증가 대비 회귀 가드 — 기존엔 "2048.0 GB" 등으로 표기됨.
    expect(formatBytes(2 * 1024 * 1024 * 1024 * 1024)).toBe('2.0 TB')
  })

  it('NaN/Infinity는 "-" 폴백 (일시적 nullable API 반환 가드)', () => {
    expect(formatBytes(Number.NaN)).toBe('-')
    expect(formatBytes(Number.POSITIVE_INFINITY)).toBe('-')
    expect(formatBytes(Number.NEGATIVE_INFINITY)).toBe('-')
  })
})
