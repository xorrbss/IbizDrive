---
Last Updated: 2026-05-01
---

# F4 — Frontend Shares UI 실연결 plan

## 요약

A10 backend(`24a78b2`, PR #21)에서 출하된 4개 share endpoint(`POST /api/files/:id/share`, `DELETE /api/shares/:shareId`, `GET /api/shares/by-me`, `GET /api/shares/with-me`)를 frontend에 연결한다. 현재 ShareDialog는 mock 링크 + 클립보드 복사 placeholder 상태(M8 PR #16 도입). 본 트랙은:

1. **API client + types + qk + hooks + 테스트** — 4개 endpoint 전체.
2. **ShareDialog 재구축** — subject(MVP: `everyone` only) + preset select + 만료(optional) + 기존 share 목록 + revoke 버튼.
3. **`/shares` 페이지(받은 공유)** — `with-me` 목록 + 사이드바 진입 링크 (M9 `/trash` 패턴 미러).

A9→F1.1 / A11→F2.1 직렬화 패턴의 연장. 단, 본 트랙은 mock→fetch swap만이 아니라 ShareDialog 재구축 + 신규 페이지 신설을 포함하므로 M9급 스코프(단일 PR + 5~6 sub-phase).

## 현재 상태 분석

### Backend (master `e0957e5`)

- `POST /api/files/:fileId/share` — `hasPermission(#fileId, 'file', 'SHARE')`. Request: `{ subjects: [{type, id?}], preset, expiresAt?, message? }`. Response: `201 { shares: ShareDto[] }`. Errors: 400/404/409.
- `DELETE /api/shares/:shareId` — `canRevoke(shareId, principal)` (자기 + ADMIN). Response: 204. Errors: 403/404.
- `GET /api/shares/by-me?cursor=&limit=` — by-me 페이지네이션 (cursor opaque base64).
- `GET /api/shares/with-me?cursor=&limit=` — MVP는 `subject_type='user' AND subject_id=actor` 매칭만.
- ShareDto: `{ id, fileId, permissionId, sharedBy, subjectType, subjectId, preset, expiresAt?, message?, createdAt }`.
- audit emit: `share.created` / `share.revoked` (`permission.revoked`는 이중 발행 회피로 미발행, ADR #34).

### Frontend (master `e0957e5`)

- `frontend/src/components/files/ShareDialog.tsx` — mock 링크 placeholder. `https://ibiz.example/share/{fileId}` 표기 + 클립보드 복사. backend 연동 0.
- `frontend/src/stores/shareUi.ts` — `{isOpen, fileId, fileName, open, close}` 최소.
- `frontend/src/components/files/BulkActionBar.tsx` — "공유" 버튼 mount 완료 (`shareEnabled = count===1 && type==='file'`). 클릭 시 `useShareUiStore.open(fileId, fileName)`.
- `frontend/src/lib/api.ts` — share 메서드 0건.
- `frontend/src/lib/queryKeys.ts` — `qk.shares` 미정의.
- types/hooks/페이지 모두 미존재.

### docs/01-frontend-design.md

- §6.1 쿼리 키 팩토리 — `qk.shares` 미등록 (본 트랙에서 추가).
- §14 권한 (프론트) — share UI backlink 미작성 (본 트랙에서 추가).
- §17 라우팅 — `/shares` 신설 미반영 (본 트랙에서 추가).

### docs/02-backend-data-model.md §7.9

이미 완비. backend A10 closure 시점에 sync 완료. frontend에서 참조만.

## 목표 상태

1. **API**: `api.{createShares, revokeShare, listSharesByMe, listSharesWithMe}` 4개 메서드 신설(실 fetch). DTO 타입 1:1 미러.
2. **Types**: `frontend/src/types/share.ts` — `ShareDto`, `ShareSubject`, `SharePreset`, `ShareSubjectType`, `SharePage`, `ShareCreateRequest`.
3. **Query keys**: `qk.shares.byMe()` / `qk.shares.withMe()` opaque (cursor 미포함). 무효화는 `invalidations.afterShareCreate` / `afterShareRevoke` 신설 — by-me + with-me + (옵션: `qk.permissions(fileId)` re-evaluate).
4. **Hooks**:
   - `useCreateShare()` — mutation, onSuccess 후 invalidation.
   - `useRevokeShare()` — mutation.
   - `useSharesByMe()` — `useInfiniteQuery` + cursor.
   - `useSharesWithMe()` — `useInfiniteQuery` + cursor.
5. **ShareDialog 재구축** — subject(`everyone` only MVP) + preset radio (read/upload/edit/admin) + expiresAt(optional, datetime-local input) + 기존 by-me 목록 (해당 fileId 필터) + revoke 버튼. mock 링크/클립보드 코드 제거.
6. **`/shares` 페이지** — `app/(explorer)/shares/page.tsx` + `ClientSharesPage` + `SharesTable` (with-me 목록). Sidebar `<SharesLink />` 추가 (M9 `<TrashLink />` 패턴 미러).
7. **docs/01 추가** — §6.1에 `qk.shares` 등재 + §14에 share UI backlink + §17에 `/shares` 라우팅 등재.
8. **테스트 ≥30건 GREEN**: api(15+) / hooks(8+) / ShareDialog(5+) / SharesTable(4+) / SharesLink(2). 기존 회귀 0.

## phase별 실행 지도

### F4.0 — dev-docs bootstrap (현재 phase, 게이트 1)

본 plan/context/tasks 3파일 생성. **사용자 승인 후** F4.1 진입.

핵심 결정사항 사용자 확인 필요(아래 §핵심 결정 §1, §2, §6).

### F4.1 — qk.shares + types + 무효화 헬퍼 (게이트 2)

**구현**:
1. `frontend/src/types/share.ts` 신설 — backend ShareDto/ShareCreateRequest 1:1.
2. `lib/queryKeys.ts`에 `qk.shares = { byMe: () => [...], withMe: () => [...] }` 추가.
3. `invalidations.afterShareCreate(qc)` / `afterShareRevoke(qc)` 신설 — by-me + with-me 일괄.
4. `queryKeys.test.ts`에 신규 키 + 무효화 테스트 RED→GREEN.

**검증**: ≥4 테스트 GREEN. 회귀 0.

### F4.2 — api.{createShares,revokeShare,listSharesByMe,listSharesWithMe} + 테스트 (게이트 3)

**구현 (TDD)**:
1. `api.shares.test.ts` 작성 — fetch wire 계약 (URL/method/body, 응답 매핑, 401/403/404/409 envelope) RED.
2. `api.ts`에 4 메서드 추가. `buildApiError` helper 재사용 (M9.1에서 도입).
3. GREEN.

**테스트 케이스 (15+)**:
- createShares: 단일 subject 성공 / 다중 subject / message 포함 / expiresAt 포함 / 400 BAD_REQUEST(subjects empty) / 404 / 409 PERMISSION_CONFLICT.
- revokeShare: 204 success / 403 / 404.
- listSharesByMe: 빈 결과 / nextCursor 있음 / 401.
- listSharesWithMe: 빈 결과 / nextCursor 있음 / cursor 전달 검증.

### F4.3 — hooks + 테스트 (게이트 4)

**구현**:
1. `useCreateShare.ts` — mutation, onSuccess → `invalidations.afterShareCreate(qc)`.
2. `useRevokeShare.ts` — mutation, onSuccess → `invalidations.afterShareRevoke(qc)`.
3. `useSharesByMe.ts` / `useSharesWithMe.ts` — `useInfiniteQuery` + getNextPageParam.

**테스트 케이스 (≥8)**:
- useCreateShare 성공 시 by-me/with-me 무효화.
- useRevokeShare 성공 시 by-me 무효화.
- useSharesByMe initial fetch + nextPage.
- useSharesWithMe initial fetch + cursor 전달.
- 에러 시 무효화 미발생.

### F4.4 — ShareDialog 재구축 + SharesTable + /shares 페이지 + Sidebar 진입 (게이트 5)

**구현**:
1. `ShareDialog.tsx` 재작성 — preset radio + expiresAt input + subject 'everyone' 고정 + 기존 by-me list filter by fileId + revoke 버튼. 기존 mock 코드 전면 제거.
2. `app/(explorer)/shares/page.tsx` + `ClientSharesPage` (server entry → client).
3. `components/shares/SharesTable.tsx` — with-me 목록 (이름 / 공유한 사람 / preset / 만료 / 액션). `useFilesInFolder` 같은 4상태(loading/error/empty/data) 패턴.
4. `components/shares/SharesLink.tsx` — Sidebar 진입 (M9 TrashLink mirror).
5. `app/(explorer)/layout.tsx` — `<SharesLink />` mount.

**테스트**:
- ShareDialog: 4상태 + 'everyone' subject 고정 + preset 변경 + 기존 share revoke 클릭 → useRevokeShare 호출 (≥5).
- SharesTable: 4상태 + 만료 표시 + revoke 차단 (with-me는 revoke 불가) (≥4).
- SharesLink: href + active state (2).

### F4.5 — closure (게이트 6)

PR `feat(F4): frontend shares UI (POST/DELETE/list 4 endpoint 연결 + /shares + ShareDialog 재구축)` → CI green → squash-merge → archive `dev/active → dev/completed` → `docs/progress.md` F4 closure entry.

## 핵심 결정 (게이트 1 사용자 확인 필요)

### 1. **Subject 범위 — MVP 'everyone' only** ⚠️ 결정 필요

backend는 user/department/role/everyone 4 종 subject 지원. 그러나 frontend는 user/department/role 목록 endpoint(`GET /api/users` 등) **부재**. UUID 직접 입력 UX는 사용성 매우 낮음.

**제안 (A)**: MVP 'everyone' subject만 — preset 라디오 + (옵션) message + expiresAt만 노출. 사내 공유 링크 유스케이스 1차 충족. per-user/department/role 공유는 별도 트랙(`A12 — user list endpoint` 후속 + F4 follow-up).

**대안 (B)**: subject 텍스트 UUID 입력 — 사용자가 동료 UUID를 직접 입력. 기능적이지만 비현실적 UX.

**대안 (C)**: A12(folder share)와 합쳐 user list endpoint 신설 후 F4 진입 — 본 트랙 차단, 일정 ↑.

**추천 = (A)**. 근거:
- backend가 'everyone' 지원하므로 즉시 가치 출하.
- per-user 공유는 user picker 부재 시 의미 없음 — 별도 트랙 자연.
- ShareDialog는 향후 subject picker 추가 시 동일 hook 호출부 유지(시그니처 안정).

### 2. **`/shares` 페이지 vs ShareDialog 내부 list** ⚠️ 결정 필요

with-me(받은 공유) 목록을 어디에 노출?

**제안 (A)**: 별도 `/shares` 페이지 — Sidebar 진입 링크. M9 `/trash` 패턴 미러. 받은 공유 항목 클릭 시 해당 파일로 이동(`/files/...?file=`).

**대안 (B)**: ShareDialog 내부 탭으로 통합 — "내가 공유한 / 받은 공유" 탭. Dialog가 무거워짐.

**대안 (C)**: `/shares` 페이지 생략, 받은 공유는 별도 트랙으로 deferral.

**추천 = (A)**. 근거:
- 받은 공유는 진입점이 명확해야 함(사이드바). Dialog는 일시적 진입.
- M9 패턴 mirror로 일관성.
- (C) 채택 시 backend `with-me` endpoint 활용처 0 → 가치 손실.

### 3. ShareDialog `expiresAt` UX — datetime-local input

HTML5 `<input type="datetime-local">` 사용. timezone offset은 frontend에서 `new Date().toISOString()`으로 변환. backend는 ISO8601 expects.

### 4. preset wire format = backend 4값 (`read|upload|edit|admin`)

ADR #34: SHARE preset 미지원 (V5 CHECK 위반 → 400). 본 트랙은 4값만 노출. `Preset.SHARE` 옵션 frontend에 표시 금지.

### 5. revoke 권한 가드는 backend 위임

`canRevoke = (sharedBy == me) || (role==ADMIN)`. frontend는 by-me 목록에서만 revoke 노출(보수 정책). 403 fallback은 toast.error.

### 6. **Sidebar 위치** — TrashLink 위 vs 아래 ⚠️ 결정 필요

현재 layout: `<FolderTree />` ... `<TrashLink />` ... `<StorageBar />`. SharesLink 위치?

**제안 (A)**: TrashLink **위**에 SharesLink 추가 (FolderTree 직후, mt-auto 영역 바로 위).

**대안 (B)**: TrashLink **아래** (TrashLink와 StorageBar 사이).

**추천 = (A)**. 근거: 휴지통은 마이너 진입(삭제 후 복원), 받은 공유는 메인 워크플로(매일 보는 항목). 휴지통보다 위에 배치.

### 7. invalidation 매트릭스 — share 변경이 file 권한 캐시에 영향

`createShares`는 `permissions` 테이블 INSERT를 발생 → file READ 권한이 받은 사람에게 부여됨. 받은 사람의 다음 fetch 시 권한 갱신 필요. 그러나:
- frontend `qk.permissions(fileId)`는 권한 보유자별 조회 — 받은 사람의 캐시는 다른 세션.
- 같은 세션에서 자기에게 share를 부여하는 케이스는 없음(자기 → 자기 share 차단 금지지만 의미 없음).
- 단순화: `afterShareCreate`는 by-me + with-me만 무효화. file permissions 캐시 무효화는 미실행 (실용적으로 불필요).

## acceptance criteria

1. `api.{createShares, revokeShare, listSharesByMe, listSharesWithMe}` 4 메서드 fetch 본체 + 401/403/404/409 envelope handling.
2. `qk.shares.byMe()` / `qk.shares.withMe()` opaque 키 + `invalidations.afterShareCreate` / `afterShareRevoke`.
3. `useCreateShare` / `useRevokeShare` mutation + `useSharesByMe` / `useSharesWithMe` infinite query 4 hooks GREEN.
4. ShareDialog 재구축 — subject 'everyone' 고정 + preset radio + expiresAt input + 기존 by-me list (이 fileId 필터) + revoke 버튼. mock 코드 제거.
5. `/shares` 페이지 + `<SharesLink />` Sidebar mount + with-me 목록 4상태.
6. 테스트 ≥30건 GREEN. 회귀 0.
7. docs/01 §6.1 + §14 + §17 `/shares` 등재.
8. `pnpm test` / `pnpm typecheck` / `pnpm lint` / `pnpm build` 모두 GREEN.

## DoD (10 항목)

- [ ] (1) F4.0 dev-docs 작성 + 사용자 승인.
- [ ] (2) `types/share.ts` + `qk.shares` + `invalidations.afterShare*`.
- [ ] (3) `api.shares.test.ts` ≥15 GREEN + `api.ts` 4 메서드.
- [ ] (4) hooks 4종 + 테스트 ≥8 GREEN.
- [ ] (5) ShareDialog 재구축 + 테스트 ≥5 GREEN.
- [ ] (6) `/shares` + SharesTable + SharesLink + 테스트 ≥6 GREEN.
- [ ] (7) docs/01 §6.1 + §14 + §17 sync.
- [ ] (8) `pnpm test` / `typecheck` / `lint` / `build` 모두 GREEN.
- [ ] (9) PR 생성 + CI green + squash-merge.
- [ ] (10) closure commit + dev-docs archive + worktree cleanup.

## 검증 게이트

- F4.0 → 사용자 plan 리뷰 + 결정 #1, #2, #6 확정 → F4.1 진입.
- F4.1 → ≥4 테스트 GREEN, queryKeys 무효화 정합 → F4.2 진입.
- F4.2 → ≥15 api 테스트 GREEN, 회귀 0 → F4.3 진입.
- F4.3 → ≥8 hook 테스트 GREEN, 회귀 0 → F4.4 진입.
- F4.4 → ≥11 UI 테스트 GREEN, build /shares SSG 통과 → F4.5 진입.
- F4.5 → PR CI green + 사용자 승인 → squash-merge + closure.

## 리스크와 완화

1. **subject UX 빈약 — 'everyone' MVP** — backlog: `A-future` user list endpoint + F4 follow-up. 본 트랙은 'everyone' 한정으로 작은 가치 출하.
2. **expires_at timezone 변환 오류** — datetime-local 입력은 local time. `new Date(value).toISOString()` 변환 + 단위 테스트.
3. **with-me 갱신 race** — share 받은 직후 다른 탭에서 표시 안 됨. M10 SSE 도입 전까지는 staleTime 기본(5min)으로 절충. 사용자가 페이지 새로고침하면 즉시 반영.
4. **revoke 후 file 접근권 즉시 회수** — backend가 permissions row delete까지 처리(§7.9). frontend는 file 캐시 갱신 없이도 다음 액션 시 403 → toast 폴백.
5. **`/shares` SSG Suspense 회귀** — `(explorer)` 레이아웃에 `<StatusBar />`이 이미 Suspense 감쌈(PR #24). 동일 매커니즘 → 회귀 가능성 낮음. F4.4 종료 시 build 검증 필수.
6. **단일 PR 크기 (≈12 파일 추가/수정)** — M9급. CI 시간 + 리뷰 부담 ↑. sub-phase별 commit + 별도 dev-docs로 추적성 확보.

## 비고

- A12(folder 공유) 관련 결정은 본 트랙 외 — file 공유만 MVP. ADR #34 backlog.
- SHARE_EXPIRED 자동 전환 cron은 backend 별도 트랙(deferred). frontend는 만료된 share를 with-me/by-me 응답에 포함하지 않음(backend WHERE expires_at IS NULL OR expires_at > NOW()).
- M9 패턴 mirror — sub-phase 게이트별 commit + 단일 PR squash-merge + closure direct push to master.
