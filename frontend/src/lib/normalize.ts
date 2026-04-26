/**
 * 7-step normalization pipeline — see `docs/02-backend-data-model.md §3`.
 * Mirror of backend `NormalizeUtil.java`. Both implementations are validated
 * against the shared corpus `docs/normalize-fixtures.json` (ADR #16).
 *
 * Three public functions — see the spec for which step set each applies.
 */

export const ERR_NUL_CHAR = 'INVALID_NAME_NUL_CHAR'
export const ERR_EMPTY = 'INVALID_NAME_EMPTY'
export const ERR_TOO_LONG = 'INVALID_NAME_TOO_LONG'
export const ERR_FORBIDDEN_CHAR = 'INVALID_NAME_FORBIDDEN_CHAR'
export const ERR_RESERVED = 'INVALID_NAME_RESERVED'
export const ERR_TRAILING_DOT = 'INVALID_NAME_TRAILING_DOT'

export type NormalizationErrorCode =
  | typeof ERR_NUL_CHAR
  | typeof ERR_EMPTY
  | typeof ERR_TOO_LONG
  | typeof ERR_FORBIDDEN_CHAR
  | typeof ERR_RESERVED
  | typeof ERR_TRAILING_DOT

export class NormalizationError extends Error {
  readonly code: NormalizationErrorCode
  constructor(code: NormalizationErrorCode) {
    super(code)
    this.code = code
    this.name = 'NormalizationError'
  }
}

const MAX_LENGTH = 255

const RESERVED_NAMES = new Set([
  'CON', 'PRN', 'AUX', 'NUL',
  'COM1', 'COM2', 'COM3', 'COM4', 'COM5', 'COM6', 'COM7', 'COM8', 'COM9',
  'LPT1', 'LPT2', 'LPT3', 'LPT4', 'LPT5', 'LPT6', 'LPT7', 'LPT8', 'LPT9',
])

/** Steps 1-2-3-6-7: NFC, control strip, whitespace unify, trim, validate. */
export function normalizeFileName(input: string): string {
  let s = nfc(input)
  s = stripControlChars(s)
  s = unifyWhitespace(s)
  s = s.trim()
  validateForFilename(s)
  return s
}

/** Steps 1-2-3-5-6-7: + lowercase. */
export function normalizedNameForDedup(input: string): string {
  let s = nfc(input)
  s = stripControlChars(s)
  s = unifyWhitespace(s)
  s = s.toLowerCase()
  s = s.trim()
  validateForFilename(s)
  return s
}

/** Steps 1-2-3-5-4-6: + collapse, no length/forbidden/reserved/trailing-dot validation. */
export function normalizeForSearch(input: string): string {
  let s = nfc(input)
  s = stripControlChars(s)
  s = unifyWhitespace(s)
  s = s.toLowerCase()
  s = collapseWhitespace(s)
  s = s.trim()
  return s
}

// ---- pipeline steps ----

function nfc(input: string): string {
  if (input == null) {
    throw new NormalizationError(ERR_EMPTY)
  }
  return input.normalize('NFC')
}

/**
 * Step 2: NUL is rejected; C0 (excluding NUL) becomes a space; DEL/C1/ZWSP/BIDI/WJ/BOM are dropped.
 */
function stripControlChars(input: string): string {
  let out = ''
  for (const ch of input) {
    const cp = ch.codePointAt(0)!
    if (cp === 0x0000) {
      throw new NormalizationError(ERR_NUL_CHAR)
    }
    if (cp >= 0x0001 && cp <= 0x001f) {
      out += ' '
      continue
    }
    if (cp === 0x007f) continue
    if (cp >= 0x0080 && cp <= 0x009f) continue
    if (cp >= 0x200b && cp <= 0x200f) continue
    if (cp >= 0x202a && cp <= 0x202e) continue
    if (cp === 0x2060) continue
    if (cp === 0xfeff) continue
    out += ch
  }
  return out
}

/** Step 3: NBSP, fullwidth space, etc → ASCII space. */
function unifyWhitespace(input: string): string {
  let out = ''
  for (const ch of input) {
    const cp = ch.codePointAt(0)!
    if (
      cp === 0x00a0 ||
      (cp >= 0x2000 && cp <= 0x200a) ||
      cp === 0x202f ||
      cp === 0x205f ||
      cp === 0x3000
    ) {
      out += ' '
    } else {
      out += ch
    }
  }
  return out
}

/** Step 4 (search only): collapse runs of ASCII spaces. */
function collapseWhitespace(input: string): string {
  return input.replace(/ +/g, ' ')
}

/** Step 7: shared validation for filename / dedup. */
function validateForFilename(s: string): void {
  if (s.length === 0) {
    throw new NormalizationError(ERR_EMPTY)
  }
  if (s.length > MAX_LENGTH) {
    throw new NormalizationError(ERR_TOO_LONG)
  }
  if (s.indexOf('/') >= 0 || s.indexOf('\\') >= 0) {
    throw new NormalizationError(ERR_FORBIDDEN_CHAR)
  }
  if (s.endsWith('.')) {
    throw new NormalizationError(ERR_TRAILING_DOT)
  }
  const dot = s.indexOf('.')
  const base = dot >= 0 ? s.substring(0, dot) : s
  if (RESERVED_NAMES.has(base.toUpperCase())) {
    throw new NormalizationError(ERR_RESERVED)
  }
}
