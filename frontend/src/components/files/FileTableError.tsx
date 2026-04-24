// src/components/files/FileTableError.tsx
type Props = {
  onRetry: () => void
}

export function FileTableError({ onRetry }: Props) {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 py-[60px] px-5 text-center">
      <div className="w-20 h-20 rounded-full bg-[color-mix(in_oklch,var(--danger)_10%,transparent)] flex items-center justify-center">
        <span aria-hidden className="text-[28px] text-danger">!</span>
      </div>
      <p className="text-[15px] font-semibold text-fg">파일 목록을 불러올 수 없습니다</p>
      <p className="text-[12.5px] text-fg-muted max-w-[320px]">
        네트워크를 확인하고 다시 시도해주세요
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-1 h-8 px-3.5 inline-flex items-center rounded text-[12.5px] font-medium bg-accent text-white hover:bg-accent-hover transition-colors"
      >
        다시 시도
      </button>
    </div>
  )
}
