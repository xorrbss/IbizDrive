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

  it('1GB 이상은 소수점 1자리 GB', () => {
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1.0 GB')
    expect(formatBytes(1.5 * 1024 * 1024 * 1024)).toBe('1.5 GB')
    expect(formatBytes(2 * 1024 * 1024 * 1024)).toBe('2.0 GB')
  })
})
