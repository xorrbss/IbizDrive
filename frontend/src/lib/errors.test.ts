import { describe, expect, it } from 'vitest'
import {
  TEAM_ARCHIVED,
  RENAME_CONFLICT,
  APPROVAL_REQUIRED,
  APPROVAL_SELF,
  APPROVAL_NOT_FOUND,
  APPROVAL_ALREADY_DECIDED,
  ApprovalRequiredError,
  getApiErrorCode,
  messageForError,
  parseApprovalRequired,
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

describe('errors — dual-approval (ADR #47)', () => {
  it('APPROVAL_* 상수 wire 값 정합 (backend ApiError code 1:1)', () => {
    expect(APPROVAL_REQUIRED).toBe('APPROVAL_REQUIRED')
    expect(APPROVAL_SELF).toBe('APPROVAL_SELF')
    expect(APPROVAL_NOT_FOUND).toBe('APPROVAL_NOT_FOUND')
    expect(APPROVAL_ALREADY_DECIDED).toBe('APPROVAL_ALREADY_DECIDED')
  })

  it('ApprovalRequiredError — name/message/approvalId/expiresAt 노출', () => {
    const err = new ApprovalRequiredError(
      'aaaa-bbbb',
      '2026-05-15T00:00:00Z',
      '커스텀 메시지',
    )
    expect(err).toBeInstanceOf(Error)
    expect(err.name).toBe('ApprovalRequiredError')
    expect(err.message).toBe('커스텀 메시지')
    expect(err.approvalId).toBe('aaaa-bbbb')
    expect(err.expiresAt).toBe('2026-05-15T00:00:00Z')
  })

  it('ApprovalRequiredError — message 생략 시 기본 메시지', () => {
    const err = new ApprovalRequiredError('id', 'iso')
    expect(err.message).toBe('승인 요청이 등록되었습니다')
  })
})

describe('errors — parseApprovalRequired', () => {
  function jsonResponse(body: unknown, status: number): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'content-type': 'application/json' },
    })
  }

  it('valid envelope → ApprovalRequiredError 인스턴스 (approvalId + expiresAt + message 보존)', async () => {
    const res = jsonResponse(
      {
        error: {
          code: 'APPROVAL_REQUIRED',
          message: '이 작업은 2인 승인이 필요합니다',
          details: {
            approvalId: '11111111-1111-1111-1111-111111111111',
            expiresAt: '2026-05-15T00:00:00Z',
          },
        },
      },
      202,
    )
    const err = await parseApprovalRequired(res)
    expect(err).toBeInstanceOf(ApprovalRequiredError)
    expect(err!.approvalId).toBe('11111111-1111-1111-1111-111111111111')
    expect(err!.expiresAt).toBe('2026-05-15T00:00:00Z')
    expect(err!.message).toBe('이 작업은 2인 승인이 필요합니다')
  })

  it('다른 code (예: VALIDATION_ERROR) → null (envelope mismatch)', async () => {
    const res = jsonResponse(
      { error: { code: 'VALIDATION_ERROR', message: 'bad' } },
      202,
    )
    expect(await parseApprovalRequired(res)).toBeNull()
  })

  it('details.approvalId 누락 → null (envelope 오염)', async () => {
    const res = jsonResponse(
      {
        error: {
          code: 'APPROVAL_REQUIRED',
          details: { expiresAt: '2026-05-15T00:00:00Z' },
        },
      },
      202,
    )
    expect(await parseApprovalRequired(res)).toBeNull()
  })

  it('details.expiresAt 누락 → null (envelope 오염)', async () => {
    const res = jsonResponse(
      {
        error: {
          code: 'APPROVAL_REQUIRED',
          details: { approvalId: 'aaa' },
        },
      },
      202,
    )
    expect(await parseApprovalRequired(res)).toBeNull()
  })

  it('JSON 본문 부재 (예: 204) → null', async () => {
    const res = new Response(null, { status: 204 })
    expect(await parseApprovalRequired(res)).toBeNull()
  })

  it('비-JSON 본문 → null (catch 분기)', async () => {
    const res = new Response('not-json', {
      status: 202,
      headers: { 'content-type': 'text/plain' },
    })
    expect(await parseApprovalRequired(res)).toBeNull()
  })
})
