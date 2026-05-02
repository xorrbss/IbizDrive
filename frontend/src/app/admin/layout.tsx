import Link from 'next/link'
import { AuthGuard } from '@/components/auth/AuthGuard'
import { AdminGuard } from '@/components/auth/AdminGuard'
import { AdminSideNav } from '@/components/admin/AdminSideNav'

/**
 * 관리자 영역 레이아웃 (docs/04 §2 라우트 구조 — m-admin-entry).
 *
 * <p>가드 중첩: {@code AuthGuard}가 401(미인증)을 처리, 그 안의 {@code AdminGuard}가
 * role=ADMIN을 검사. 본 가드 조합은 **UX**만 책임지며, 보안 가드(백엔드
 * {@code @PreAuthorize})는 별도 트랙(docs/04 §1 참조).
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <AdminGuard>
        <div className="flex h-screen w-screen bg-bg text-fg overflow-hidden">
          <aside
            aria-label="관리자 사이드바"
            className="w-[248px] shrink-0 bg-surface-1 border-r border-border flex flex-col overflow-y-auto"
          >
            <Link
              href="/admin"
              className="flex items-center gap-2 px-4 py-3 border-b border-border text-[14px] font-semibold text-fg"
            >
              <span aria-hidden className="w-[18px] h-[18px] rounded-sm bg-accent inline-block" />
              IbizDrive 관리자
            </Link>
            <AdminSideNav />
            <div className="mt-auto p-2 border-t border-border">
              <Link
                href="/files"
                className="flex items-center justify-center px-2.5 py-1.5 rounded text-[12px] text-fg-2 hover:bg-surface-2 hover:text-fg"
              >
                ← 탐색기로
              </Link>
            </div>
          </aside>
          <main className="flex-1 min-w-0 overflow-hidden">{children}</main>
        </div>
      </AdminGuard>
    </AuthGuard>
  )
}
