'use client'
import { useChangeTeamMemberRole } from '@/hooks/useChangeTeamMemberRole'
import { TEAM_OWNER_REQUIRED } from '@/lib/errors'
import type { TeamMember, TeamMemberRole } from '@/types/team'

export function ChangeRoleDialog({
  teamId, member, onClose,
}: { teamId: string; member: TeamMember; onClose: () => void }) {
  const targetRole: TeamMemberRole = member.role === 'OWNER' ? 'MEMBER' : 'OWNER'
  const change = useChangeTeamMemberRole(teamId)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await change.mutateAsync({ userId: member.userId, role: targetRole })
      onClose()
    } catch {
      // change.isError handles inline display
    }
  }

  const errCode = (change.error as Error & { code?: string } | undefined)?.code
  const errMsg = errCode === TEAM_OWNER_REQUIRED
    ? '팀에 최소 한 명의 OWNER가 필요합니다'
    : change.isError ? `변경 실패: ${String(change.error)}` : null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="역할 변경"
      onKeyDown={(e) => { if (e.key === 'Escape') onClose() }}
      className="fixed inset-0 flex items-center justify-center bg-black/40 z-50"
    >
      <form
        onSubmit={submit}
        className="bg-surface-1 border border-border rounded p-4 w-[420px] flex flex-col gap-3"
      >
        <h2 className="text-[14px] font-semibold">역할 변경</h2>
        <p className="text-[13px]">
          <strong>{member.displayName}</strong> 의 역할을{' '}
          <strong>{member.role}</strong> → <strong>{targetRole}</strong> 로 변경합니다.
        </p>
        {errMsg && (
          <p role="alert" className="text-[12px] text-danger">
            {errMsg}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-3 py-1 text-[12px]">
            취소
          </button>
          <button
            type="submit"
            disabled={change.isPending}
            className="px-3 py-1 bg-accent text-white text-[12px] rounded disabled:opacity-50"
          >
            {change.isPending ? '변경 중...' : '변경'}
          </button>
        </div>
      </form>
    </div>
  )
}
