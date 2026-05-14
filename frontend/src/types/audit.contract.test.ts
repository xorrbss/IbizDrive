import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { describe, it, expect } from 'vitest'

/**
 * audit.ts ↔ backend AuditEventType.java 1:1 양방향 drift 가드.
 *
 * <p>두 enum은 wire format으로 직렬화되는 계약이라 어느 한쪽만 추가/삭제되면
 * 런타임에 `Unknown AuditEventType wire format` 예외 또는 frontend type unknown.
 * docs/03 §4.1이 계약 명세이지만 강제력이 없으므로 본 test가 양방향 set 일치를 강제.
 *
 * <p>backend 파일 경로는 monorepo 가정. worktree에서도 동일 구조라 그대로 작동.
 */

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
// types -> src -> frontend -> repo (or worktree) root
const REPO_ROOT = resolve(__dirname, '../../..')
const BACKEND_ENUM = resolve(
  REPO_ROOT,
  'backend/src/main/java/com/ibizdrive/audit/AuditEventType.java',
)
const FRONTEND_TYPE = resolve(__dirname, './audit.ts')

function readBackendWires(): string[] {
  const src = readFileSync(BACKEND_ENUM, 'utf8')
  const re = /\b[A-Z_]+\(\s*"([a-z._]+)"\s*\)/g
  const wires: string[] = []
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    wires.push(m[1])
  }
  return wires
}

function readFrontendUnionWires(): string[] {
  const src = readFileSync(FRONTEND_TYPE, 'utf8')
  const start = src.indexOf('export type AuditEventType')
  if (start === -1) throw new Error('AuditEventType union not found in audit.ts')
  const after = src.indexOf('export type AuditResourceType', start)
  if (after === -1) throw new Error('AuditResourceType marker not found (audit.ts shape changed?)')
  const block = src.slice(start, after)
  const re = /'([a-z._]+)'/g
  const wires: string[] = []
  let m: RegExpExecArray | null
  while ((m = re.exec(block)) !== null) {
    wires.push(m[1])
  }
  return wires
}

describe('AuditEventType ↔ backend AuditEventType.java 1:1 contract', () => {
  it('frontend union과 backend enum이 정확히 같은 wire 집합을 가진다', () => {
    const backend = new Set(readBackendWires())
    const frontend = new Set(readFrontendUnionWires())
    const onlyBackend = [...backend].filter((w) => !frontend.has(w)).sort()
    const onlyFrontend = [...frontend].filter((w) => !backend.has(w)).sort()
    expect({ onlyBackend, onlyFrontend }).toEqual({ onlyBackend: [], onlyFrontend: [] })
  })

  it('양쪽 모두 비어있지 않다 (정규식 실수로 0 매치 회귀 방지)', () => {
    const backend = readBackendWires()
    const frontend = readFrontendUnionWires()
    expect(backend.length).toBeGreaterThan(40)
    expect(frontend.length).toBeGreaterThan(40)
  })
})
