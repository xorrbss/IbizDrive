'use client'
import type { AuditEventType, AuditLogFilters } from '@/types/audit'

/**
 * 감사 로그 필터 폼. controlled — 부모가 상태와 onChange 소유.
 *
 * Reset/Apply 분리 없이 즉시 반영(onChange) 방식. 디바운스는 부모에서 필요시 추가.
 * v1.0 mock 범위: actorName/eventType/dateRange만. IP/리소스 필터는 §7.1에 따라 백엔드 연결 후.
 */

const EVENT_TYPE_OPTIONS: ReadonlyArray<{ value: AuditEventType | ''; label: string }> = [
  { value: '', label: '전체' },
  { value: 'file.uploaded', label: '파일 업로드' },
  { value: 'file.downloaded', label: '파일 다운로드' },
  { value: 'file.renamed', label: '파일 이름 변경' },
  { value: 'file.moved', label: '파일 이동' },
  { value: 'file.deleted', label: '파일 삭제' },
  { value: 'file.restored', label: '파일 복원' },
  { value: 'folder.created', label: '폴더 생성' },
  { value: 'folder.renamed', label: '폴더 이름 변경' },
  { value: 'folder.audit_level_changed', label: '폴더 감사 레벨 변경' },
  { value: 'permission.granted', label: '권한 부여' },
  { value: 'permission.revoked', label: '권한 회수' },
  { value: 'share.created', label: '공유 생성' },
  { value: 'share.expired', label: '공유 만료' },
  { value: 'user.login.success', label: '로그인 성공' },
  { value: 'user.login.failed', label: '로그인 실패' },
  { value: 'admin.user.created', label: '관리자: 사용자 생성' },
  { value: 'admin.legal_hold.placed', label: '관리자: 법적 보존 설정' },
  { value: 'system.backup.completed', label: '시스템 백업 완료' },
  { value: 'audit.exported', label: '감사 로그 내보내기' },
] as const

interface Props {
  value: AuditLogFilters
  onChange: (next: AuditLogFilters) => void
  onReset: () => void
}

export function AuditFilters({ value, onChange, onReset }: Props) {
  const set = <K extends keyof AuditLogFilters>(key: K, v: AuditLogFilters[K]) =>
    onChange({ ...value, [key]: v })

  return (
    <form
      role="search"
      aria-label="감사 로그 필터"
      className="flex flex-wrap items-end gap-3 px-4 py-3 bg-surface-1 border-b border-border"
      onSubmit={(e) => e.preventDefault()}
    >
      <label className="flex flex-col gap-1 text-[12px] text-fg-muted">
        <span>행위자</span>
        <input
          type="text"
          value={value.actorQuery ?? ''}
          onChange={(e) => set('actorQuery', e.target.value)}
          placeholder="이름으로 검색"
          className="h-8 px-2 w-44 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
        />
      </label>
      <label className="flex flex-col gap-1 text-[12px] text-fg-muted">
        <span>이벤트</span>
        <select
          value={value.eventType ?? ''}
          onChange={(e) => set('eventType', e.target.value as AuditEventType | '')}
          className="h-8 px-2 w-56 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
        >
          {EVENT_TYPE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1 text-[12px] text-fg-muted">
        <span>시작일</span>
        <input
          type="date"
          value={value.fromDate ?? ''}
          onChange={(e) => set('fromDate', e.target.value || undefined)}
          className="h-8 px-2 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
        />
      </label>
      <label className="flex flex-col gap-1 text-[12px] text-fg-muted">
        <span>종료일</span>
        <input
          type="date"
          value={value.toDate ?? ''}
          onChange={(e) => set('toDate', e.target.value || undefined)}
          className="h-8 px-2 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
        />
      </label>
      <button
        type="button"
        onClick={onReset}
        className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
      >
        초기화
      </button>
    </form>
  )
}
