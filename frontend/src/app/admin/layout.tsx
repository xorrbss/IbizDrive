import { AuthGuard } from '@/components/auth/AuthGuard'
import { AdminGuard } from '@/components/auth/AdminGuard'
import { AdminChrome } from '@/components/admin/AdminChrome'
import './admin.css'

/**
 * 관리자 영역 레이아웃 — 디자인 핸드오프 2026-05-10 admin.jsx §AdminConsole
 * (line 9~28) 의 헤더 + 탭바 + body 구조로 재구성 (T7-P1).
 *
 * <p>가드 중첩 (불변): `<AuthGuard>` (비로그인 → /login) 안쪽에 `<AdminGuard>`
 * (`allowedRoles=['ADMIN','AUDITOR']`). layout은 read-only 영역 진입을
 * 허용하고, ADMIN-only mutation 페이지는 페이지 단에서 default
 * `<AdminGuard>`로 다시 좁힌다 (wave1.5-auditor-admin-ui-access). 보안의
 * 진실은 백엔드 `@PreAuthorize`.
 *
 * <p>chrome 분리: `<AdminChrome>` 가 client component로 `usePathname()`
 * 기반 탭 derive를 담당. layout 자체는 server component 유지.
 *
 * <p>이전 `<AdminSideNav>` 좌측 네비게이션은 제거 — 디자인의 가로 탭바로
 * 대체됨. 기존 라우트(/admin/users, /admin/audit/logs, /admin/trash/*,
 * /admin/departments, /admin/system)는 그대로 유지하되 새 chrome 안에서
 * 렌더 (Phase 2 rename은 후속 트랙).
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <AdminGuard allowedRoles={['ADMIN', 'AUDITOR']}>
        <div className="admin">
          <AdminChrome />
          <main className="admin-body">{children}</main>
        </div>
      </AdminGuard>
    </AuthGuard>
  )
}
