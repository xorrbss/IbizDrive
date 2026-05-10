'use client'
import { useState } from 'react'
import { X } from 'lucide-react'
import { useAdminUpdateTeam } from '@/hooks/useAdminTeams'
import type { AdminTeamDetail } from '@/lib/api'
import { TEAM_COLORS } from './CreateTeamModal'

/**
 * 팀 편집 모달 — admin-teams.jsx 디자인의 "편집" 버튼 후속 UX.
 *
 * <p>name/description/color 변경. leadId는 멤버 테이블의 "리더로 지정"으로 별도 처리
 * (디자인 분리). 색상 swatch는 CreateTeamModal과 동일 8색.
 *
 * <p>변경된 필드만 PATCH body에 포함 — backend는 제공된 필드만 적용.
 */
export interface EditTeamModalProps {
  team: AdminTeamDetail
  onClose: () => void
  onSaved?: () => void
}

export function EditTeamModal({ team, onClose, onSaved }: EditTeamModalProps) {
  const [name, setName] = useState(team.name)
  const [description, setDescription] = useState(team.description ?? '')
  const [color, setColor] = useState(team.color)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const update = useAdminUpdateTeam()

  const dirty =
    name.trim() !== team.name ||
    (description.trim() || null) !== (team.description ?? null) ||
    color !== team.color
  const canSubmit = name.trim().length > 0 && dirty && !update.isPending

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    setErrorMsg(null)
    const body: { name?: string; description?: string; color?: string } = {}
    if (name.trim() !== team.name) body.name = name.trim()
    if ((description.trim() || null) !== (team.description ?? null)) {
      body.description = description.trim()
    }
    if (color !== team.color) body.color = color
    try {
      await update.mutateAsync({ id: team.id, body })
      onSaved?.()
      onClose()
    } catch (e) {
      setErrorMsg(updateErrorMessage(e))
    }
  }

  return (
    <div
      className="modal-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="edit-team-title"
    >
      <div className="modal modal-md" onClick={(e) => e.stopPropagation()}>
        <header className="modal-head">
          <h2 id="edit-team-title">팀 편집</h2>
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
              <label htmlFor="edit-team-name">팀 이름</label>
              <input
                id="edit-team-name"
                className="form-input"
                value={name}
                maxLength={100}
                required
                onChange={(e) => setName(e.target.value)}
                autoFocus
              />
            </div>
            <div className="form-row">
              <label htmlFor="edit-team-desc">설명</label>
              <input
                id="edit-team-desc"
                className="form-input"
                value={description}
                maxLength={1000}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            <div className="form-row">
              <label>색상</label>
              <div className="color-swatches" role="radiogroup" aria-label="팀 색상">
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
                {update.isPending ? '저장 중…' : '저장'}
              </button>
            </div>
          </footer>
        </form>
      </div>
    </div>
  )
}

function updateErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string }
  if (err.status === 409 && err.code === 'TEAM_CONFLICT') {
    return '같은 이름의 활성 팀이 이미 존재합니다.'
  }
  if (err.status === 404) return '팀을 찾을 수 없습니다.'
  if (err.status === 400) return '입력값을 확인해주세요.'
  if (err.status === 403) return '권한이 없습니다.'
  return '변경에 실패했습니다.'
}
