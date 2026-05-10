'use client'
import { Edit2, Trash2, UserPlus, RotateCcw, X } from 'lucide-react'
import type { AdminTeamDetail } from '@/lib/api'
import type { TeamMember } from '@/types/team'
import { PAvatar, RolePill } from './Avatars'

/**
 * 우측 팀 상세 — admin-teams.jsx TeamDetail (line 107~222) 1:1 매핑.
 *
 * <p>구성: 헤더(swatch + name + desc + 편집/삭제) → StatRow(4) → 멤버 섹션 → 담당 폴더 섹션.
 * 담당 폴더 섹션은 v1.x placeholder (folder linkage 미스코프).
 *
 * <p>read-only: AUDITOR면 모든 mutation 버튼/액션 숨김.
 */
export interface TeamDetailProps {
  team: AdminTeamDetail
  members: TeamMember[]
  /** AUDITOR면 false — 모든 mutation 버튼 숨김. */
  canMutate?: boolean
  onEdit?: () => void
  onArchive?: () => void
  onRestore?: () => void
  onAddMember?: () => void
  onSetLead?: (userId: string) => void
  onRemoveMember?: (userId: string) => void
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

export function TeamDetail({
  team,
  members,
  canMutate = true,
  onEdit,
  onArchive,
  onRestore,
  onAddMember,
  onSetLead,
  onRemoveMember,
}: TeamDetailProps) {
  const lead = members.find((m) => m.userId === team.leadId)

  return (
    <section className="team-detail" aria-label={`${team.name} 상세`}>
      <header className="team-detail-head">
        <div className="team-detail-title-row">
          <span
            className="team-detail-swatch"
            style={{ background: team.color }}
            aria-hidden
          />
          <div>
            <h2 className="team-detail-name">{team.name}</h2>
            {team.description && (
              <div className="team-detail-desc">{team.description}</div>
            )}
          </div>
        </div>
        {canMutate && (
          <div className="team-detail-actions">
            {onEdit && !team.archived && (
              <button type="button" className="btn-ghost btn-sm" onClick={onEdit}>
                <Edit2 size={12} aria-hidden />
                <span>편집</span>
              </button>
            )}
            {team.archived
              ? onRestore && (
                  <button
                    type="button"
                    className="btn-ghost btn-sm"
                    onClick={onRestore}
                  >
                    <RotateCcw size={12} aria-hidden />
                    <span>복원</span>
                  </button>
                )
              : onArchive && (
                  <button
                    type="button"
                    className="btn-ghost btn-sm btn-danger"
                    onClick={onArchive}
                  >
                    <Trash2 size={12} aria-hidden />
                    <span>삭제</span>
                  </button>
                )}
          </div>
        )}
      </header>

      <div className="team-stat-row">
        <div className="team-stat">
          <div className="team-stat-label">멤버</div>
          <div className="team-stat-value">
            {team.memberCount}
            <span className="team-stat-unit">명</span>
          </div>
        </div>
        <div className="team-stat">
          <div className="team-stat-label">담당 폴더</div>
          <div className="team-stat-value">0</div>
        </div>
        <div className="team-stat">
          <div className="team-stat-label">팀 리더</div>
          <div className="team-stat-value-user">
            <PAvatar userId={team.leadId} name={lead?.displayName} size={22} />
            <span>{lead?.displayName ?? '—'}</span>
          </div>
        </div>
        <div className="team-stat">
          <div className="team-stat-label">생성일</div>
          <div className="team-stat-value-sm">{formatDate(team.createdAt)}</div>
        </div>
      </div>

      <div className="team-section">
        <div className="team-section-head">
          <h3>멤버 ({members.length})</h3>
          {canMutate && onAddMember && !team.archived && (
            <button type="button" className="btn-ghost btn-sm" onClick={onAddMember}>
              <UserPlus size={12} aria-hidden />
              <span>멤버 추가</span>
            </button>
          )}
        </div>
        <table className="team-member-table">
          <thead>
            <tr>
              <th>이름</th>
              <th>이메일</th>
              <th>역할</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {members.map((m) => {
              const isLead = m.userId === team.leadId
              const isOwner = m.role === 'OWNER'
              return (
                <tr key={m.userId}>
                  <td>
                    <div className="m-name-cell">
                      <PAvatar userId={m.userId} name={m.displayName} size={28} />
                      <div>
                        <div className="m-name">{m.displayName}</div>
                        {isLead && <div className="m-sub">팀 리더</div>}
                      </div>
                    </div>
                  </td>
                  <td className="m-meta">{m.email}</td>
                  <td>
                    {isLead ? (
                      <RolePill role="manager" label="리더" />
                    ) : isOwner ? (
                      <RolePill role="owner" label="소유자" />
                    ) : (
                      <RolePill role="editor" label="멤버" />
                    )}
                  </td>
                  <td className="m-actions">
                    {canMutate && !isLead && !team.archived && (
                      <>
                        {onSetLead && (
                          <button
                            type="button"
                            className="link-btn"
                            onClick={() => onSetLead(m.userId)}
                          >
                            리더로 지정
                          </button>
                        )}
                        {onRemoveMember && (
                          <button
                            type="button"
                            className="icon-btn-ghost"
                            onClick={() => onRemoveMember(m.userId)}
                            title="제거"
                            aria-label={`${m.displayName} 제거`}
                          >
                            <X size={11} aria-hidden />
                          </button>
                        )}
                      </>
                    )}
                  </td>
                </tr>
              )
            })}
            {members.length === 0 && (
              <tr>
                <td colSpan={4}>
                  <div className="team-empty">멤버가 없습니다.</div>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="team-section">
        <div className="team-section-head">
          <h3>담당 폴더</h3>
        </div>
        <div className="team-empty">
          폴더 연결 기능은 곧 추가됩니다.
        </div>
      </div>
    </section>
  )
}
