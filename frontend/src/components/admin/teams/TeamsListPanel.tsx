'use client'
import { Plus, Search } from 'lucide-react'
import type { AdminTeamSummary } from '@/lib/api'

/**
 * 좌측 팀 리스트 패널 — admin-teams.jsx (line 71~104) 1:1 매핑.
 *
 * <p>구성: 헤더(타이틀 + 카운트 + 등록 버튼) → 검색 입력 → 스크롤 가능한 팀 row 목록.
 * row 클릭 시 onSelect(id). 검색은 client-side (이름/설명 부분 일치, 대소문자 무관).
 *
 * <p>folder count는 v1.x deferred — 디자인 placeholder를 maintain (memberCount만 표시).
 */
export interface TeamsListPanelProps {
  teams: AdminTeamSummary[]
  selectedId: string | null
  onSelect: (id: string) => void
  query: string
  onQueryChange: (q: string) => void
  onCreate?: () => void
  /** AUDITOR read-only — 등록 버튼 숨김. */
  canCreate?: boolean
}

export function TeamsListPanel({
  teams,
  selectedId,
  onSelect,
  query,
  onQueryChange,
  onCreate,
  canCreate = true,
}: TeamsListPanelProps) {
  const q = query.trim().toLowerCase()
  const filtered = q
    ? teams.filter(
        (t) =>
          t.name.toLowerCase().includes(q) ||
          (t.description ?? '').toLowerCase().includes(q),
      )
    : teams

  return (
    <aside className="teams-list-panel">
      <div className="teams-list-head">
        <div className="teams-list-title">
          <span>팀</span>
          <span className="muted-count">{teams.length}</span>
        </div>
        {canCreate && onCreate && (
          <button
            type="button"
            className="btn-primary btn-sm"
            onClick={onCreate}
            aria-label="팀 등록"
          >
            <Plus size={12} aria-hidden />
            <span>팀 등록</span>
          </button>
        )}
      </div>

      <div className="teams-search">
        <Search size={12} aria-hidden />
        <input
          type="search"
          placeholder="팀 검색"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          aria-label="팀 검색"
        />
      </div>

      <div className="teams-list" role="list">
        {filtered.map((t) => (
          <button
            key={t.id}
            type="button"
            role="listitem"
            className={`team-row ${t.id === selectedId ? 'active' : ''}`}
            onClick={() => onSelect(t.id)}
            aria-current={t.id === selectedId ? 'true' : undefined}
          >
            <span
              className="team-row-swatch"
              style={{ background: t.color }}
              aria-hidden
            />
            <div className="team-row-body">
              <div className="team-row-name">
                {t.name}
                {t.archived && (
                  <span className="muted-count" style={{ marginLeft: 6 }}>
                    (archived)
                  </span>
                )}
              </div>
              <div className="team-row-meta">
                <span>{t.memberCount}명</span>
                <span className="dot-sep">·</span>
                <span>폴더 0</span>
              </div>
            </div>
          </button>
        ))}
        {filtered.length === 0 && (
          <div className="picker-empty" role="status">
            검색 결과가 없습니다.
          </div>
        )}
      </div>
    </aside>
  )
}
