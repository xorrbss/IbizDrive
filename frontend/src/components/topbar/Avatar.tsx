'use client'

/**
 * 사용자 아바타 (M14 docs/01 §18 #14).
 *
 * 백엔드 `/api/me` 미구현 — 현재는 placeholder ("U" initial circle).
 * 향후 `useMe()` 훅 신설 시 prop으로 initial / displayName 전달.
 *
 * 역할: img 의미가 아니라 단순 시각적 표식 → role 미지정 + aria-label로
 * 스크린리더에 사용자 이름만 안내.
 */
type Props = {
  /** 한 글자 이니셜. 미지정 시 'U'. */
  initial?: string
  /** aria-label용 사용자 표시 이름. */
  displayName?: string
}

export function Avatar({ initial = 'U', displayName = '사용자' }: Props) {
  return (
    <div
      aria-label={displayName}
      title={displayName}
      className="h-7 w-7 inline-flex items-center justify-center rounded-full bg-accent text-accent-fg text-[12px] font-semibold select-none"
    >
      {initial.slice(0, 1).toUpperCase()}
    </div>
  )
}
