import { describe, expect, it } from 'vitest'
import {
  TEAM_ARCHIVED,
  RENAME_CONFLICT,
  getApiErrorCode,
  messageForError,
} from './errors'

describe('errors — getApiErrorCode', () => {
  it('returns code from Error with .code field (buildApiError 결과)', () => {
    const err = Object.assign(new Error('boom'), {
      status: 423,
      code: 'TEAM_ARCHIVED',
    })
    expect(getApiErrorCode(err)).toBe('TEAM_ARCHIVED')
  })

  it('returns null when error has no code (네트워크 에러 등)', () => {
    expect(getApiErrorCode(new Error('network down'))).toBeNull()
  })

  it('returns null for non-Error inputs (string, undefined, null)', () => {
    expect(getApiErrorCode('TEAM_ARCHIVED')).toBeNull()
    expect(getApiErrorCode(undefined)).toBeNull()
    expect(getApiErrorCode(null)).toBeNull()
  })

  it('returns null when code is non-string (defensive)', () => {
    const err = Object.assign(new Error('weird'), { code: 423 })
    expect(getApiErrorCode(err)).toBeNull()
  })
})

describe('errors — messageForError', () => {
  it('TEAM_ARCHIVED → archived 팀 메시지 (PR #141 enforcement)', () => {
    const err = Object.assign(new Error('Locked'), {
      status: 423,
      code: 'TEAM_ARCHIVED',
    })
    expect(messageForError(err, 'fallback')).toBe(
      'archive된 팀의 콘텐츠는 수정할 수 없습니다',
    )
  })

  it('미등록 code (예: RENAME_CONFLICT) → fallback 사용', () => {
    const err = Object.assign(new Error('Conflict'), {
      status: 409,
      code: RENAME_CONFLICT,
    })
    expect(messageForError(err, '이동에 실패했습니다')).toBe('이동에 실패했습니다')
  })

  it('code 없는 일반 Error → fallback', () => {
    expect(messageForError(new Error('?'), '복원에 실패했습니다')).toBe(
      '복원에 실패했습니다',
    )
  })

  it('상수가 wire 값(접두사 ERR_ 없음)과 일치 — backend GlobalExceptionHandler 매핑과 정합', () => {
    expect(TEAM_ARCHIVED).toBe('TEAM_ARCHIVED')
    expect(RENAME_CONFLICT).toBe('RENAME_CONFLICT')
  })
})
