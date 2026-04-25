import { SearchX } from 'lucide-react'

export function SearchEmpty({ q }: { q: string }) {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 py-[60px] px-5 text-center">
      <div className="w-20 h-20 rounded-full bg-surface-2 flex items-center justify-center text-fg-subtle">
        <SearchX size={36} strokeWidth={1.4} aria-hidden />
      </div>
      <p className="text-[15px] font-semibold text-fg">검색 결과가 없습니다</p>
      <p className="text-[12.5px] text-fg-muted max-w-[320px]">
        <span className="font-medium text-fg">‘{q}’</span>에 일치하는 파일이나 폴더를 찾을 수 없습니다
      </p>
    </div>
  )
}
