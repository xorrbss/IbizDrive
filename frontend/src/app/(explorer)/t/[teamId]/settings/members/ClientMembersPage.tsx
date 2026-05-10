'use client'
import { useState } from 'react'
import { useTeamMembers } from '@/hooks/useTeamMembers'
import { useMe } from '@/hooks/useMe'
import { TeamMemberTable } from '@/components/team/TeamMemberTable'
import { InviteMemberDialog } from '@/components/team/InviteMemberDialog'
import { ChangeRoleDialog } from '@/components/team/ChangeRoleDialog'
import { RemoveMemberDialog } from '@/components/team/RemoveMemberDialog'
import type { TeamMember } from '@/types/team'

export function ClientMembersPage({ teamId }: { teamId: string }) {
  const me = useMe()
  const members = useTeamMembers(teamId)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [changeTarget, setChangeTarget] = useState<TeamMember | null>(null)
  const [removeTarget, setRemoveTarget] = useState<TeamMember | null>(null)

  const myUserId = me.data?.user.id ?? null
  const myMember = members.data?.find((m) => m.userId === myUserId)
  const canManage = myMember?.role === 'OWNER'

  if (members.isLoading) {
    return <div className="p-6 text-fg-muted">멤버 로딩 중…</div>
  }
  const errCode = (members.error as (Error & { code?: string }) | undefined)?.code
  if (errCode === 'PERMISSION_DENIED') {
    return <div className="p-6 text-danger">접근 권한이 없습니다.</div>
  }
  if (members.isError) {
    return <div className="p-6 text-danger">멤버 로드 실패: {String(members.error)}</div>
  }

  return (
    <div className="p-6 flex flex-col gap-4">
      <header className="flex items-center justify-between">
        <h1 className="text-[18px] font-semibold">팀 멤버</h1>
        {canManage && (
          <button
            type="button"
            onClick={() => setInviteOpen(true)}
            className="px-3 py-1.5 bg-accent text-white text-[13px] rounded"
          >
            멤버 초대
          </button>
        )}
      </header>
      <TeamMemberTable
        members={members.data ?? []}
        currentUserId={myUserId}
        canManage={canManage}
        onChangeRole={setChangeTarget}
        onRemove={setRemoveTarget}
      />
      {inviteOpen && (
        <InviteMemberDialog teamId={teamId} onClose={() => setInviteOpen(false)} />
      )}
      {changeTarget && (
        <ChangeRoleDialog
          teamId={teamId}
          member={changeTarget}
          onClose={() => setChangeTarget(null)}
        />
      )}
      {removeTarget && (
        <RemoveMemberDialog
          teamId={teamId}
          member={removeTarget}
          onClose={() => setRemoveTarget(null)}
        />
      )}
    </div>
  )
}
