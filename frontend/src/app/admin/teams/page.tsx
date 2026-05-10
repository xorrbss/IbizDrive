'use client'
import { useEffect, useMemo, useState } from 'react'
import { AdminGuard } from '@/components/auth/AdminGuard'
import { useMe } from '@/hooks/useMe'
import {
  useAdminArchiveTeam,
  useAdminInviteTeamMember,
  useAdminRemoveTeamMember,
  useAdminRestoreTeam,
  useAdminTeam,
  useAdminTeams,
  useAdminUpdateTeam,
} from '@/hooks/useAdminTeams'
import { useTeamMembers } from '@/hooks/useTeamMembers'
import { TeamsListPanel } from '@/components/admin/teams/TeamsListPanel'
import { TeamDetail } from '@/components/admin/teams/TeamDetail'
import { CreateTeamModal } from '@/components/admin/teams/CreateTeamModal'
import { EditTeamModal } from '@/components/admin/teams/EditTeamModal'
import { MemberPickerModal } from '@/components/admin/teams/MemberPickerModal'

/**
 * `/admin/teams` — 디자인 핸드오프 2026-05-10 admin-teams.jsx §AdminTeams (line 38~240)
 * 1:1 매핑 + backend admin team endpoints 5종.
 *
 * <p>구성: 좌측 list panel(검색/등록) + 우측 team detail(편집/삭제/멤버 관리). 모달:
 * Create/Edit/MemberPicker. AUDITOR는 read-only — 모든 mutation 버튼 숨김
 * (defense-in-depth; AdminTabBar가 이미 teams 탭을 ADMIN 전용으로 가시화).
 *
 * <p>가드: ADMIN만 — default `<AdminGuard>` (wave1.5-auditor-admin-ui-access).
 * AUDITOR가 직접 URL 진입해도 admin layout 진입 후 본 가드가 redirect.
 */
export default function AdminTeamsPage() {
  return (
    <AdminGuard>
      <AdminTeamsBody />
    </AdminGuard>
  )
}

function AdminTeamsBody() {
  const { data: me } = useMe()
  const isAuditor =
    !!me && me.roles.includes('AUDITOR') && !me.roles.includes('ADMIN')
  const canMutate = !isAuditor

  const teamsQ = useAdminTeams()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')

  // 첫 로드 후 자동으로 첫 팀 선택. 사용자가 명시적으로 선택을 바꾸면 그대로 유지.
  useEffect(() => {
    if (selectedId) return
    const first = teamsQ.data?.[0]
    if (first) setSelectedId(first.id)
  }, [teamsQ.data, selectedId])

  const detailQ = useAdminTeam(selectedId)
  const membersQ = useTeamMembers(selectedId)

  // Modal state
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [pickerOpen, setPickerOpen] = useState(false)

  // Mutations bound to selectedId
  const archive = useAdminArchiveTeam()
  const restore = useAdminRestoreTeam()
  const update = useAdminUpdateTeam()
  const invite = useAdminInviteTeamMember(selectedId ?? '')
  const remove = useAdminRemoveTeamMember(selectedId ?? '')

  const teams = useMemo(() => teamsQ.data ?? [], [teamsQ.data])

  const onArchive = async () => {
    if (!selectedId || !canMutate) return
    if (!window.confirm('이 팀을 보관함으로 이동할까요? 활성 멤버십은 유지됩니다.')) {
      return
    }
    try {
      await archive.mutateAsync({ id: selectedId })
    } catch (e) {
      const err = e as { status?: number; code?: string }
      window.alert(`삭제 실패: ${err.code ?? err.status ?? 'unknown'}`)
    }
  }

  const onRestore = async () => {
    if (!selectedId || !canMutate) return
    try {
      await restore.mutateAsync({ id: selectedId })
    } catch (e) {
      const err = e as { status?: number; code?: string }
      window.alert(`복원 실패: ${err.code ?? err.status ?? 'unknown'}`)
    }
  }

  const onSetLead = async (userId: string) => {
    if (!selectedId || !canMutate) return
    try {
      await update.mutateAsync({ id: selectedId, body: { leadId: userId } })
    } catch (e) {
      const err = e as { status?: number; code?: string }
      window.alert(`리더 지정 실패: ${err.code ?? err.status ?? 'unknown'}`)
    }
  }

  const onRemoveMember = async (userId: string) => {
    if (!selectedId || !canMutate) return
    if (!window.confirm('이 멤버를 팀에서 제거할까요?')) return
    try {
      await remove.mutateAsync({ userId })
    } catch (e) {
      const err = e as { status?: number; code?: string }
      if (err.code === 'TEAM_OWNER_REQUIRED') {
        window.alert('마지막 OWNER는 제거할 수 없습니다.')
        return
      }
      window.alert(`제거 실패: ${err.code ?? err.status ?? 'unknown'}`)
    }
  }

  const onAddMembers = async (
    users: { id: string; displayName: string; email: string }[],
  ) => {
    if (!selectedId || !canMutate) return
    setPickerOpen(false)
    for (const u of users) {
      try {
        await invite.mutateAsync({ userId: u.id })
      } catch (e) {
        const err = e as { status?: number; code?: string }
        window.alert(
          `${u.displayName} 추가 실패: ${err.code ?? err.status ?? 'unknown'}`,
        )
      }
    }
  }

  if (teamsQ.isLoading) {
    return (
      <div className="flex-1 overflow-auto p-6">
        <p className="text-sm text-fg-2">불러오는 중…</p>
      </div>
    )
  }

  if (teamsQ.isError) {
    return (
      <div className="flex-1 overflow-auto p-6">
        <p role="alert" className="text-sm text-red-600">
          팀 목록을 불러오지 못했습니다.
        </p>
      </div>
    )
  }

  if (teams.length === 0) {
    return (
      <div className="flex-1 overflow-auto p-6">
        <div className="empty-mini" style={{ padding: '64px 0' }}>
          <p style={{ fontSize: 14, color: 'var(--fg)', fontWeight: 500, marginBottom: 6 }}>
            아직 등록된 팀이 없습니다
          </p>
          {canMutate && (
            <button
              type="button"
              className="btn-primary btn-sm"
              onClick={() => setCreateOpen(true)}
              style={{ marginTop: 12 }}
            >
              + 팀 등록
            </button>
          )}
        </div>
        {createOpen && (
          <CreateTeamModal
            onClose={() => setCreateOpen(false)}
            onCreated={(id) => setSelectedId(id)}
          />
        )}
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-auto p-6">
      <div className="teams-screen" data-screen-label="admin/teams">
        <TeamsListPanel
          teams={teams}
          selectedId={selectedId}
          onSelect={setSelectedId}
          query={searchQuery}
          onQueryChange={setSearchQuery}
          onCreate={() => setCreateOpen(true)}
          canCreate={canMutate}
        />

        {detailQ.isLoading && (
          <section className="team-detail">
            <p className="text-sm text-fg-2">팀 상세 불러오는 중…</p>
          </section>
        )}
        {detailQ.isError && (
          <section className="team-detail">
            <p role="alert" className="text-sm text-red-600">
              팀 상세를 불러오지 못했습니다.
            </p>
          </section>
        )}
        {detailQ.data && (
          <TeamDetail
            team={detailQ.data}
            members={membersQ.data ?? []}
            canMutate={canMutate}
            onEdit={() => setEditOpen(true)}
            onArchive={onArchive}
            onRestore={onRestore}
            onAddMember={() => setPickerOpen(true)}
            onSetLead={onSetLead}
            onRemoveMember={onRemoveMember}
          />
        )}
      </div>

      {createOpen && (
        <CreateTeamModal
          onClose={() => setCreateOpen(false)}
          onCreated={(id) => setSelectedId(id)}
        />
      )}
      {editOpen && detailQ.data && (
        <EditTeamModal
          team={detailQ.data}
          onClose={() => setEditOpen(false)}
        />
      )}
      {pickerOpen && selectedId && (
        <MemberPickerModal
          excludeIds={(membersQ.data ?? []).map((m) => m.userId)}
          onClose={() => setPickerOpen(false)}
          onAdd={onAddMembers}
        />
      )}
    </div>
  )
}
