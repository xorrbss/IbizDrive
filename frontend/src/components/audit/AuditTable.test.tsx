import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AuditTable } from './AuditTable'
import type { AuditLogEntry } from '@/types/audit'

const entry: AuditLogEntry = {
  id: 'a1',
  occurredAt: '2026-04-25T10:30:00Z',
  eventType: 'file.uploaded',
  actorId: 'user_kim',
  actorName: '김영수',
  resourceType: 'file',
  resourceId: 'res_1',
  resourceName: '제안서.pdf',
  ip: '10.0.1.1',
  metadata: null,
}

describe('AuditTable', () => {
  it('isLoading=true면 로드 중 표시', () => {
    render(<AuditTable entries={[]} isLoading={true} isError={false} />)
    expect(screen.getByText('로드 중...')).toBeTruthy()
  })

  it('isError=true면 alert 표시', () => {
    render(<AuditTable entries={[]} isLoading={false} isError={true} />)
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('빈 배열 + 로드 완료면 안내문', () => {
    render(<AuditTable entries={[]} isLoading={false} isError={false} />)
    expect(screen.getByText(/감사 로그가 없습니다/)).toBeTruthy()
  })

  it('row 렌더 — eventType, actor, resource, ip 표시', () => {
    render(<AuditTable entries={[entry]} isLoading={false} isError={false} />)
    expect(screen.getByText('file.uploaded')).toBeTruthy()
    expect(screen.getByText('김영수')).toBeTruthy()
    expect(screen.getByText('제안서.pdf')).toBeTruthy()
    expect(screen.getByText('10.0.1.1')).toBeTruthy()
  })

  it('aria-rowcount는 entries+1 (헤더 포함)', () => {
    render(<AuditTable entries={[entry, { ...entry, id: 'a2' }]} isLoading={false} isError={false} />)
    const table = screen.getByRole('table')
    expect(table.getAttribute('aria-rowcount')).toBe('3')
  })

  it('resourceName이 null이면 [type]만, type도 null이면 dash', () => {
    render(
      <AuditTable
        entries={[
          { ...entry, id: 'b', resourceName: null, resourceType: 'system' },
          { ...entry, id: 'c', resourceName: null, resourceType: null, ip: null },
        ]}
        isLoading={false}
        isError={false}
      />,
    )
    expect(screen.getByText('[system]')).toBeTruthy()
    // dash row의 ip 셀도 — 표시
    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBeGreaterThanOrEqual(1)
  })
})
