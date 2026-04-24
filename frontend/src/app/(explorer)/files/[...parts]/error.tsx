'use client'
export default function Error({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div
      role="alert"
      className="flex-1 flex flex-col items-center justify-center gap-3 py-[60px] px-5 text-center"
    >
      <div className="w-20 h-20 rounded-full bg-[color-mix(in_oklch,var(--danger)_10%,transparent)] flex items-center justify-center">
        <span aria-hidden className="text-[28px] text-danger">!</span>
      </div>
      <h2 className="text-[15px] font-semibold text-fg">문제가 발생했습니다</h2>
      <p className="text-[12.5px] text-fg-muted max-w-[360px] break-words">{error.message}</p>
      <button
        type="button"
        onClick={reset}
        className="mt-1 h-8 px-3.5 inline-flex items-center rounded text-[12.5px] font-medium bg-accent text-white hover:bg-accent-hover transition-colors"
      >
        다시 시도
      </button>
    </div>
  )
}
