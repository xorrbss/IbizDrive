'use client'
import type { TeamMember } from '@/types/team'

export function TeamMemberTable({
  members, currentUserId, canManage, onChangeRole, onRemove,
}: {
  members: TeamMember[]
  currentUserId: string | null
  canManage: boolean
  onChangeRole: (member: TeamMember) => void
  onRemove: (member: TeamMember) => void
}) {
  return (
    <table className="w-full text-[13px]">
      <thead className="text-fg-muted text-[12px]">
        <tr>
          <th className="text-left py-2 px-3">이름</th>
          <th className="text-left py-2 px-3">역할</th>
          <th className="text-left py-2 px-3">가입일</th>
          {canManage && <th className="text-right py-2 px-3">액션</th>}
        </tr>
      </thead>
      <tbody>
        {members.map((m) => (
          <tr key={m.userId} className="border-t border-border hover:bg-surface-2">
            <td className="py-2 px-3">
              <div className="flex flex-col">
                <span className={m.userId === currentUserId ? 'font-semibold' : undefined}>
                  {m.displayName}
                  {m.userId === currentUserId && <span className="ml-1 text-fg-muted">(나)</span>}
                </span>
                <span className="text-[12px] text-fg-muted">{m.email}</span>
              </div>
            </td>
            <td className="py-2 px-3">{m.role}</td>
            <td className="py-2 px-3 text-fg-muted">
              {new Date(m.joinedAt).toLocaleDateString('ko-KR')}
            </td>
            {canManage && (
              <td className="py-2 px-3 text-right">
                <button
                  type="button"
                  onClick={() => onChangeRole(m)}
                  className="text-[12px] underline mr-2"
                >
                  역할 변경
                </button>
                <button
                  type="button"
                  onClick={() => onRemove(m)}
                  className="text-[12px] text-danger underline"
                >
                  제거
                </button>
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
