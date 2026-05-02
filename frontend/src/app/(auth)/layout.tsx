/**
 * 비로그인 전용 레이아웃 (auth-pages, ADR #41).
 *
 * <p>(explorer)와 분리된 라우트 그룹 — 사이드바/TopBar/DnD 일체 미포함의 미니멀 화면.
 * 이미 로그인된 사용자가 `/login` 또는 `/signup`을 직접 열면 LoginPage/SignupPage가
 * useMe로 감지해 `/files`로 redirect (server가 아니라 client). 본 layout은 chrome만 제공.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen w-screen flex items-center justify-center bg-bg text-fg p-4">
      {children}
    </div>
  )
}
