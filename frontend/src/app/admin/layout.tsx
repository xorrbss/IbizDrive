import Link from 'next/link'

/**
 * 관리자 영역 레이아웃 (docs/04 §2 라우트 구조).
 * v1.0 mock: header만. 사이드 네비는 §2 전체 라우트 도입 시 (M_admin) 확장.
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col h-screen w-screen bg-bg text-fg overflow-hidden">
      <header
        aria-label="관리자 헤더"
        className="flex items-center gap-4 px-4 py-2 bg-surface-1 border-b border-border"
      >
        <Link href="/files" className="flex items-center gap-2 text-[14px] font-semibold text-fg">
          <span aria-hidden className="w-[18px] h-[18px] rounded-sm bg-accent inline-block" />
          IbizDrive 관리자
        </Link>
        <nav aria-label="관리자 네비게이션" className="flex items-center gap-1 text-[12.5px]">
          <Link
            href="/admin/audit/logs"
            className="px-2.5 py-1 rounded text-fg-2 hover:bg-surface-2 hover:text-fg"
          >
            감사 로그
          </Link>
        </nav>
      </header>
      <main className="flex-1 min-w-0 overflow-hidden">{children}</main>
    </div>
  )
}
