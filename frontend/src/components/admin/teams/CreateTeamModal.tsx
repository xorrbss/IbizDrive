'use client'
import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { useMe } from '@/hooks/useMe'
import { useAdminCreateTeamWithMetadata } from '@/hooks/useAdminTeams'
import { PAvatar } from './Avatars'
import { MemberPickerModal } from './MemberPickerModal'

/**
 * 팀 등록 모달 — admin-teams.jsx CreateTeamModal (line 247~337) 1:1 매핑.
 *
 * <p>입력: name(필수), description, color(8색 swatch), members(creator 자동 포함),
 * leadId(members 중에서 선택).
 *
 * <p>제출 시 {@link useAdminCreateTeamWithMetadata}로 다단계 합성 mutation —
 * POST /api/teams → invite members → PATCH metadata. creator는 자동 OWNER로
 * 가입하므로 leadId === creator일 때 즉시 PATCH 가능 (디자인 default).
 *
 * <p>에러: 409 TEAM_CONFLICT (같은 name 활성 팀 존재) → 인라인 메시지.
 */
export const TEAM_COLORS = [
  '#5B7FCC',
  '#C16A8B',
  '#5BA08A',
  '#C9925A',
  '#7C6BB5',
  '#A56FB8',
  '#5C9B9B',
  '#B9824D',
] as const

export interface CreateTeamModalProps {
  onClose: () => void
  onCreated?: (teamId: string) => void
}

interface PickedMember {
  userId: string
  name: string
}

export function CreateTeamModal({ onClose, onCreated }: CreateTeamModalProps) {
  const { data: me } = useMe()
  const myUserId = me?.user.id ?? null
  const myName = me?.user.name ?? '나'

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [color, setColor] = useState<string>(TEAM_COLORS[0])
  const [members, setMembers] = useState<PickedMember[]>(
    myUserId ? [{ userId: myUserId, name: myName }] : [],
  )
  const [leadId, setLeadId] = useState<string>(myUserId ?? '')
  const [pickerOpen, setPickerOpen] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const create = useAdminCreateTeamWithMetadata()

  // me() 비동기 로딩 — myUserId 채워지면 members/leadId 1회 동기화.
  useEffect(() => {
    if (myUserId && members.length === 0) {
      setMembers([{ userId: myUserId, name: myName }])
      setLeadId(myUserId)
    }
    // myName은 myUserId 도달 시 같이 정해지므로 dep에서 제외해도 된다.
    // members.length 0 → 1 1회만 트리거.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [myUserId])

  const canSubmit =
    name.trim().length > 0 &&
    members.length > 0 &&
    !!leadId &&
    members.some((m) => m.userId === leadId) &&
    !create.isPending

  const removeMember = (userId: string) => {
    if (userId === leadId) return // lead는 제거 금지
    setMembers((prev) => prev.filter((m) => m.userId !== userId))
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    setErrorMsg(null)
    try {
      const additionalMemberIds = members
        .filter((m) => m.userId !== myUserId)
        .map((m) => m.userId)
      const result = await create.mutateAsync({
        name: name.trim(),
        description: description.trim() || undefined,
        color,
        additionalMemberIds,
        leadId: leadId !== myUserId ? leadId : undefined,
        leadIsCreator: leadId === myUserId,
      })
      onCreated?.(result.id)
      onClose()
    } catch (e) {
      setErrorMsg(createErrorMessage(e))
    }
  }

  return (
    <div
      className="modal-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-team-title"
    >
      <div className="modal modal-md" onClick={(e) => e.stopPropagation()}>
        <header className="modal-head">
          <h2 id="create-team-title">팀 등록</h2>
          <button
            type="button"
            className="icon-btn-ghost"
            onClick={onClose}
            aria-label="닫기"
          >
            <X size={13} aria-hidden />
          </button>
        </header>
        <form onSubmit={onSubmit}>
          <div className="modal-body">
            <div className="form-row">
              <label htmlFor="team-name">팀 이름</label>
              <input
                id="team-name"
                className="form-input"
                placeholder="예: 디자인 시스템"
                value={name}
                maxLength={100}
                required
                onChange={(e) => setName(e.target.value)}
                autoFocus
              />
            </div>
            <div className="form-row">
              <label htmlFor="team-desc">설명</label>
              <input
                id="team-desc"
                className="form-input"
                placeholder="팀의 역할을 한 줄로 설명"
                value={description}
                maxLength={1000}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            <div className="form-row">
              <label>색상</label>
              <div
                className="color-swatches"
                role="radiogroup"
                aria-label="팀 색상 선택"
              >
                {TEAM_COLORS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    className={`color-swatch ${color === c ? 'active' : ''}`}
                    style={{ background: c }}
                    onClick={() => setColor(c)}
                    aria-label={c}
                    role="radio"
                    aria-checked={color === c}
                  />
                ))}
              </div>
            </div>
            <div className="form-row">
              <div className="form-row-head">
                <label>멤버 ({members.length})</label>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => setPickerOpen(true)}
                >
                  + 멤버 추가
                </button>
              </div>
              <div className="picked-members">
                {members.map((m) => (
                  <span key={m.userId} className="picked-chip">
                    <PAvatar userId={m.userId} name={m.name} size={20} />
                    <span>{m.name}</span>
                    {m.userId !== leadId && (
                      <button
                        type="button"
                        onClick={() => removeMember(m.userId)}
                        aria-label={`${m.name} 제거`}
                        title="제거"
                      >
                        <X size={9} aria-hidden />
                      </button>
                    )}
                  </span>
                ))}
              </div>
            </div>
            <div className="form-row">
              <label htmlFor="team-lead">팀 리더</label>
              <select
                id="team-lead"
                className="form-input"
                value={leadId}
                onChange={(e) => setLeadId(e.target.value)}
              >
                {members.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.name}
                  </option>
                ))}
              </select>
            </div>
            {errorMsg && (
              <p role="alert" className="text-sm text-red-600">
                {errorMsg}
              </p>
            )}
          </div>
          <footer className="modal-foot">
            <div />
            <div className="modal-foot-actions">
              <button type="button" className="btn-ghost" onClick={onClose}>
                취소
              </button>
              <button type="submit" className="btn-primary" disabled={!canSubmit}>
                {create.isPending ? '등록 중…' : '팀 등록'}
              </button>
            </div>
          </footer>
        </form>
        {pickerOpen && (
          <MemberPickerModal
            excludeIds={members.map((m) => m.userId)}
            onClose={() => setPickerOpen(false)}
            onAdd={(users) => {
              setMembers((prev) => {
                const seen = new Set(prev.map((m) => m.userId))
                return [
                  ...prev,
                  ...users
                    .filter((u) => !seen.has(u.id))
                    .map((u) => ({ userId: u.id, name: u.displayName })),
                ]
              })
              setPickerOpen(false)
            }}
          />
        )}
      </div>
    </div>
  )
}

function createErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string }
  if (err.status === 409 && err.code === 'TEAM_CONFLICT') {
    return '같은 이름의 활성 팀이 이미 존재합니다.'
  }
  if (err.status === 400) return '입력값을 확인해주세요.'
  if (err.status === 403) return '권한이 없습니다.'
  return '팀 등록에 실패했습니다.'
}
