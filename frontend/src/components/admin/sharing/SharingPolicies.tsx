'use client'
import { Share2, Clock, Lock, Download } from 'lucide-react'
import type { ReactNode } from 'react'

/**
 * 외부 공유 정책 4개 row (admin.jsx L596~622).
 *
 * <p>각 row는 아이콘 + label + sub + value + "변경" 버튼. "변경" 버튼은 backend
 * mutation endpoint가 v1.x backlog이므로 disabled + tooltip 처리한다 (visual
 * fidelity 우선 — 클릭해도 mutation 호출 0).
 *
 * <p>style: `policy-row` (admin.css L483~503). tone="warn"은 design 원본
 * "공개 링크" row 한정.
 */
export function SharingPolicies() {
  return (
    <div>
      <PolicyRow
        label="외부 도메인 공유"
        value="허용 (관리자 검토 필요)"
        sub="플래그된 키워드 또는 PII 패턴 발견 시 자동 검토 큐"
        icon={<Share2 size={13} aria-hidden="true" />}
      />
      <PolicyRow
        label="공유 링크 기본 만료"
        value="30일"
        sub="이후 자동 만료 — 만료 직전 알림"
        icon={<Clock size={13} aria-hidden="true" />}
      />
      <PolicyRow
        label="공개 링크 (누구나)"
        value="관리자 승인 필요"
        sub="기본은 차단 — 요청 시 검토 후 승인"
        icon={<Lock size={13} aria-hidden="true" />}
        tone="warn"
      />
      <PolicyRow
        label="다운로드 차단"
        value="민감 폴더에 자동 적용"
        sub="재무, 인사, 법무 폴더는 뷰 전용"
        icon={<Download size={13} aria-hidden="true" />}
      />
    </div>
  )
}

interface PolicyRowProps {
  label: string
  value: string
  sub: string
  icon: ReactNode
  tone?: 'warn'
}

function PolicyRow({ label, value, sub, icon, tone }: PolicyRowProps) {
  return (
    <div className={`policy-row${tone ? ` tone-${tone}` : ''}`}>
      <div className="policy-icon">{icon}</div>
      <div className="policy-main">
        <div className="policy-label">{label}</div>
        <div className="policy-sub">{sub}</div>
      </div>
      <div className="policy-value">{value}</div>
      <button
        type="button"
        className="btn-ghost btn-xs"
        disabled
        title="v1.x 후속 트랙 — backend sharing-policy endpoint 미연결"
        aria-disabled="true"
      >
        변경
      </button>
    </div>
  )
}
