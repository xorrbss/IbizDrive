export default function NotFound() {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 py-[60px] px-5 text-center">
      <div className="w-20 h-20 rounded-full bg-surface-2 flex items-center justify-center">
        <span aria-hidden className="text-[28px] text-fg-subtle">?</span>
      </div>
      <h2 className="text-[15px] font-semibold text-fg">폴더를 찾을 수 없습니다</h2>
      <p className="text-[12.5px] text-fg-muted max-w-[320px]">
        경로가 변경되었거나 삭제되었을 수 있습니다.
      </p>
    </div>
  )
}
