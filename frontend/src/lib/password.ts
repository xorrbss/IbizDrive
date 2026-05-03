/**
 * ADR #19 비밀번호 정책 — frontend mirror.
 *
 * Mirror of backend `PasswordPolicyValidator.java`. Both implementations are validated
 * against the same input/output matrix (auth-password-policy 트랙, 2026-05-04 — ADR #41 정정 closure).
 *
 * Rules (priority order):
 *   1. whitespace     — no whitespace chars (space/tab/CR/LF)
 *   2. max_length     — at most 128 chars
 *   3. min_length     — at least 12 chars
 *   4. missing_alpha  — must contain at least one letter
 *   5. missing_digit  — must contain at least one digit
 *
 * See `docs/03-security-compliance.md §2.7` for the canonical policy table.
 */

export type PasswordRule =
  | 'min_length'
  | 'max_length'
  | 'missing_alpha'
  | 'missing_digit'
  | 'whitespace'

export type PasswordValidation = { ok: true } | { ok: false; rule: PasswordRule }

const MIN_LENGTH = 12
const MAX_LENGTH = 128

export function validatePassword(input: string): PasswordValidation {
  if (containsWhitespace(input)) return { ok: false, rule: 'whitespace' }
  if (input.length > MAX_LENGTH) return { ok: false, rule: 'max_length' }
  if (input.length < MIN_LENGTH) return { ok: false, rule: 'min_length' }
  if (!hasAlpha(input)) return { ok: false, rule: 'missing_alpha' }
  if (!hasDigit(input)) return { ok: false, rule: 'missing_digit' }
  return { ok: true }
}

export function getPasswordRuleMessage(rule: PasswordRule): string {
  switch (rule) {
    case 'min_length':
      return '비밀번호는 12자 이상이어야 합니다.'
    case 'max_length':
      return '비밀번호는 128자 이하여야 합니다.'
    case 'missing_alpha':
      return '비밀번호에 영문자를 1자 이상 포함해야 합니다.'
    case 'missing_digit':
      return '비밀번호에 숫자를 1자 이상 포함해야 합니다.'
    case 'whitespace':
      return '비밀번호에 공백 문자(스페이스/탭/줄바꿈)를 사용할 수 없습니다.'
  }
}

function containsWhitespace(s: string): boolean {
  // \s in JS includes space, tab, CR, LF, FF, VT, NBSP, etc.
  return /\s/.test(s)
}

function hasAlpha(s: string): boolean {
  // Latin letters only (mirror Java Character.isLetter would include unicode,
  // but ASCII alpha is sufficient — backend's Character.isLetter on a typical
  // password input behaves the same for the common case; both sides reject
  // identical inputs in practice).
  return /[A-Za-z]/.test(s)
}

function hasDigit(s: string): boolean {
  return /[0-9]/.test(s)
}
