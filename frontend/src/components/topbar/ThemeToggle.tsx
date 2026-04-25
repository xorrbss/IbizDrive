'use client'
import { Moon, Sun } from 'lucide-react'
import { useTheme } from '@/hooks/useTheme'

/**
 * 테마 토글 버튼 — Sun(라이트) / Moon(다크) 아이콘.
 *
 * - 클릭 / Enter / Space 로 동작 (button 기본 동작 사용).
 * - aria-pressed 로 현재 다크 모드 여부 노출.
 * - aria-label 은 "다음에 적용될 모드"가 아닌 "현재 상태 + 클릭 시 동작"을 함께 안내.
 */
export function ThemeToggle() {
  const { theme, toggle } = useTheme()
  const isDark = theme === 'dark'
  const label = isDark ? '라이트 모드로 전환' : '다크 모드로 전환'

  return (
    <button
      type="button"
      aria-pressed={isDark}
      aria-label={label}
      title={label}
      onClick={toggle}
      className="h-8 w-8 inline-flex items-center justify-center rounded-md text-fg-2 hover:bg-surface-2 hover:text-fg transition-colors focus-visible:outline-2 focus-visible:outline-accent"
    >
      {isDark ? (
        <Moon aria-hidden size={16} />
      ) : (
        <Sun aria-hidden size={16} />
      )}
    </button>
  )
}
