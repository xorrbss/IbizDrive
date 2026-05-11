import { Trash2, FileX, Link2, ShieldOff } from 'lucide-react'
import {
  ADMIN_CLEANUP,
  ADMIN_CLEANUP_LABEL,
  type AdminCleanupEntry,
  type AdminCleanupKind,
} from '@/lib/admin/storageMock'
import { formatTBGB } from '@/lib/admin/overviewMock'

/**
 * AdminStorage 정리 기록 위젯 — 디자인 핸드오프 2026-05-10 admin.jsx
 * §AdminStorage cleanup-list (L448~517) 1:1 매핑.
 *
 * <p>정리 작업 카테고리 4종(휴지통 자동 / orphan / 만료 공유 / 만료 권한)을 시간 역순으로
 * 표시. 각 row 는 icon + label + meta(시각 + 처리 객체 수) + 회수 용량.
 *
 * <p>본 위젯은 design fidelity sweep Phase 3c 의 frontend-only 재현이다. 현재
 * backend `/api/admin/storage/overview` 응답은 `orphanCleanup` 1건만 노출하므로
 * 4-카테고리 시리즈는 mock(`ADMIN_CLEANUP`) 으로 보강. cleanup metric endpoint
 * 합류 시 hook 으로 교체 (v1.x backlog).
 *
 * <p>style: `.cleanup-list`, `.cleanup-row`, `.cleanup-main`, `.cleanup-label`,
 * `.cleanup-meta`, `.cleanup-size`, `.cleanup-total` (admin.css L432~450).
 * 신규 CSS 없음 — className wiring 만.
 *
 * <p>icon 매핑(lucide-react 직접 import — admin overview/sharing 위젯과 동일 패턴):
 * <ul>
 *   <li>trash-auto → Trash2</li>
 *   <li>orphan → FileX</li>
 *   <li>expired-share → Link2</li>
 *   <li>expired-permission → ShieldOff</li>
 * </ul>
 */
export function CleanupList() {
  const entries = ADMIN_CLEANUP
  const totalBytes = entries.reduce((a, e) => a + e.reclaimedBytes, 0)
  const totalObjects = entries.reduce((a, e) => a + e.objects, 0)

  if (entries.length === 0) {
    return <div className="empty-mini">정리 기록 없음</div>
  }

  return (
    <div>
      <ul className="cleanup-list" aria-label="정리 기록">
        {entries.map((e) => (
          <CleanupRow key={e.id} entry={e} />
        ))}
      </ul>
      <div className="cleanup-total" aria-label="정리 합계">
        <span>합계</span>
        <strong>{formatTBGB(totalBytes)}</strong>
        <span className="text-fg-2" style={{ fontSize: 11.5 }}>
          / {totalObjects.toLocaleString('ko-KR')}건
        </span>
      </div>
    </div>
  )
}

function CleanupRow({ entry }: { entry: AdminCleanupEntry }) {
  return (
    <li className="cleanup-row">
      <CleanupIcon kind={entry.kind} />
      <div className="cleanup-main">
        <div className="cleanup-label">{ADMIN_CLEANUP_LABEL[entry.kind]}</div>
        <div className="cleanup-meta">
          {formatWhen(entry.when)} · {entry.objects.toLocaleString('ko-KR')}건 처리
        </div>
      </div>
      <span className="cleanup-size">
        {entry.reclaimedBytes > 0 ? formatTBGB(entry.reclaimedBytes) : '-'}
      </span>
    </li>
  )
}

function CleanupIcon({ kind }: { kind: AdminCleanupKind }) {
  switch (kind) {
    case 'trash-auto':
      return <Trash2 size={14} aria-hidden="true" />
    case 'orphan':
      return <FileX size={14} aria-hidden="true" />
    case 'expired-share':
      return <Link2 size={14} aria-hidden="true" />
    case 'expired-permission':
      return <ShieldOff size={14} aria-hidden="true" />
  }
}

/**
 * 정리 기록 시각 포매터 — `5/11 02:00` 형태 (ko-KR locale). design data.js 의 동일
 * helper. backend endpoint 합류 시 ISO 그대로 받아 본 helper 로 포맷한다.
 */
function formatWhen(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const m = d.getMonth() + 1
  const day = d.getDate()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${m}/${day} ${hh}:${mm}`
}
