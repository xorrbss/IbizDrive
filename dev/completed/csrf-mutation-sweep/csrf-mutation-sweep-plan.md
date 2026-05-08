# csrf-mutation-sweep — Plan

Last Updated: 2026-05-09

## 요약

`frontend/src/lib/api.ts`의 모든 mutation(POST/PATCH/PUT/DELETE) 호출에서
`X-CSRF-TOKEN` 헤더 누락 일괄 차단. PR #115(`fix-create-folder-csrf`)
hotfix에서 발견된 회귀가 다른 11개 mutation에도 동일하게 존재함을 sweep으로
확인. 누락된 mutation에 헤더 추가 + 회귀 가드 vitest 추가.

## 현재 상태 분석

### 백엔드 정책 (SecurityConfig.java)

- CSRF: cookie-based double-submit (`CookieCsrfTokenRepository#withHttpOnlyFalse`)
- header 이름: `X-CSRF-Token` (Spring default `X-XSRF-TOKEN` 대신 docs 계약)
- HTTP 헤더 case-insensitive 라 frontend의 `X-CSRF-TOKEN`과 매칭됨.
- 면제: `/api/auth/signup`, `/api/auth/password/forgot`,
  `/api/auth/password/reset` (ADR #41 self-signup, A1.5 비밀번호 분실).

### 프론트엔드 인벤토리

`api.ts`의 mutation fetch 호출 23건 (createFolder는 PR #115에서 처리 중이라
본 sweep 제외):

| # | 라인 | 함수 | method | CSRF | 처리 |
|---|---|---|---|---|---|
| 1 | 285 | `restoreFileVersion` | POST | ❌ | **fix** |
| 2 | 306 | `softDeleteFile` | DELETE | ❌ | **fix** |
| 3 | 318 | `softDeleteFolder` | DELETE | ❌ | **fix** |
| 4 | 344 | `moveItem` | POST | ❌ | **fix** |
| 5 | 395 | `renameItem` | PATCH | ❌ | **fix** |
| 6 | 444 | `createFolder` | POST | ❌ | PR #115 (제외) |
| 7 | 966 | `restoreFile` | POST | ❌ | **fix** |
| 8 | 985 | `restoreFolder` | POST | ❌ | **fix** |
| 9 | 1007 | `purgeTrashItem` | DELETE | ❌ | **fix** |
| 10 | 1046 | `revokeShare` | DELETE | ❌ | **fix** |
| 11 | 1080 | `signup` | POST | ❌ | 면제 (제외) |
| 12 | 1100 | `login` | POST | ✅ | OK |
| 13 | 1122 | `logout` | POST | ✅ | OK |
| 14 | 1155 | `passwordForgot` | POST | ❌ | 면제 (제외) |
| 15 | 1172 | `passwordReset` | POST | ❌ | 면제 (제외) |
| 16 | 1190 | `passwordChange` | POST | ✅ | OK |
| 17 | 1225 | `adminInviteUser` | POST | ✅ | OK |
| 18 | 1277 | `adminUpdateUser` | PATCH | ✅ | OK |
| 19 | 1339 | `adminDeptCreate` | POST | ✅ | OK |
| 20 | 1375 | `adminDeptUpdate` | PATCH | ✅ | OK |
| 21 | 1421 | `adminToggleCron` | PUT | ❌ | **fix** |
| 22 | 1561 | `shares helper` (`postShareJson`) | POST | ❌ | **fix** |
| 23 | 1722 | `adminBulkTrash` | POST | ❌ | **fix** |

**총 11건 fix 대상** (sweep 범위).

### 헬퍼 가용성

- `readCookie('XSRF-TOKEN')` 헬퍼: api.ts L1645에 이미 존재. 본 sweep은
  이 헬퍼 재사용 — `createFolder` hotfix(PR #115)와 동일 패턴.
- `ensureCsrfToken()` (L1632 근처): cookie 없으면 `/api/auth/csrf` 호출
  보장. login/logout/passwordChange/admin*은 이 비동기 패턴. 인증 후
  mutation은 이미 cookie에 토큰이 있는 게 정상이라 `readCookie` 동기 패턴
  충분 (createFolder hotfix 결정과 일치).

## 목표 상태

11건 mutation 각각이:
1. fetch headers에 `'X-CSRF-TOKEN': csrf` 포함 (`csrf` = `readCookie('XSRF-TOKEN')`).
2. 회귀 가드 vitest로 헤더 송신 검증 (단일 통합 테스트 파일).

## Phase별 실행 지도

### Phase 1 — 11건 mutation에 X-CSRF-TOKEN 헤더 추가

- `restoreFileVersion`(L285), `softDeleteFile`(L306), `softDeleteFolder`(L318),
  `moveItem`(L344), `renameItem`(L395), `restoreFile`(L966),
  `restoreFolder`(L985), `purgeTrashItem`(L1007), `revokeShare`(L1046),
  `adminToggleCron`(L1421), `shares helper`(L1561), `adminBulkTrash`(L1722).
- 패턴: `const csrf = readCookie('XSRF-TOKEN')` + `headers: { ..., 'X-CSRF-TOKEN': csrf ?? '' }`
  (createFolder hotfix와 동형).
- restoreFile/restoreFolder는 newName conditional spread 패턴 유지하면서 헤더만 추가.

### Phase 2 — 회귀 가드 vitest 추가

- `frontend/src/lib/api.csrfMutations.test.ts` (NEW). 11건 각각 한 케이스씩.
- 각 케이스: fetch mock, document.cookie set, api.<func>(...) 호출,
  fetch headers에 `X-CSRF-TOKEN` 송신 검증.

### Phase 3 — 게이트 + Dev Sync + PR

- `cd frontend && pnpm typecheck && pnpm lint && pnpm test --run api.csrfMutations`.
- `docs/progress.md` 최상단 entry.
- `dev-docs-update`로 plan/context/tasks 갱신.
- 단일 commit + PR open + 백그라운드 자동 머지/archive/cleanup.

## Acceptance Criteria

- [ ] 11건 mutation 모두 fetch headers에 `X-CSRF-TOKEN` 포함 (manual diff 검증)
- [ ] vitest `api.csrfMutations.test.ts`에 11 케이스, 모두 GREEN
- [ ] `pnpm typecheck` exit 0
- [ ] `pnpm lint` exit 0
- [ ] backend wire 호환 0 (코드 무변경)
- [ ] docs/progress.md 최상단 entry 작성

## 검증 게이트

1. `pnpm typecheck` exit 0
2. `pnpm lint` exit 0
3. `pnpm test --run api.csrfMutations` GREEN (11/11)
4. existing `pnpm test --run api.createFolder` 영향 없음 (createFolder 미변경 확인)

## 리스크와 완화

1. **PR #115(createFolder) 충돌**
   - 회피: 본 sweep에서 createFolder 함수 미수정. 머지 시점에 PR #115가
     이미 master에 포함돼 있으면 충돌 0.
   - 만약 PR #115가 dirty로 더 지연되면 sweep PR도 머지 후 createFolder가
     readCookie 패턴 적용 안 된 채 master에 남을 가능성 있음. 그 경우
     별도 후속 PR로 처리 (or PR #115 머지 강제).
2. **익명 endpoint 오인 추가**
   - signup/passwordForgot/passwordReset에 헤더 추가하면 backend가 무시는
     하지만 cookie 없는 사용자가 `readCookie` 호출해도 `null` → spread 시
     헤더 비어있어 부작용 0. 단, KISS 원칙상 면제 endpoint는 그대로 두기.
3. **테스트 파일 비대화**
   - 11 케이스 한 파일에 모으면 ~250줄. KISS 유지하면서 mutation 도메인별
     describe 블록으로 그룹화 (files/folders/trash/share/admin).
