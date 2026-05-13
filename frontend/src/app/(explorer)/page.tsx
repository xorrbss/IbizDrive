'use client'
import { HomeDashboard } from '@/components/home/HomeDashboard'

/**
 * root `/` — User Home Dashboard (ADR #48, 2026-05-14).
 *
 * <p>이전 동작: 첫 부서/팀 root 폴더로 redirect. 본 PR 이후: personal dashboard 4 위젯 렌더.
 * workspace 진입은 (explorer) layout 의 사이드바 트리 단일 진입점.
 */
export default function Home() {
  return <HomeDashboard />
}
