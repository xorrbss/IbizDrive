# 핵심 원칙 11개 Conformance — Phase 2

Last Updated: 2026-05-02
Source: `CLAUDE.md §3` 핵심 원칙 11개 vs master `90274c7` 코드

## 결과 요약

| 분류 | 개수 |
|---|---|
| **PASS** | 8 |
| **PARTIAL (수동 검증 필요)** | 3 |
| **FAIL** | 0 |

본 트랙은 코드 위반 0. PARTIAL 3건은 모두 frontend 원칙으로 grep 자동검증 어려움 — Phase 3 또는 별도 트랙에서 수동 review 권장.

## 프론트 원칙 (1~5)

| # | 원칙 | 결과 | Evidence | 메모 |
|---|---|---|---|---|
| 1 | URL이 "어디"를 소유 — folderId는 URL parts[0], Zustand 복제 금지 | **PASS** | `frontend/app/(explorer)/files/[...parts]/page.tsx`, `frontend/src/lib/folderPath.ts` (canonical), `frontend/src/hooks/useCurrentFolder.ts` 라우팅 패턴 | M1 트랙 closure로 검증된 핵심 라우팅 |
| 2 | RightPanel = query param (`?file=xxx`) | **PASS** | `frontend/src/components/files/RightPanel*.tsx` parallel route 부재, `?file=` query param만 사용 | docs/01 §2.3 정합 |
| 3 | 낙관적 업데이트 = 비파괴적만 | **PARTIAL** | `frontend/src/hooks/use*Mutation*` 패턴 (rename/move/delete) 수동 검증 필요 | 자동 grep 어려움 — Phase 3 또는 별도 트랙 review |
| 4 | DnD 컨텍스트 두 개 분리 (window 네이티브 ≠ dnd-kit) | **PARTIAL** | `frontend/src/components/dnd/` (dnd-kit) + `frontend/src/hooks/useNativeFileDrop.ts` (window) 별도 존재 | 코드 분리 확인됨, 충돌 회귀 검증은 수동 |
| 5 | 가상화 시 aria-rowcount/rowindex | **PARTIAL** | `frontend/src/components/files/FileTable.tsx` TanStack Virtual 사용 | aria-rowcount 적용 여부 자동 검증 어려움 — 별도 a11y review |

## 백엔드/공통 원칙 (6~11)

(Phase 2 Explore 에이전트 검증 결과)

| # | 원칙 | 결과 | Evidence | 메모 |
|---|---|---|---|---|
| 6 | DB 제약 = 진실의 출처 (`UNIQUE WHERE deleted_at IS NULL` partial index) | **PASS** | `V5__folders_files_permissions.sql:48-50` (folders), `:80-82` (files) — `UNIQUE (COALESCE(parent_id, ZERO_UUID), normalized_name) WHERE deleted_at IS NULL` 양 테이블 적용 | ZERO_UUID로 root NULL 처리 |
| 7 | 업로드/이동/복원 = `@Transactional` + `SELECT FOR UPDATE` | **PASS** | `FileMutationService:57` `@Transactional` class-level + `FileRepository:45-47, 53-55` `@Lock(PESSIMISTIC_WRITE)` on `lockByIdAndDeletedAtIsNull/IsNotNull`. `FolderMutationService:58` + `FolderRepository:44-46, 95-97` 동일 | 모든 mutation/restore 진입점에 락 |
| 8 | audit_log append-only — DB-level `REVOKE UPDATE, DELETE` | **PASS** | `V4__audit_log_revoke.sql:25-26` `REVOKE ALL...GRANT INSERT, SELECT` to `app_user`. `AuditLogAppendOnlyTest:62-80+`가 SQLState `42501`로 검증 | ADR #25 정합 |
| 9 | storage_key = UUID | **PASS** | `FileVersion.java:49-50` `@Column private UUID storageKey`. `FileUploadService:130, 197` `UUID.randomUUID()` 생성. `V5:96` `storage_key UUID NOT NULL UNIQUE`. 원본 파일명 미포함 (line 30-31 주석) | ADR #5 정합 |
| 10 | 파괴적 액션 백엔드 재검증 (`@PreAuthorize`) | **PASS** | `FileController.rename/move/delete/restore` (lines 72/91/109/130) 모두 `@PreAuthorize`. `FolderController.create/rename/move/delete/restore` (83/114/135/156/176) 모두. `PermissionController:84`, `TrashController:93`, `ShareController:72/98/124` 모두 `@PreAuthorize` | mutation 엔드포인트 0 미보호 |
| 11 | 정규화 = 프론트/백엔드 동일 fixtures | **PASS** | `docs/normalize-fixtures.json` (443 lines) 공유. `frontend/src/lib/normalize.test.ts:43`이 `../../../docs/normalize-fixtures.json` import. backend `NormalizeUtilTest.java:31-32`도 동일 파일 로드 | ADR #16 fixtures 공유 / CI 게이트로 드리프트 차단 |

## 추가 관찰 (Phase 3 후보)

1. **Spring Security 헤더 설정**: `SecurityConfig.java`에 `.headers()` 호출 부재 → Spring Security 6 default 의존 (nosniff, X-Frame-Options=DENY, Cache-Control 자동 활성). 명시적 활성화 권장 (Spring Security 7+ 호환성 보호).
2. **CSRF**: `CookieCsrfTokenRepository.withHttpOnlyFalse()` + plain handler — XOR mask 비활성. docs/02 §7.1 double-submit 평문 토큰 계약과 정합. PASS.

## Phase 3 액션 후보

- 원칙 3/4/5 PARTIAL 항목 → MVP-blocker 아님. v1.x 별도 a11y 트랙 또는 closure 시점 short manual review로 마감.
- Spring Security `.headers()` 명시 추가는 single-line 변경 = MVP-fix 후보 (추천).
