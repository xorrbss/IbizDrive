import { Info } from 'lucide-react'
import type { AdminFlaggedShare } from '@/lib/admin/sharingMock'

/**
 * Overview 페이지용 플래그 row — 디자인 핸드오프 2026-05-10 admin.jsx §FlagRow
 * (L665~692, expanded=false 분기) 1:1 매핑.
 *
 * <p>`/admin/sharing` 의 `SharingFlagged` 와 동일 mock data 를 사용하지만 overview 는
 * compact 1-line 요약: icon + 파일명 + reason + 상대 시간 (액션 버튼/actor meta 없음).
 * 전체 보기는 우측 상단 "전체 보기 →" 버튼이 `/admin/sharing` 으로 이동.
 *
 * <p>style: `.flag-row` (admin.css L455~464). `.expanded` 클래스 없이 grid 3-col
 * (icon / main / time) 모양.
 */
export interface FlagRowProps {
  item: AdminFlaggedShare
}

export function FlagRow({ item }: FlagRowProps) {
  return (
    <div className="flag-row">
      <div className="flag-icon" aria-hidden="true">
        <Info size={13} />
      </div>
      <div className="flag-main">
        <div className="flag-title">{item.file}</div>
        <div className="flag-reason">{item.reason}</div>
      </div>
      <span className="flag-time">{formatRelative(item.when)}</span>
    </div>
  )
}

/**
 * 간이 상대시간 포매터 — design data.js 의 formatRelative 대체. backend / shared
 * util 합류 시 교체. SharingFlagged 의 동일 helper 와 의도적으로 중복(KISS:
 * util 모듈 신설보다 컴포넌트 옆 inline 유지가 단순).
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
