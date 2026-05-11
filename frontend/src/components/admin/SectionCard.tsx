import type { ReactNode } from 'react'

/**
 * Admin 페이지 공통 SectionCard — 디자인 핸드오프 2026-05-10 admin.jsx §SectionCard
 * (L212~225) 1:1 매핑.
 *
 * <p>admin.css `.section-card` / `.section-card-head` / `.section-card-title` /
 * `.section-card-sub` / `.section-card-body` (L233~250) 클래스를 wiring 한다.
 * 제목/서브타이틀 + 우측 슬롯(액션 버튼·요약 통계 등) + body children을 표준 모양으로
 * 묶어 admin 페이지가 동일 카드 형태를 공유하도록 한다.
 *
 * <p>Phase 3a (sharing) inline helper 와 Phase 3b (overview) 양쪽에서 동일 helper 가
 * 두 번 이상 반복 사용되는 시점에 components/admin 로 승격. 추가 사용처가 생기면
 * 그대로 import 한다 (Phase 3 design fidelity sweep Sub-phase 2 결정).
 */
export interface SectionCardProps {
  /** 카드 타이틀 (h2 / h3로 렌더되며 본 컴포넌트는 h2 사용 — 페이지 내 h1 아래 단계) */
  title: string
  /** 보조 설명 (선택) — title 아래 1줄 muted */
  subtitle?: string
  /** 헤더 우측 슬롯 (선택) — 버튼/요약 통계 등 */
  right?: ReactNode
  /** body 영역 children */
  children: ReactNode
}

export function SectionCard({ title, subtitle, right, children }: SectionCardProps) {
  return (
    <section className="section-card">
      <header className="section-card-head">
        <div>
          <h2 className="section-card-title">{title}</h2>
          {subtitle && <p className="section-card-sub">{subtitle}</p>}
        </div>
        {right}
      </header>
      <div className="section-card-body">{children}</div>
    </section>
  )
}
