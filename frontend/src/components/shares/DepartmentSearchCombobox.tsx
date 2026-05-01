'use client'
import { useEffect, useId, useRef, useState } from 'react'
import { useDepartmentSearch } from '@/hooks/useDepartmentSearch'
import type { DepartmentSummary } from '@/types/department'

/**
 * A16.6 — 부서 검색 콤보박스 (A16 / docs/02 §7.15, ADR #37).
 *
 * UserSearchCombobox(F6.3) 1:1 답습 — 표시 필드만 변경(displayName+email → name).
 * WAI-ARIA 1.2 Combobox + Listbox 패턴.
 *
 * 동작/속성/접근성은 UserSearchCombobox와 동형. 자세한 설명은 그곳을 참조.
 */
export function DepartmentSearchCombobox({
  value,
  onChange,
  inputId,
  placeholder = '부서 이름',
}: {
  value: DepartmentSummary | null
  onChange: (dept: DepartmentSummary | null) => void
  inputId?: string
  placeholder?: string
}) {
  const [rawInput, setRawInput] = useState<string>(value ? value.name : '')
  const [isOpen, setIsOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState<number>(-1)

  const search = useDepartmentSearch(rawInput)
  const items: DepartmentSummary[] = search.data?.items ?? []
  const isFetching = search.isFetching

  const generatedListId = useId()
  const listboxId = `deptpicker-list-${generatedListId.replace(/:/g, '')}`
  const optionId = (idx: number) => `${listboxId}-opt-${idx}`

  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setActiveIndex(-1)
  }, [items.length, rawInput])

  const isQueryReady = rawInput.trim().length >= 2
  const showListbox = isOpen && isQueryReady

  const commit = (dept: DepartmentSummary) => {
    onChange(dept)
    setRawInput(dept.name)
    setIsOpen(false)
    setActiveIndex(-1)
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const next = e.target.value
    setRawInput(next)
    setIsOpen(true)
    if (value !== null) {
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
              일치하는 부서가 없습니다
            </li>
          )}
          {items.map((d, idx) => {
            const active = idx === activeIndex
            return (
              <li
                key={d.id}
                id={optionId(idx)}
                role="option"
                aria-selected={active}
                onMouseDown={(e) => {
                  e.preventDefault()
                }}
                onClick={() => commit(d)}
                className={`px-2 py-1.5 cursor-pointer text-[12.5px] ${
                  active ? 'bg-surface-2' : 'hover:bg-surface-2'
                }`}
              >
                <span className="text-fg">{d.name}</span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
