import { describe, expect, it } from 'vitest'
import { getPasswordRuleMessage, validatePassword, type PasswordRule } from './password'

/**
 * ADR #19 비밀번호 정책 — frontend 미러.
 * backend `PasswordPolicyValidator`와 동일 입출력 매트릭스 (auth-password-policy 트랙, 2026-05-04).
 *
 * 위반 우선순위: whitespace -> max_length -> min_length -> missing_alpha -> missing_digit
 */

describe('validatePassword — pass cases', () => {
  it('accepts 12 chars with alpha+digit (min boundary)', () => {
    expect(validatePassword('abcdefghijk1')).toEqual({ ok: true })
  })

  it('accepts 128 chars with alpha+digit (max boundary)', () => {
    const p = 'a'.repeat(64) + '1'.repeat(64)
    expect(p.length).toBe(128)
    expect(validatePassword(p)).toEqual({ ok: true })
  })

  it('accepts mixed alpha+digit+symbols', () => {
    expect(validatePassword('Password1!@#')).toEqual({ ok: true })
  })
})

describe('validatePassword — min_length', () => {
  it('rejects 11 chars even with alpha+digit', () => {
    expect(validatePassword('abcdefghij1')).toEqual({ ok: false, rule: 'min_length' })
  })

  it('rejects empty string', () => {
    expect(validatePassword('')).toEqual({ ok: false, rule: 'min_length' })
  })
})

describe('validatePassword — max_length', () => {
  it('rejects 129 chars with alpha+digit', () => {
    const p = 'a'.repeat(64) + '1'.repeat(65)
    expect(p.length).toBe(129)
    expect(validatePassword(p)).toEqual({ ok: false, rule: 'max_length' })
  })
})

describe('validatePassword — missing_alpha', () => {
  it('rejects 12 digits only', () => {
    expect(validatePassword('123456789012')).toEqual({ ok: false, rule: 'missing_alpha' })
  })

  it('rejects digits + symbols (no alpha)', () => {
    expect(validatePassword('123456!@#$%^')).toEqual({ ok: false, rule: 'missing_alpha' })
  })
})

describe('validatePassword — missing_digit', () => {
  it('rejects 12 alpha only', () => {
    expect(validatePassword('abcdefghijkl')).toEqual({ ok: false, rule: 'missing_digit' })
  })

  it('rejects alpha + symbols (no digit)', () => {
    expect(validatePassword('abcdef!@#$%^')).toEqual({ ok: false, rule: 'missing_digit' })
  })
})

describe('validatePassword — whitespace', () => {
  it('rejects space', () => {
    expect(validatePassword('abcdef 12345')).toEqual({ ok: false, rule: 'whitespace' })
  })

  it('rejects tab', () => {
    expect(validatePassword('abcdef\t12345')).toEqual({ ok: false, rule: 'whitespace' })
  })

  it('rejects newline', () => {
    expect(validatePassword('abcdef\n12345')).toEqual({ ok: false, rule: 'whitespace' })
  })

  it('rejects CR', () => {
    expect(validatePassword('abcdef\r12345')).toEqual({ ok: false, rule: 'whitespace' })
  })
})

describe('validatePassword — priority', () => {
  it('whitespace beats min_length', () => {
    expect(validatePassword('ab 12')).toEqual({ ok: false, rule: 'whitespace' })
  })

  it('whitespace beats max_length', () => {
    const p = 'a'.repeat(64) + ' ' + '1'.repeat(64)
    expect(validatePassword(p)).toEqual({ ok: false, rule: 'whitespace' })
  })

  it('max_length beats missing_alpha', () => {
    const p = '1'.repeat(129)
    expect(validatePassword(p)).toEqual({ ok: false, rule: 'max_length' })
  })

  it('min_length beats missing_alpha', () => {
    expect(validatePassword('12345678901')).toEqual({ ok: false, rule: 'min_length' })
  })

  it('missing_alpha beats missing_digit', () => {
    expect(validatePassword('!@#$%^&*()_+')).toEqual({ ok: false, rule: 'missing_alpha' })
  })
})

describe('getPasswordRuleMessage', () => {
  const rules: PasswordRule[] = [
    'min_length',
    'max_length',
    'missing_alpha',
    'missing_digit',
    'whitespace',
  ]

  it.each(rules)('returns non-empty Korean message for %s', (rule) => {
    const msg = getPasswordRuleMessage(rule)
    expect(msg).toBeTruthy()
    expect(msg.length).toBeGreaterThan(0)
  })

  it('all rule messages are distinct', () => {
    const msgs = rules.map(getPasswordRuleMessage)
    expect(new Set(msgs).size).toBe(rules.length)
  })
})
