'use client'
import { useEffect, useMemo, useState } from 'react'
import { Check, Search, X } from 'lucide-react'
import { useAdminUsers } from '@/hooks/useAdminUsers'
import { PAvatar } from './Avatars'

/**
 * 멤버 추가 모달 — admin-teams.jsx MemberPickerModal (line 342~398) 1:1 매핑.
 *
 * <p>candidate source: admin user list (`useAdminUsers(0, 100, q)`) — 활성 사용자 일괄 fetch.
 * 디자인의 "이름/이메일/부서" 검색 중 부서는 admin user 응답에 부재 → 이름+이메일만.
 *
 * <p>excludeIds로 현재 팀 멤버를 client-side에서 제외. 다중 선택 후 "추가" 클릭 시
 * onAdd(ids[])로 일괄 위임 — 호출자가 sequential POST로 처리.
 */
export interface PickedUser {
  id: string
  displayName: string
  email: string
}

export interface MemberPickerModalProps {
  excludeIds?: string[]
  onClose: () => void
  /**
   * 다중 선택 후 "추가" 클릭 시 호출. id 배열 + name lookup 둘 다 받을 수 있도록
   * users 형태로 위임 — 호출자가 즉시 chip 갱신할 수 있다.
   */
  onAdd: (users: PickedUser[]) => void | Promise<void>
}

export function MemberPickerModal({
  excludeIds = [],
  onClose,
  onAdd,
}: MemberPickerModalProps) {
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [submitting, setSubmitting] = useState(false)

  // 300ms debounce — useAdminUsers는 q를 raw로 받지만 list refetch 빈도 줄이기.
  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(query), 300)
    return () => window.clearTimeout(t)
  }, [query])

  const { data, isLoading } = useAdminUsers(0, 100, debounced)
  const exclude = useMemo(() => new Set(excludeIds), [excludeIds])

  const candidates = useMemo(() => {
    const list = (data?.content ?? []).filter(
      (u) => u.isActive && !exclude.has(u.id),
    )
    return list
  }, [data, exclude])

  const toggle = (id: string) => {
    setPicked((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const submit = async () => {
    if (picked.size === 0 || submitting) return
    setSubmitting(true)
    try {
      const pickedUsers: PickedUser[] = candidates
        .filter((c) => picked.has(c.id))
        .map((c) => ({ id: c.id, displayName: c.displayName, email: c.email }))
      await onAdd(pickedUsers)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      className="modal-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="member-picker-title"
    >
      <div className="modal modal-md" onClick={(e) => e.stopPropagation()}>
        <header className="modal-head">
          <h2 id="member-picker-title">멤버 추가</h2>
          <button
            type="button"
            className="icon-btn-ghost"
            onClick={onClose}
            aria-label="닫기"
          >
            <X size={13} aria-hidden />
          </button>
        </header>
        <div className="modal-body modal-body-flush">
          <div className="picker-search">
            <Search size={13} aria-hidden />
            <input
              type="search"
              placeholder="이름, 이메일로 검색"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              autoFocus
              aria-label="사용자 검색"
            />
          </div>
          <div className="picker-list">
            {isLoading && (
              <div className="picker-empty" role="status">
                불러오는 중…
              </div>
            )}
            {!isLoading &&
              candidates.map((u) => {
                const checked = picked.has(u.id)
                return (
                  <button
                    key={u.id}
                    type="button"
                    className={`picker-row ${checked ? 'active' : ''}`}
                    onClick={() => toggle(u.id)}
                    aria-pressed={checked}
                  >
                    <span className={`picker-check ${checked ? 'checked' : ''}`}>
                      {checked && <Check size={10} aria-hidden />}
                    </span>
                    <PAvatar userId={u.id} name={u.displayName} size={28} />
                    <div className="picker-row-body">
                      <div className="picker-row-name">{u.displayName}</div>
                      <div className="picker-row-sub">{u.email}</div>
                    </div>
                  </button>
                )
              })}
            {!isLoading && candidates.length === 0 && (
              <div className="picker-empty" role="status">
                검색 결과가 없습니다.
              </div>
            )}
          </div>
        </div>
        <footer className="modal-foot">
          <span className="modal-foot-meta">{picked.size}명 선택</span>
          <div className="modal-foot-actions">
            <button type="button" className="btn-ghost" onClick={onClose}>
              취소
            </button>
            <button
              type="button"
              className="btn-primary"
              disabled={picked.size === 0 || submitting}
              onClick={submit}
            >
              {submitting ? '추가 중…' : '추가'}
            </button>
          </div>
        </footer>
      </div>
    </div>
  )
}
