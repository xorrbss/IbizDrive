// src/components/files/FileTableForbidden.tsx
export function FileTableForbidden() {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 py-[60px] px-5 text-center">
      <div className="w-20 h-20 rounded-full bg-surface-2 flex items-center justify-center">
        <span aria-hidden className="text-[28px] text-fg-subtle">🔒</span>
      </div>
      <p className="text-[15px] font-semibold text-fg">접근 권한이 없습니다</p>
      <p className="text-[12.5px] text-fg-muted max-w-[320px]">
        이 폴더의 열람 권한이 필요합니다. 관리자에게 문의하세요.
      </p>
    </div>
  )
}
