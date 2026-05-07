import Link from 'next/link'
import { AuthGuard } from '@/components/auth/AuthGuard'
import { AdminGuard } from '@/components/auth/AdminGuard'
import { AdminSideNav } from '@/components/admin/AdminSideNav'

/**
 * 관리자 영역 레이아웃 (m-admin-entry-rewrite, docs/04 §1 §2;
 * wave1.5-auditor-admin-ui-access — AUDITOR 진입 허용).
 *
 * <p>가드 중첩: `<AuthGuard>` (비로그인 → /login) 안쪽에 `<AdminGuard>`
 * (`allowedRoles=['ADMIN','AUDITOR']`). layout은 read-only 영역 진입을 허용
 * 하고, ADMIN-only mutation 페이지는 페이지 단에서 default `<AdminGuard>`로
 * 다시 감싸 좁힌다. 두 가드의 책임은 분리되어야 한다 — AuthGuard는 인증
 * 보유 여부를, AdminGuard는 role을 검사. 보안의 진실은 백엔드 `@PreAuthorize`.
 *
 * <p>레이아웃은 header / (사이드 nav | main) 2열. header는 진입 anchor만
 * 유지하고 nav는 모두 사이드로 이동(단일 진실).
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <AdminGuard allowedRoles={['ADMIN', 'AUDITOR']}>
        <div className="flex flex-col h-screen w-screen bg-bg text-fg overflow-hidden">
          <header
            aria-label="관리자 헤더"
            className="flex items-center gap-4 px-4 py-2 bg-surface-1 border-b border-border"
          >
            <Link
              href="/files"
              className="flex items-center gap-2 text-[14px] font-semibold text-fg"
            >
              <span aria-hidden className="w-[18px] h-[18px] rounded-sm bg-accent inline-block" />
              IbizDrive 관리자
            </Link>
          </header>
          <div className="flex-1 min-h-0 flex">
            <AdminSideNav />
            <main className="flex-1 min-w-0 overflow-auto">{children}</main>
          </div>
        </div>
      </AdminGuard>
    </AuthGuard>
  )
}
