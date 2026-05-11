import { FileText } from 'lucide-react'
import {
  ADMIN_LEGAL_HOLDS,
  type AdminLegalHold,
} from '@/lib/admin/retentionMock'

/**
 * AdminRetention 법적 보존 위젯 — 디자인 핸드오프 2026-05-10 admin.jsx
 * §AdminRetention legal-list (L913~919) + LegalRow (L924~944) 1:1 매핑.
 *
 * <p>본 위젯은 design fidelity sweep Phase 3d 의 frontend-only 재현이다. Legal
 * Hold 본 기능은 `docs/03 §6.3` 에 v2.x deferred 로 명시되어 있어 backend
 * endpoint 는 아직 합류 전이며, 본 위젯은 mock(`ADMIN_LEGAL_HOLDS`) 을 그대로
 * 렌더한다. v2.x 진입 시 hook 으로 교체한다.
 *
 * <p>style: `.legal-list`, `.legal-row`, `.legal-main`, `.legal-file`,
 * `.legal-reason`, `.legal-until` (admin.css L695~711). 신규 CSS 없음 —
 * className wiring 만.
 *
 * <p>icon: lucide-react FileText 직접 import (admin overview/storage 위젯과
 * 동일 패턴). design 의 FileIcon kind=pdf 는 v1.0 generic FileText 로 단순화
 * 한다 (UIIcon 추상화 X — Sub-phase 1/2 와 동일 결정).
 */
export function LegalHoldList() {
  const holds = ADMIN_LEGAL_HOLDS

  if (holds.length === 0) {
    return <div className="empty-mini">법적 보존 항목 없음</div>
  }

  return (
    <div className="legal-list" aria-label="법적 보존 항목">
      {holds.map((h) => (
        <LegalRow key={h.id} hold={h} />
      ))}
    </div>
  )
}

function LegalRow({ hold }: { hold: AdminLegalHold }) {
  return (
    <div className="legal-row">
      <FileText size={16} aria-hidden="true" />
      <div className="legal-main">
        <div className="legal-file">{hold.file}</div>
        <div className="legal-reason">{hold.reason}</div>
      </div>
      <div className="legal-until">
        <div className="muted">보존 만료</div>
        <div>{hold.until}</div>
      </div>
      <div className="cell-user">
        <span className="muted" style={{ fontSize: 12 }}>
          {hold.byName}
        </span>
      </div>
      <button type="button" className="btn-ghost btn-xs" disabled>
        설정
      </button>
    </div>
  )
}
