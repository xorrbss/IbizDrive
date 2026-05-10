import { AdminGuard } from '@/components/auth/AdminGuard'

/**
 * `/admin/sharing` placeholder — 공유 정책 (외부 도메인 정책, 플래그된 공유,
 * 도메인 allow/block 등). 디자인 핸드오프 2026-05-10 admin.jsx §AdminSharing
 * (line 582~706) 1:1 매핑 + backend sharing policy endpoint 신규.
 * v1.x 후속 트랙.
 */
export default function AdminSharingPage() {
  return (
    <AdminGuard>
      <div className="empty-mini" style={{ padding: '64px 0' }}>
        <p style={{ fontSize: 14, color: 'var(--fg)', fontWeight: 500, marginBottom: 6 }}>
          공유 정책
        </p>
        <p>v1.x 예정 — 외부 도메인 정책, 플래그된 공유 검토 큐.</p>
      </div>
    </AdminGuard>
  )
}
