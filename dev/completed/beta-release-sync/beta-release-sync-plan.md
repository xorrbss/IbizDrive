---
Last Updated: 2026-05-05
---

# Plan — beta-release-sync

## 요약

`BETA-RELEASE.md`를 master HEAD(5f143c6) 기준 실제 코드 상태로 정렬하는 docs-only 트랙. 2026-05-02 last-updated 이후 머지된 5건(#48 auth-must-change-pw, #50 auth-forgot-rate-limit, #54 m-admin-entry-rewrite, #55 auth-password-policy, #53 email-async)을 베타 GO/NO-GO 체크리스트에 반영하고, 누락된 dev/process 스테일 파일을 정리한다.

## 현재 상태 분석

`master` 5f143c6 기준 drift:

**`BETA-RELEASE.md` header**
- `Last Updated: 2026-05-02` — 5건 closure 누락
- Source 라인 — auth-must-change-pw / auth-forgot-rate-limit / m-admin-entry-rewrite / auth-password-policy / email-async 미인용

**§1 코드 베이스 게이트**
- `frontend test GREEN | 647/647` — 실제 738/738 (auth-password-policy closure 결과). drift 91 케이스
- backend test "auth-pages signup TDD +12 tests 포함" — 이후 다수 트랙으로 추가, 단순 표기 정정 또는 풀세트 표기로 갱신

**§5 인증 / 세션** — 추가 행 필요
- 비밀번호 정책 = ADR #19 본문(12자 + 영·숫 + 공백 금지) 회복 (auth-password-policy, ADR #19/#41 closure)
- mustChangePassword UX enforcement (auth-must-change-pw, ADR #21 §2.7)
- 운영자 초대 endpoint `POST /api/admin/users` (m-admin-entry-rewrite, ADR #21 closure)
- 이메일 비동기 발송 `@Async` (email-async, ADR #45) — anti-enumeration timing leak 완화

**§6 감사 / 권한**
- audit emit coverage `42 enum 중 29 emit (69%)` — 실제 32/42 (76%): a1.5 closure +2(USER_PASSWORD_FORGOT_REQUESTED, USER_PASSWORD_RESET) + m-admin-entry-rewrite +1(ADMIN_USER_CREATED). progress.md m-admin-entry-rewrite 결정 #7에 명시.

**§7 v1.x deferred**
- "admin frontend ... audit logs UI(M12)만 활성, 나머지 v1.x" — m-admin-entry-rewrite로 `/admin` shell + `/admin/users` 활성. 표현 정정.

**dev/process 스테일 파일 3개**
- `a1.5-email-infra.md` (status: closed 2026-05-02)
- `auth-forgot-rate-limit.md` (status: closed 2026-05-03)
- `email-async.md` (status: closed 2026-05-03)

dev 스킬 ⓪ 규칙: closure 시 삭제 의무. 각 트랙 closure에서 누락된 housekeeping.

## 목표 상태

- `BETA-RELEASE.md` last-updated = 2026-05-05, Source 라인이 5건 closure 인용.
- §1 frontend 738/738 정정.
- §5에 4행 추가 (password policy / mustChangePassword / admin invite / email async).
- §6 audit emit coverage = 32/42 (76%).
- §7 admin frontend 표현이 "초대 폼 + audit logs UI 활성, 사용자 목록/role 변경 v1.x"로 정정.
- `dev/process/{a1.5-email-infra,auth-forgot-rate-limit,email-async}.md` 삭제.
- `docs/progress.md` 본 트랙 closure entry 추가.

## Phase별 실행 지도

### P1 — `BETA-RELEASE.md` sync

- header (Last Updated + Source) 갱신
- §1 frontend 카운트 정정
- §5 4행 추가
- §6 audit emit coverage 32/42 (76%)로 정정 + 신규 emit cross-link
- §7 admin frontend 표현 정정
- 검증: `grep -n "2026-05-02\|29 emit\|647/647\|audit logs UI(M12)만 활성" BETA-RELEASE.md`로 잔존 0 확인

### P2 — `dev/process/` 스테일 파일 삭제

- 3개 파일 삭제 (status: closed 명시 파일만)
- 검증: `ls dev/process/`가 비어있음 확인

### P3 — closure (progress.md + dev-docs archive + PR)

- `docs/progress.md` 본 entry 최상단 추가
- `dev/active/beta-release-sync/` → `dev/completed/beta-release-sync/`
- self-review → push → PR

## Acceptance criteria

- [ ] `BETA-RELEASE.md`에 "2026-05-02", "29 emit", "647/647", "audit logs UI(M12)만 활성" 표현 잔존 0
- [ ] §5에 password policy / mustChangePassword / admin invite / email async 4행 신규
- [ ] §6 audit emit coverage = 32/42 (76%)
- [ ] `dev/process/` 디렉터리 비어있음 (또는 본 세션 ownership 파일 1개만)
- [ ] `docs/progress.md` 최상단에 본 트랙 closure entry
- [ ] PR open, CI GREEN

## 검증 게이트

본 트랙은 docs-only이므로 `pnpm test`/`./gradlew test`는 회귀 안전 (코드 0 변경). 단, PR push 후 CI(frontend vitest + backend junit) GREEN 자체는 게이트로 유지.

## 리스크와 완화 전략

- **리스크**: BETA-RELEASE.md 갱신 중 다른 신규 트랙이 Source 라인에 추가됨 → 머지 충돌. **완화**: 본 트랙은 docs/{progress.md, BETA-RELEASE.md} 외 코드 0. 신규 트랙은 일반적으로 progress.md만 수정 → conflict는 단순 추가 합치로 해결.
- **리스크**: dev/process 파일 삭제 후 재발 (다른 트랙에서 재발생). **완화**: 본 트랙 closure 시 본 세션 ownership 파일도 함께 삭제. 향후 트랙은 dev 스킬 ⓪ 규칙 재확인 의존.
