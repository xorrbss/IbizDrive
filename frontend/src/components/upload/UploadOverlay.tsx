'use client'

type Props = { visible: boolean }

export function UploadOverlay({ visible }: Props) {
  if (!visible) return null
  return (
    <div
      role="presentation"
      className="absolute inset-0 z-10 flex items-center justify-center pointer-events-none bg-[color-mix(in_oklch,var(--accent)_10%,transparent)] border-2 border-dashed border-accent rounded"
      aria-hidden
    >
      <div className="text-[14px] font-medium text-accent">여기에 놓아 업로드</div>
    </div>
  )
}
