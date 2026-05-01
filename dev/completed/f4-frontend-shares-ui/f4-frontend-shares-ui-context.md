---
Last Updated: 2026-05-01
---

# F4 — Frontend Shares UI 실연결 context

## SESSION PROGRESS

- 2026-05-01: F4.0 bootstrap. worktree `feature/f4-frontend-shares-ui` 생성, master `e0957e5`(M9 closure) 베이스. plan/context/tasks 3파일 작성. 게이트 1(plan 리뷰) 직전 정지.

## Current Execution Contract

- **자율 실행** + 게이트별 정지 + 보고 (사용자 IbizDrive 자율 실행 모드).
- 게이트 1(F4.0 종료) 시점은 사용자 결정 필요(§핵심 결정 #1, #2, #6).
- TDD: phase별 RED → GREEN. 회귀 0(`pnpm test/typecheck/lint/build` full GREEN).
- KISS — 별도 mapper 파일 신설 금지. inline 매핑.
- 에러 envelope = 글로벌 `QueryCache.onError` 위임.
- ABORT 트레일 + 60분 0커밋 self-kill 적용.
- 단일 PR(F4.5) — sub-phase별 commit + squash-merge + closure direct push to master.

## 현재 active task

- **F4.0** ✅ 완료 (본 문서 작성). 게이트 1 사용자 승인 대기.
- **F4.1** 다음 — `types/share.ts` + `qk.shares` + `invalidations.afterShare*` + 단위 테스트.
- **F4.2** F4.1 GREEN 후 — `api.{createShares,revokeShare,listSharesByMe,listSharesWithMe}` + ≥15 테스트.
- **F4.3** F4.2 GREEN 후 — 4 hooks + ≥8 테스트.
- **F4.4** F4.3 GREEN 후 — ShareDialog 재구축 + `/shares` 페이지 + SharesTable + SharesLink + ≥11 테스트.
- **F4.5** F4.4 GREEN 후 — PR + closure.

## 다음 세션 읽기 순서

1. `dev/active/f4-frontend-shares-ui/f4-frontend-shares-ui-plan.md` — phase 계획, acceptance criteria, 결정 항목.
2. `dev/active/f4-frontend-shares-ui/f4-frontend-shares-ui-tasks.md` — 체크박스 + 참조 블록.
3. `docs/02-backend-data-model.md` §7.9 — 4 endpoint 정확한 contract.
4. `frontend/src/components/files/ShareDialog.tsx` — 재구축 대상 (mock placeholder, ~106줄).
5. `frontend/src/stores/shareUi.ts` — 기존 store(무수정 가능, 필요 시 확장).
6. `frontend/src/components/files/BulkActionBar.tsx` — Share 진입점 (시그니처 무수정).
7. `frontend/src/lib/api.ts` 끝부분 — 기존 method 패턴 참조 (M9 trash 메서드).
8. `frontend/src/lib/queryKeys.ts` — `qk.shares` 추가 위치.
9. `frontend/src/lib/invalidations.ts` — `afterShareCreate/afterShareRevoke` 추가.
10. `frontend/src/lib/api.search.test.ts` + `api.permissions.test.ts` — fetch wire 테스트 패턴 선례.
11. `frontend/src/components/files/TrashLink.tsx` + `app/(explorer)/trash/page.tsx` — `/shares` 미러 패턴.
12. (필요 시) `dev/completed/m9-frontend-trash/*` — M9급 단일 PR closure 절차.

## 핵심 파일과 역할

| 파일 | 역할 | 수정 |
|---|---|---|
| `frontend/src/types/share.ts` | ShareDto/ShareCreateRequest/ShareSubject/SharePreset 타입 (신설) | NEW |
| `frontend/src/lib/queryKeys.ts` | `qk.shares.byMe/withMe` 추가 | YES |
| `frontend/src/lib/invalidations.ts` | `afterShareCreate/afterShareRevoke` 추가 | YES |
| `frontend/src/lib/api.ts` | 4 메서드 추가 (createShares/revokeShare/list*) | YES |
| `frontend/src/lib/api.shares.test.ts` | API 단위 테스트 ≥15 (신설) | NEW |
| `frontend/src/hooks/useCreateShare.ts` | mutation hook (신설) | NEW |
| `frontend/src/hooks/useRevokeShare.ts` | mutation hook (신설) | NEW |
| `frontend/src/hooks/useSharesByMe.ts` | infinite query hook (신설) | NEW |
| `frontend/src/hooks/useSharesWithMe.ts` | infinite query hook (신설) | NEW |
| `frontend/src/components/files/ShareDialog.tsx` | mock 코드 제거 + 재구축 | YES (전면 재작성) |
| `frontend/src/stores/shareUi.ts` | UI store, 시그니처 안정 가능성 높음 | conditional |
| `frontend/src/app/(explorer)/shares/page.tsx` | server entry → client (신설) | NEW |
| `frontend/src/app/(explorer)/shares/ClientSharesPage.tsx` | client wrapper (신설) | NEW |
| `frontend/src/components/shares/SharesTable.tsx` | with-me 목록 4상태 (신설) | NEW |
| `frontend/src/components/shares/SharesLink.tsx` | Sidebar 진입 (신설) | NEW |
| `frontend/src/app/(explorer)/layout.tsx` | `<SharesLink />` mount | YES |
| `docs/01-frontend-design.md` §6.1/§14/§17 | qk.shares 등재 + share UI backlink + `/shares` 라우팅 | YES |
| `backend/.../ShareController.java` | A10 endpoint | NO (이미 머지) |
| `docs/02-backend-data-model.md` §7.9 | API spec | NO (A10에서 정합 완료) |

## 중요한 의사결정

1. **본 트랙은 mock→fetch swap 단일이 아님** — F1.1/F2.1과 달리 ShareDialog가 mock 링크 placeholder. 따라서 재구축 + 신규 페이지(`/shares`) + Sidebar 진입 필요. 결과적으로 M9급 스코프(단일 PR + 5 sub-phase).
2. **Subject 범위 = 'everyone' MVP**(§핵심 결정 #1) — backend는 4종 subject 지원하나 frontend user list endpoint 부재. UUID 직접 입력 UX 비현실. per-user 공유는 별도 트랙(`A-future user list` 후속).
3. **`/shares` 페이지 신설**(§핵심 결정 #2) — M9 `/trash` 패턴 mirror. with-me 목록은 진입점이 명확해야 하므로 ShareDialog 내부 탭이 아닌 별도 페이지.
4. **preset wire format = 4값**(`read|upload|edit|admin`) — ADR #34대로 `SHARE` preset 미노출.
5. **revoke 권한 = backend canRevoke 위임** — frontend by-me 목록에서만 revoke 노출. with-me에서는 revoke 버튼 없음.
6. **invalidation = by-me + with-me만** — file `qk.permissions(fileId)` 캐시는 무효화 미발생(같은 세션 자기→자기 share 케이스 없음, 실용적 불필요).
7. **expires_at timezone** — `<input type="datetime-local">` 입력 → `new Date(value).toISOString()` 변환 후 backend 전송. 단위 테스트 1건 추가.
8. **`/shares` SSG Suspense** — PR #24가 `<StatusBar />`을 Suspense로 감쌌으므로 `useSearchParams` 회귀 위험 낮음. F4.4 종료 시 `pnpm build` 검증 필수.
9. **Sidebar 위치 = TrashLink 위**(§핵심 결정 #6) — 받은 공유는 메인 워크플로(매일), 휴지통은 마이너 진입.

## 빠른 재개 안내

- **현재 어디인가**: F4.0 완료 — bootstrap 종료. 게이트 1(plan 리뷰) 정지점. 사용자 결정 #1, #2, #6 대기.
- **다음 행동**: 사용자 승인 즉시 F4.1 진입 — `types/share.ts` 작성 + `qk.shares` 추가 + invalidations 추가 + `queryKeys.test.ts` ≥4 추가 → GREEN.
- **블로커**: 결정 #1, #2, #6 미승인 시 F4.1 차단. 다른 결정은 본 plan에서 합의 가정.
- **재개 명령**:
  ```
  cd C:/project/IbizDrive/.claude/worktrees/f4-frontend-shares-ui
  cat dev/active/f4-frontend-shares-ui/f4-frontend-shares-ui-tasks.md
  pnpm --dir frontend test src/lib/queryKeys.test.ts -- --run
  ```
