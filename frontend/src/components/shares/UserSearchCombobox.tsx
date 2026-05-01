'use client'
import { useEffect, useId, useRef, useState } from 'react'
import { useUserSearch } from '@/hooks/useUserSearch'
import type { UserSummary } from '@/types/user'

/**
 * F6.3 — 사용자 검색 콤보박스 (A14 / docs/02 §7.14, ADR #35).
 *
 * WAI-ARIA 1.2 Combobox + Listbox 패턴 (외부 a11y 라이브러리 거부 — KISS).
 *
 * 동작:
 * - input에 typing → useUserSearch(rawInput) → listbox 노출.
 * - ArrowDown/Up: activedescendant rotate (wrap-around).
 * - Enter: 현재 active option 선택. 미활성이면 무시.
 * - Esc: listbox close, input 보존.
 * - Click option: 선택 + close.
 * - 선택 후 input 재입력 → onChange(null) (선택 해제, RenameDialog input-as-state 패턴).
 *
 * value prop은 controlled selection. input 텍스트는 internal state — 부모가 reset하려면
 * value=null로 넘기고 컴포넌트를 remount(또는 key 교체).
 */
export function UserSearchCombobox({
  value,
  onChange,
  inputId,
  placeholder = '사용자 이름 또는 이메일',
}: {
  value: UserSummary | null
  onChange: (user: UserSummary | null) => void
  inputId?: string
  placeholder?: string
}) {
  // input 텍스트 — 선택 상태와 분리. 선택 시 displayName으로 채우고, 재입력 시 onChange(null).
  const [rawInput, setRawInput] = useState<string>(value ? value.displayName : '')
  const [isOpen, setIsOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState<number>(-1)

  const search = useUserSearch(rawInput)
  const items: UserSummary[] = search.data?.items ?? []
  const isFetching = search.isFetching

  const generatedListId = useId()
  const listboxId = `userpicker-list-${generatedListId.replace(/:/g, '')}`
  const optionId = (idx: number) => `${listboxId}-opt-${idx}`

  const inputRef = useRef<HTMLInputElement>(null)

  // 결과가 바뀌면 activeIndex 0으로 리셋(선택 가능 항목 있을 때만 ArrowDown으로 활성화).
  useEffect(() => {
    setActiveIndex(-1)
  }, [items.length, rawInput])

  const isQueryReady = rawInput.trim().length >= 2
  const showListbox = isOpen && isQueryReady

  const commit = (user: UserSummary) => {
    onChange(user)
    setRawInput(user.displayName)
    setIsOpen(false)
    setActiveIndex(-1)
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const next = e.target.value
    setRawInput(next)
    setIsOpen(true)
    if (value !== null) {
      // 이전 선택 무효화 (RenameDialog input-as-state 패턴)
      onChange(null)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      if (items.length === 0) return
      setIsOpen(true)
      setActiveIndex((idx) => (idx + 1) % items.length)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      if (items.length === 0) return
      setIsOpen(true)
      setActiveIndex((idx) => (idx <= 0 ? items.length - 1 : idx - 1))
      return
    }
    if (e.key === 'Enter') {
      if (activeIndex >= 0 && items[activeIndex]) {
        e.preventDefault()
        commit(items[activeIndex])
      }
      return
    }
    if (e.key === 'Escape') {
      // dialog가 자체 Esc 처리 — propagation 막고 자체적으로만 close.
      if (showListbox) {
        e.stopPropagation()
        setIsOpen(false)
        setActiveIndex(-1)
      }
      return
    }
  }

  return (
    <div className="relative">
      <input
        ref={inputRef}
        id={inputId}
        role="combobox"
        type="text"
        value={rawInput}
        placeholder={placeholder}
        onChange={handleInputChange}
        onFocus={() => setIsOpen(true)}
        onKeyDown={handleKeyDown}
        aria-autocomplete="list"
        aria-expanded={showListbox}
        aria-controls={showListbox ? listboxId : undefined}
        aria-activedescendant={
          showListbox && activeIndex >= 0 ? optionId(activeIndex) : undefined
        }
        className="h-8 w-full px-2 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
      />
      {showListbox && (
        <ul
          id={listboxId}
          role="listbox"
          className="absolute left-0 right-0 mt-1 max-h-[200px] overflow-auto bg-surface-1 border border-border rounded-md shadow-lg z-10"
        >
          {isFetching && items.length === 0 && (
            <li className="px-2 py-1.5 text-[12px] text-fg-muted" aria-live="polite">
              검색 중…
            </li>
          )}
          {!isFetching && items.length === 0 && (
            <li className="px-2 py-1.5 text-[12px] text-fg-muted" aria-live="polite">
              일치하는 사용자가 없습니다
            </li>
          )}
          {items.map((u, idx) => {
            const active = idx === activeIndex
            return (
              <li
                key={u.id}
                id={optionId(idx)}
                role="option"
                aria-selected={active}
                onMouseDown={(e) => {
                  // input focus 유지 (onClick 시 input blur 우회)
                  e.preventDefault()
                }}
                onClick={() => commit(u)}
                className={`px-2 py-1.5 cursor-pointer text-[12.5px] ${
                  active ? 'bg-surface-2' : 'hover:bg-surface-2'
                }`}
              >
                <span className="text-fg">{u.displayName}</span>{' '}
                <span className="text-fg-muted text-[11.5px]">{u.email}</span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
