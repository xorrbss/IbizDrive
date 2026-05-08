'use client'
import { SlidersHorizontal } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useVariant } from '@/hooks/useVariant'
import type { Variant } from '@/lib/variant'
import { ThemeToggle } from './ThemeToggle'

const VARIANT_LABELS: Record<Variant, string> = {
  default: '기본',
  notion: 'Notion',
  dropbox: 'Dropbox',
  terminal: 'Terminal',
}

const VARIANTS: Variant[] = ['default', 'notion', 'dropbox', 'terminal']

/**
 * 디자인 미세 조정 패널 (M13.1).
 *
 * - 트리거: TopBar 의 SlidersHorizontal 아이콘 버튼.
 * - 내용: 테마 (ThemeToggle 임베드) + variant 4종 라디오 그룹.
 * - outside click + Esc 키로 닫힘. focus trap 미적용 (v1.x).
 *
 * variant 진실 출처: localStorage('variant') + [data-variant] (lib/variant.ts).
 */
export function TweaksPanel() {
  const { variant, setVariant } = useVariant()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement | null>(null)

  // outside click → close
  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (!ref.current) return
      if (!ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  // Esc → close
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label="설정"
        title="설정"
        className="h-8 w-8 inline-flex items-center justify-center rounded-md text-fg-2 hover:bg-surface-2 hover:text-fg transition-colors focus-visible:outline-2 focus-visible:outline-accent"
      >
        <SlidersHorizontal aria-hidden size={16} />
      </button>
      {open && (
        <div
          role="dialog"
          aria-label="디자인 설정"
          className="absolute z-10 mt-1 right-0 w-56 rounded border border-border bg-surface-1 shadow-md py-2 text-[12.5px]"
        >
          <section className="px-3 py-1.5 flex items-center justify-between">
            <span className="text-fg-muted">테마</span>
            <ThemeToggle />
          </section>
          <div className="my-1 border-t border-border" />
          <section className="px-3 py-1.5">
            <div className="text-fg-muted mb-1.5">변형</div>
            <div
              role="radiogroup"
              aria-label="디자인 변형"
              className="flex flex-col gap-0.5"
            >
              {VARIANTS.map((v) => {
                const active = v === variant
                return (
                  <button
                    key={v}
                    type="button"
                    role="radio"
                    aria-checked={active}
                    onClick={() => setVariant(v)}
                    className={`w-full text-left px-2 py-1 rounded hover:bg-surface-2 flex items-center justify-between ${
                      active ? 'text-fg font-medium' : 'text-fg-2'
                    }`}
                  >
                    <span>{VARIANT_LABELS[v]}</span>
                    {active && (
                      <span className="text-fg-muted text-[11px]">선택됨</span>
                    )}
                  </button>
                )
              })}
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
