---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — closure-only. M12 핵심 wiring은 A2.6에서 이미 완료됨.
---

# M12 — Audit Log UI closure

## 요약

사용자 요청은 "M12 (Audit Log UI) — /admin/audit-logs 구현, 백엔드 endpoint 신설 필요할 수 있음(GET /api/admin/audit-logs)"이지만, 베이스라인 조사 결과 **두 항목 모두 이미 구현 완료** 상태:

- 백엔드: `AuditQueryController` `@RequestMapping("/api/admin/audit")` 가동 (필터/페이지네이션/role-scope 분기)
- 프론트: `frontend/src/app/admin/audit/logs/page.tsx` + `useAuditLogs` + `api.getAuditLogs` 실 fetch (A2.6에서 mock → 실 API 교체 완료, `api.audit.test.ts`에서 보장)

본 트랙은 **닫는 작업**만 수행:
1. `page.tsx:18-19` stale 주석("백엔드 연결 없음. mock 사용") 정리
2. M12 milestone closure 표기 — `docs/progress.md` 또는 `docs/04-admin-operations.md §7`
3. 백엔드 smoke 검증 (수동 또는 e2e — 실 DB + audit row 1건 → page.tsx 렌더 확인)

CSV 서버사이드 export + `audit.exported` runtime emission은 `docs/progress.md:927`에 명시적 v1.x deferred → 본 트랙 out-of-scope, backlog 그대로.

## 현재 상태 분석

### 이미 존재 (master `11349bf`)

- 백엔드: `AuditQueryController.GET /api/admin/audit` — `fromDate, toDate, actorQuery, eventType, page, pageSize` 6 필터 + role-scope (ADMIN/AUDITOR 전체, MEMBER 본인) — `AuditLogPageDto` 반환
- 프론트: `api.getAuditLogs(filters, page, pageSize)` — fetch `/api/admin/audit?...`, 응답 매핑(`actorId/actorName=null → 'system'` 폴백) — `api.audit.test.ts`에서 401/403/200 케이스 보장
- 페이지: `app/admin/audit/logs/page.tsx` — 필터/테이블/페이지네이션/CSV(client-side, current-page) 작동
- 사이드바: `app/admin/layout.tsx` `/admin/audit/logs` 링크 존재

### 부재 / gap

- `page.tsx:18-19` stale 주석 — "백엔드 연결 없음. api.getAuditLogs는 클라이언트 mock 데이터 사용"
- M12 milestone closure 표기 (progress.md 본문 또는 docs/04 §7 status 행)
- 실제 backend가 떠있는 환경에서 e2e/smoke 검증 (이번 트랙은 수동 확인 권장 — Playwright e2e는 별도 인프라 필요)

### v1.x deferred (이 트랙 out-of-scope)

- `audit.exported` runtime emission — CSV/JSON export endpoint 자체가 v1.x (`docs/progress.md:927`)
- 서버사이드 전체 결과 스트리밍 export — 현재 client-side current-page만

## 목표 상태

### 변경 파일

- `frontend/src/app/admin/audit/logs/page.tsx` — 라인 14-20 docblock의 "백엔드 연결 없음 / mock" 표현을 "백엔드 GET /api/admin/audit 연결 (A2.6)" + "CSV는 client-side current-page (서버 전체 export는 v1.x)"로 정정
- `docs/progress.md` — M12 closure 세션 기록 추가
- `docs/04-admin-operations.md §7` — backend wired 표기 (현재 status 어떻게 되어있는지 확인 후)

### 검증

- 수동: backend 기동 후 (`./gradlew :backend:bootRun`) → frontend `pnpm dev` → `/admin/audit/logs` 진입 + 더미 audit row 생성(예: login) 후 페이지 노출 확인
- 자동: 기존 `api.audit.test.ts` + `useAuditLogs.test.tsx` GREEN 유지

## phase 실행 지도

| Phase | Title | 산출물 |
|---|---|---|
| M12C.0 | dev-docs bootstrap | plan/context/tasks |
| M12C.1 | page.tsx 주석 정정 + 회귀 검증 | docblock patch + `pnpm test` GREEN |
| M12C.2 | docs closure 표기 (progress.md + docs/04 §7) | docs patches |
| M12C.3 | smoke (수동) + PR + master merge + archive | manual smoke note + PR |

## acceptance criteria

- [ ] `page.tsx` docblock에 stale "mock" 표현 부재, 실제 wiring 명시
- [ ] `docs/progress.md`에 M12 closure 라인 (날짜 + "M12 closure" + 핵심 사실)
- [ ] `docs/04-admin-operations.md §7` status가 backend-wired 반영 (필요 시 표기)
- [ ] frontend `pnpm test` GREEN (회귀 0)
- [ ] frontend `pnpm typecheck && pnpm lint` GREEN
- [ ] M12 backlog 항목 중 v1.x deferred(`audit.exported` runtime, server-side export)는 이 트랙 out-of-scope임을 docs에 명확히 보존

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + 사용자 OK | M12C.1 진입 |
| 1 | M12C.1 page.tsx patch + frontend tests GREEN | M12C.2 진입 |
| 2 | M12C.2 docs patch | M12C.3 진입 |
| 3 | smoke note + PR + 사용자 OK + master merge | archive + progress.md |

## 리스크와 완화

1. **e2e smoke 환경 부재** — 본 세션에서 backend 실 부팅이 어렵다면 manual smoke는 사용자 수동 검증 의뢰. plan에 명시.
2. **scope creep — server-side export 끌어들이기 유혹** — v1.x 명시 deferred. 본 트랙은 closure만. 새 backlog 항목은 별도 트랙으로.
3. **page.tsx 주석 외 코드 변경 0이면 PR 의미 작음** — closure 자체에 가치(추적/문서 정합). docs/progress.md 변경이 핵심 가치.

## 비-목표 (out-of-scope)

- ~~서버사이드 CSV/JSON export endpoint~~ — v1.x deferred 그대로
- ~~`audit.exported` runtime audit emission~~ — v1.x deferred 그대로
- ~~Playwright e2e 추가~~ — 별도 트랙
- ~~필터 UX 개선/검색 자동완성~~ — backlog

## 다음 세션 읽기 순서

1. 이 plan
2. `m12-audit-ui-closure-context.md`
3. `m12-audit-ui-closure-tasks.md`
4. `frontend/src/app/admin/audit/logs/page.tsx` (15-20행 주석)
5. `frontend/src/lib/api.ts:493-553` (실제 wiring 확인용)
6. `docs/progress.md` (M12 행 위치 — grep "M12")
7. `docs/04-admin-operations.md §7` (audit logs 섹션)
