import Link from 'next/link'

/**
 * 관리자 진입점 (m-admin-entry).
 *
 * <p>{@code /admin} 직접 접근 시 표시되는 landing. dashboard(docs/04 §3)는 v1.x
 * deferred이므로 별도 stub 라우트를 만들지 않고 본 페이지에서 가용 기능 카드 +
 * deferred 안내를 제공한다(docs/04 §2 라우트 트리 참조).
 */
export default function AdminLandingPage() {
  return (
    <div className="h-full overflow-auto p-6">
      <header className="mb-6">
        <h1 className="text-[18px] font-semibold text-fg">관리자</h1>
        <p className="mt-1 text-[12.5px] text-fg-muted">
          MVP 단계에서는 감사 로그 조회만 활성. 그 외 운영 기능은 v1.x 이후 단계적으로 추가됩니다.
        </p>
      </header>

      <section aria-labelledby="admin-active" className="mb-8">
        <h2 id="admin-active" className="text-[13px] font-semibold text-fg mb-2">
          가용 기능
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <Link
            href="/admin/audit/logs"
            className="block p-4 rounded border border-border bg-surface-1 hover:bg-surface-2 transition-colors"
          >
            <div className="text-[13px] font-medium text-fg">감사 로그</div>
            <div className="mt-1 text-[12px] text-fg-muted">
              사용자 활동·권한 변경·다운로드 등 감사 이벤트 조회.
            </div>
          </Link>
        </div>
      </section>

      <section aria-labelledby="admin-deferred">
        <h2 id="admin-deferred" className="text-[13px] font-semibold text-fg mb-2">
          v1.x 이후 (예정)
        </h2>
        <ul className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2 text-[12px] text-fg-muted">
          {[
            '대시보드',
            '사용자 관리',
            '부서 관리',
            '권한 매트릭스',
            '스토리지 정책',
            '휴지통 정책',
            'Legal Hold',
            '시스템 상태',
          ].map((label) => (
            <li
              key={label}
              className="px-3 py-2 rounded border border-border bg-surface-1 opacity-70"
            >
              {label}
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
