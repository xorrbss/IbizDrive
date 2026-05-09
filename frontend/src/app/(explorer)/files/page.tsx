import { redirect } from 'next/navigation'

/**
 * Plan B 호환 stub — Plan B에서 `/files` 라우트를 폐기했지만 (auth)/signup, password,
 * AdminGuard, login (default `next`) 등 5개 진입점이 여전히 `/files`로 redirect한다.
 * 그 callsite들을 모두 갱신하는 대신 본 stub이 `/`로 다시 redirect — `app/page.tsx`가
 * 사용자의 첫 workspace로 라우팅한다. callsite 갱신은 별 PR로 정리 가능.
 */
export default function FilesRedirect() {
  redirect('/')
}
