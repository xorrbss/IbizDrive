'use client'
import { Info } from 'lucide-react'
import { ADMIN_FLAGGED, FLAGGED_ACTOR_NAMES, type AdminFlaggedShare } from '@/lib/admin/sharingMock'

/**
 * 검토 대기 공유 큐 — flagged share 큰 row 리스트 (admin.jsx L590~592 + FlagRow L665~692).
 *
 * <p>각 row는 `.flag-row.expanded` 스타일로 actor + 시간 메타 + 액션 버튼(차단/승인/검토)을
 * 함께 표시한다. 버튼은 backend mutation endpoint(POST /api/admin/sharing/flagged/:id/*)가
 * v1.x backlog이므로 disabled + title tooltip으로 처리 (frontend visual only).
 *
 * <p>style: `.flag-list.big`, `.flag-row.expanded`, `.flag-icon`,
 * `.flag-main/title/reason/meta/actions` (admin.css L453~481).
 */
export function SharingFlagged() {
  return (
    <div className="flag-list big">
      {ADMIN_FLAGGED.map((item) => (
        <FlagRow key={item.id} item={item} />
      ))}
    </div>
  )
}

interface FlagRowProps {
  item: AdminFlaggedShare
}

function FlagRow({ item }: FlagRowProps) {
  const actorName = FLAGGED_ACTOR_NAMES[item.actor] ?? item.actor

  return (
    <div className="flag-row expanded">
      <div className="flag-icon" aria-hidden="true">
        <Info size={13} />
      </div>
      <div className="flag-main">
        <div className="flag-title">{item.file}</div>
        <div className="flag-reason">{item.reason}</div>
        <div className="flag-meta">
          <span>{actorName}</span>
          <span className="muted" style={{ color: 'var(--fg-muted)' }}>
            · {formatRelative(item.when)}
          </span>
        </div>
      </div>
      <div className="flag-actions">
        <button
          type="button"
          className="btn-ghost btn-xs btn-danger"
          disabled
          title="v1.x 후속 트랙 — backend sharing flagged endpoint 미연결"
          aria-disabled="true"
        >
          차단
        </button>
        <button
          type="button"
          className="btn-ghost btn-xs"
          disabled
          title="v1.x 후속 트랙 — backend sharing flagged endpoint 미연결"
          aria-disabled="true"
        >
          승인
        </button>
        <button
          type="button"
          className="btn-primary btn-xs"
          disabled
          title="v1.x 후속 트랙 — backend sharing flagged endpoint 미연결"
          aria-disabled="true"
        >
          검토
        </button>
      </div>
    </div>
  )
}

/**
 * 간이 상대시간 포매터 — design data.js의 formatRelative 대체. backend / shared
 * util이 합류하면 교체.
 */
function formatRelative(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return iso
  const diffMs = Date.now() - then
  const min = Math.floor(diffMs / 60_000)
  if (min < 1) return '방금 전'
  if (min < 60) return `${min}분 전`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}시간 전`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day}일 전`
  const month = Math.floor(day / 30)
  if (month < 12) return `${month}개월 전`
  return `${Math.floor(month / 12)}년 전`
}
