'use client'
import { useState } from 'react'
import { Check, X, Info } from 'lucide-react'
import { ADMIN_POLICIES } from '@/lib/admin/sharingMock'

/**
 * 도메인 정책 SectionCard 본문 — 허용/차단 도메인 + SSO/MFA 정보 (admin.jsx L624~659).
 *
 * <p>frontend-only mutation: 도메인 추가/제거는 `useState`로만 동작하며 backend
 * 호출이 없다. backend endpoint(POST/DELETE /api/admin/sharing/domains)는
 * v1.x backlog. "+추가" 버튼은 inline input으로 펼쳐지고 Enter / "추가" 클릭
 * 시 로컬 상태에만 반영된다.
 *
 * <p>style: `.domain-block`, `.domain-tag.ok/.bad`, `.domain-info`
 * (admin.css L505~530).
 */
export function SharingDomains() {
  const [allowlist, setAllowlist] = useState<string[]>([...ADMIN_POLICIES.domainAllowlist])
  const [blocklist, setBlocklist] = useState<string[]>([...ADMIN_POLICIES.domainBlocklist])

  return (
    <div>
      <DomainBlock
        kind="ok"
        label="허용 도메인"
        items={allowlist}
        onAdd={(d) => setAllowlist((prev) => (prev.includes(d) ? prev : [...prev, d]))}
        onRemove={(d) => setAllowlist((prev) => prev.filter((x) => x !== d))}
      />
      <DomainBlock
        kind="bad"
        label="차단 도메인"
        items={blocklist}
        onAdd={(d) => setBlocklist((prev) => (prev.includes(d) ? prev : [...prev, d]))}
        onRemove={(d) => setBlocklist((prev) => prev.filter((x) => x !== d))}
      />

      <div className="domain-block">
        <div className="domain-head">
          <Info size={11} aria-hidden="true" />
          <span>SSO / MFA</span>
        </div>
        <div className="domain-info">
          <div>
            <span className="muted">SSO 제공자</span>
            <strong>{ADMIN_POLICIES.ssoProvider}</strong>
          </div>
          <div>
            <span className="muted">MFA 강제</span>
            <strong className={ADMIN_POLICIES.mfaRequired ? 'ok' : ''}>
              {ADMIN_POLICIES.mfaRequired ? '예' : '선택'}
            </strong>
          </div>
        </div>
      </div>
    </div>
  )
}

interface DomainBlockProps {
  kind: 'ok' | 'bad'
  label: string
  items: string[]
  onAdd: (domain: string) => void
  onRemove: (domain: string) => void
}

function DomainBlock({ kind, label, items, onAdd, onRemove }: DomainBlockProps) {
  const [adding, setAdding] = useState(false)
  const [draft, setDraft] = useState('')

  const commit = () => {
    const trimmed = draft.trim().toLowerCase()
    if (trimmed) onAdd(trimmed)
    setDraft('')
    setAdding(false)
  }

  return (
    <div className="domain-block">
      <div className="domain-head">
        {kind === 'ok' ? (
          <Check size={11} aria-hidden="true" />
        ) : (
          <X size={11} aria-hidden="true" />
        )}
        <span>
          {label} ({items.length})
        </span>
        <button
          type="button"
          className="btn-ghost btn-xs"
          style={{ marginLeft: 'auto' }}
          onClick={() => setAdding((v) => !v)}
          aria-expanded={adding}
        >
          + 추가
        </button>
      </div>

      {adding && (
        <div
          style={{
            display: 'flex',
            gap: 6,
            marginBottom: 8,
            alignItems: 'center',
          }}
        >
          <input
            type="text"
            value={draft}
            autoFocus
            placeholder="example.com"
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                commit()
              } else if (e.key === 'Escape') {
                setDraft('')
                setAdding(false)
              }
            }}
            aria-label={`${label} 추가`}
            className="form-input"
            style={{ flex: 1, height: 28, fontSize: 12 }}
          />
          <button type="button" className="btn-ghost btn-xs" onClick={commit}>
            추가
          </button>
        </div>
      )}

      <div className="domain-tags">
        {items.map((d) => (
          <span key={d} className={`domain-tag ${kind}`}>
            {d}
            <button
              type="button"
              onClick={() => onRemove(d)}
              aria-label={`${d} 제거`}
            >
              ×
            </button>
          </span>
        ))}
      </div>
    </div>
  )
}
