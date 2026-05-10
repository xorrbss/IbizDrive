import { AdminGuard } from '@/components/auth/AdminGuard'

/**
 * `/admin/teams` placeholder — T8 (Admin Teams) 후속 트랙에서 풀 구현.
 * 디자인 핸드오프 2026-05-10 admin-teams.jsx §AdminTeams (line 38~240)
 * 1:1 매핑 + backend admin team endpoints 4종 신규.
 */
export default function AdminTeamsPage() {
  return (
    <AdminGuard>
      <div className="empty-mini" style={{ padding: '64px 0' }}>
        <p style={{ fontSize: 14, color: 'var(--fg)', fontWeight: 500, marginBottom: 6 }}>
          팀 관리
        </p>
        <p>T8 진행 중 — 곧 추가됩니다.</p>
      </div>
    </AdminGuard>
  )
}
