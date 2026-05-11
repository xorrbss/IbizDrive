---
task: design fidelity G2/G3 + sidebar collapse
last_updated: 2026-05-11
---

# Context

## 핵심 참조

| 파일 | 역할 |
|---|---|
| `dev/active/design-handoff-gap-report-2026-05-10.md` | G1-G9 갭 리포트 (G1/G8/G9 ✅, G2/G3 본 트랙, G4/G5/G6/G7 별도) |
| `docs/01-frontend-design.md` §2 | 사이드바 구조 (Plan B 후 3-section + lazy load) |
| `docs/01-frontend-design.md` §10 | 검색 단축키 |
| `docs/01-frontend-design.md` §17 | layout/라우팅 |
| `frontend/src/app/(explorer)/layout.tsx` | aside(w-248) + main(TopBar/children/StatusBar) 구조 |
| `frontend/src/components/topbar/TopBar.tsx` | 현재 flex justify-between, 햄버거 없음 |
| `frontend/src/components/topbar/SearchBar.tsx` | w-72 + `/` 단축키 hint |
| `frontend/src/hooks/useGlobalShortcuts.ts` | `/` only |
| `frontend/src/stores/sidebarTree.ts` | persist 패턴 답습 source |

## 패턴 결정

- **store 분리** — `sidebarTree`는 폴더 expand state(30일 TTL 마이그레이트). `sidebarChrome`는 chrome toggle 단일 boolean. 책임 분리.
- **w-0 transition** — aside 자체를 `w-0`/`w-[248px]` 전환 (display:none 미사용). `overflow-hidden` 자식 보존, SR `aria-hidden`로 가림.
- **햄버거 항상 노출** — collapsed 시에도 햄버거 버튼은 TopBar에 남아 있어 다시 열 수 있음. TopBar 위치 변경 없음.
- **kbd 칩 vs placeholder hint** — placeholder text 유지(언어 분기 회피), 시각 hint는 우측 kbd 칩이 보강.

## 위험 / 함정

- **⌘K 브라우저 충돌**: Firefox 검색바, Chrome 일부 OS에서 충돌. preventDefault로 차단.
- **transition 깜빡임**: `transition-[width]` 중 자식 컨텐츠 reflow. `overflow-hidden`로 잘림 처리 + `pointer-events-none` 미적용(focus 가드는 aria-hidden로 충분).
- **persist hydration 미스매치**: SSR 초기 렌더가 collapsed=false → 첫 client paint와 다를 수 있음. `useEffect` 후 sync 시 깜빡임 발생 가능. zustand persist 기본 동작은 hydration 후 store rehydrate — 단발 깜빡임은 수용 가능(KISS).

## 향후 (트랙 종료 후)

- **mobile-view G7** — `lg:` breakpoint 분기로 사이드바/RP 자동 hide, 본 트랙 collapse store와 통합 가능.
- **TweaksPanel density 토글 G5** — viewStore 신규 또는 sidebarChrome 확장.
- **단축키 ?cheat sheet** — `?` 누르면 단축키 도움말 modal. v1.x backlog.
