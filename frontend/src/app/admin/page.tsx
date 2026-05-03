import Link from 'next/link'

/**
 * `/admin` 진입점 landing page (m-admin-entry-rewrite, docs/04 §3).
 *
 * <p>대시보드 자체(§3 운영 지표)는 v1.x deferred이므로 본 트랙은 가용 기능
 * 카드 2개(감사 로그, 사용자 초대) + deferred 안내만 노출한다. 별도 stub
 * `/admin/dashboard` 라우트는 만들지 않음(YAGNI).
 */
export default function AdminLandingPage() {
  return (
    <div className="p-8 max-w-[960px]">
      <h1 className="text-[20px] font-semibold text-fg mb-1">관리자</h1>
      <p className="text-[13px] text-fg-2 mb-6">
        현재 가용 기능입니다. 운영 지표 / 부서 / 권한 / 스토리지 등은 v1.x에서 추가됩니다.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-8">
        <Link
          href="/admin/audit/logs"
          className="block p-4 rounded border border-border bg-surface-1 hover:bg-surface-2 transition-colors"
        >
          <div className="text-[14px] font-medium text-fg">감사 로그</div>
          <div className="text-[12px] text-fg-2 mt-1">
            전체 감사 이벤트 검색 / 필터 / 상세 조회
          </div>
        </Link>
        <Link
          href="/admin/users"
          className="block p-4 rounded border border-border bg-surface-1 hover:bg-surface-2 transition-colors"
        >
          <div className="text-[14px] font-medium text-fg">사용자 초대</div>
          <div className="text-[12px] text-fg-2 mt-1">
            이메일로 신규 사용자 초대 (임시 비밀번호 자동 발송)
          </div>
        </Link>
      </div>
      <div className="rounded border border-dashed border-border p-4 bg-surface-1">
        <div className="text-[13px] font-medium text-fg-2 mb-2">v1.x에서 추가 예정</div>
        <ul className="text-[12px] text-fg-muted grid grid-cols-2 sm:grid-cols-4 gap-y-1 gap-x-4 list-disc list-inside">
          <li>대시보드</li>
          <li>부서</li>
          <li>권한</li>
          <li>스토리지</li>
          <li>휴지통</li>
          <li>Legal Hold</li>
          <li>정책</li>
          <li>시스템</li>
        </ul>
      </div>
    </div>
  )
}
