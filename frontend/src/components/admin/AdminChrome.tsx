'use client'
import { usePathname } from 'next/navigation'
import { deriveAdminTab } from '@/lib/adminTabs'
import { AdminTopHeader } from './AdminTopHeader'
import { AdminTabBar } from './AdminTabBar'

/**
 * 관리자 콘솔 chrome (헤더 + 탭바) — client wrapper.
 *
 * <p>layout.tsx는 server component로 두고, pathname 기반 탭 derive는
 * 이 client wrapper에 위임. 디자인 핸드오프 admin.jsx §AdminConsole
 * (line 9~28) 의 헤더+탭바 구조 1:1.
 */
export function AdminChrome() {
  const pathname = usePathname()
  const tab = deriveAdminTab(pathname)

  return (
    <>
      <AdminTopHeader tab={tab} />
      <AdminTabBar />
    </>
  )
}
