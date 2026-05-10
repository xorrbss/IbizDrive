'use client'
import { Lock } from 'lucide-react'
import { ADMIN_TAB_TITLES, type AdminTabId } from '@/lib/adminTabs'

/**
 * 관리자 콘솔 상단 헤더 — 디자인 핸드오프 2026-05-10 admin.jsx
 * §AdminTopHeader (line 30~63) 1:1 매핑.
 *
 * 좌측: 자물쇠 아이콘 + "관리자" + sep + 현재 탭 라벨, 그 아래 큰 타이틀.
 * 우측: tenant chip (회사명 + plan 라벨). 현재는 하드코딩 — 실제 tenant
 *      메타 endpoint 후속 트랙에서 endpoint 추가 시 props로 주입.
 *
 * tab=null이면 "관리자" 일반 라벨 (예: /admin/departments — 디자인 탭바
 * 미노출 라우트).
 */
export function AdminTopHeader({ tab }: { tab: AdminTabId | null }) {
  const title = tab ? ADMIN_TAB_TITLES[tab] : '관리자'

  return (
    <div className="admin-header">
      <div>
        <div className="admin-crumb">
          <Lock size={11} aria-hidden />
          <span>관리자</span>
          <span className="admin-crumb-sep">/</span>
          <span className="admin-crumb-current">{title}</span>
        </div>
        <h1 className="admin-title">{title}</h1>
      </div>
      <div className="admin-tenant" aria-label="조직 정보">
        <span className="admin-tenant-dot" aria-hidden />
        <div>
          <div className="admin-tenant-name">Ibiz Software Inc.</div>
          <div className="admin-tenant-sub">Workspace · Business Plus</div>
        </div>
      </div>
    </div>
  )
}
