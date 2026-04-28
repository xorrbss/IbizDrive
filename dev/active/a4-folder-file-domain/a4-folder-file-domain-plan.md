---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP COMMITTED — A4-data 트랙 진입 대기 (게이트 1)
---

# A4 — Folder/File Domain & Resource-Level Permissions — Plan

## 요약

A4 마일스톤은 (1) `folders`/`files`/`file_versions`/`permissions` 도메인 테이블과 도메인 모델을 도입하고, (2) `IbizDrivePermissionEvaluator`의 평가 로직을 user-level(role 기반)에서 **resource-level(상속 + 명시 grant)** 로 교체하며, (3) ADR #26에서 A4로 이월된 `permission.granted/revoked` 실 emission 호출처를 제공한다. SpEL `hasPermission(...)` 호출 시그니처와 A3 audit append-only 정책은 그대로 보존한다.

## 단위 분할 — **PR 2개 (ADR #27)**

A4는 **A4-data PR + A4-controllers PR** 두 트랙으로 분할한다. 의존 단방향: A4-controllers는 A4-data master 머지 후 새 worktree에서 분기.

### 트랙 A4-data PR (A4.0 → A4.1 → A4.2)

- **A4.0** docs 정합 + ADR #27/#28/#29 (no-code, **본 bootstrap 세션에서 ADR 부분 commit** — A4.0 docs 정합 패치는 별도 세션)
- **A4.1** V5 마이그레이션 (folders + files + file_versions + permissions)
- **A4.2** 4 entity + 4 repository + NormalizeUtil + frontend mirror
- **PR 머지 조건**: schema GREEN + repository unit test GREEN + audit_log REVOKE 회귀 0
- **추정 commits**: 5~7

### 트랙 A4-controllers PR (A4.3 → A4.4 → A4.5 → A4.6 → A4.7)

- **A4.3** IbizDrivePermissionEvaluator 내부 교체 (resource-level + 상속)
- **A4.4** 권한 4 endpoint + permission.granted/revoked emit (ADR #26 close)
- **A4.5** folder 7 + file 5 endpoint, TX + FOR UPDATE
- **A4.6** 통합 E2E + A2/A3 회귀 가드
- **A4.7** closure (PR + active→completed archive)
- **PR 머지 조건**: A4-data 머지 완료 + 모든 신규/회귀 테스트 GREEN
- **추정 commits**: 8~12

### 분할 근거

- 단일 마일스톤 13~19 commits → 단일 PR 리뷰 부담 ↑
- A4-data는 schema + repository unit test로 **독립 검증 가능** (controller 회귀 영향 없음)
- A4-controllers는 머지된 A4-data 위에서 분기 → **회귀 추적 용이**
- A2/A3 단일 PR 패턴과의 차이는 A4 규모를 반영한 **의식적 결정** (ADR #27 명시)

## 현재 상태 분석

### 종착점 (origin/master 6f0820d 기준)

- A1 인증 ✅ / A2 audit backbone ✅ / A3 권한 매트릭스 (user-level MVP) ✅
- `Permission` 9-value enum + `Preset` 5-value enum + `PermissionService.check()` 단일 진입점 (`backend/src/main/java/com/ibizdrive/permission/`)
- `IbizDrivePermissionEvaluator`는 SpEL hook만 도입된 상태 — 내부는 role 기반 (ADMIN=all, AUDITOR=READ, MEMBER=deny)
- `effectivePermissionsCacheKey` SHA-256 hex prefix 16자 — opaque trigger
- `permission.changed` audit emission만 활성, `permission.granted/revoked`는 enum 등록만 됨 (호출처 부재 — ADR #26 deferred)
- `audit_log` REVOKE UPDATE/DELETE V4 마이그레이션 적용 — 절대 깨면 안 됨
- folders/files 테이블, 도메인, controller, repository — **부재**

### docs 정합 상태 (입력 근거)

- `docs/02 §2.3 folders` — `parent_id UUID` only (LTREE 컬럼 없음). slug/audit_level/original_parent_id 포함.
- `docs/02 §2.4 files` — `folder_id UUID` + `current_version_id` (deferred FK), `normalized_name`, soft delete + purge_after.
- `docs/02 §2.5 file_versions` — `storage_key UUID UNIQUE` (S3 key는 UUID, 원본 파일명 절대 미저장). **A4에서는 테이블만 도입, entity/repo/CRUD는 A5 이월 (ADR #29)**.
- `docs/02 §2.6 permissions` — `preset` 단일 컬럼 (`read|upload|edit|admin`), allow/deny 컬럼 **없음**. **A4 채택 = preset 단일 유지, 명시 deny semantics v1.x 이월 (ADR #28)**.
- `docs/02 §6` — 모든 mutation은 `BEGIN; SELECT ... FOR UPDATE; ... COMMIT;` 패턴, audit insert는 `REQUIRES_NEW` 분리 (ADR #24).
- `docs/02 §7.5~§7.10` — folder 7 endpoint, file 11 endpoint(메타/version), permission 4 endpoint.
- `docs/03 §3.4` — 상속 = 부모→자식, override = 자식 명시. **본 세션에서 "(v1 deferred — ADR #28)" 마커 1줄 추가됨.**
- `docs/03 §3.6` — 403 envelope `{ code: PERMISSION_DENIED, details: { required, have } }` (A3에서 활성).
- `docs/00 §5 ADR #26` — A4 진입 트리거 (closure는 A4.4 commit hash로 갱신).
- `docs/00 §5 ADR #27/#28/#29` — **본 bootstrap 세션에서 등록 완료**.

### backup branch (backup/codex-d99cd2a) 검토 결과 — read-only 참고

- `V5__folders_files.sql` — folders/files/file_versions 3테이블만, **permissions 테이블 누락** (A4.1에서 보강).
- folders는 docs/02 §2.3와 1:1 정합. UNIQUE 인덱스에 `COALESCE(parent_id, '00000000-...')` 보강 (root folder 다중 처리). **채택**.
- LTREE 미사용. 사용자 prompt의 "LTREE 채택" 가정은 **docs/02 §2.3과 어긋남** — A4.0 결정에서 LTREE 미도입으로 확정.
- Folder.java / FileItem.java entity는 plan 단계에선 미열람 (구조 참조는 A4.2에서 검증 후 채택). 패키지는 `com.ibizdrive.folder` / `com.ibizdrive.file` 채택 — codex의 `com.ibizdrive.security.Permission` 절대 채택 금지.

## 목표 상태 (A4 종료 시점)

1. V5 마이그레이션이 origin/master에 머지되어 `folders`/`files`/`file_versions`/`permissions` 테이블 + 모든 UNIQUE/CHECK 제약 + REVOKE 정책 무충돌 보증.
2. `Folder`/`FileItem`/`PermissionRow` entity + Spring Data JPA repository + 정규화 함수 (frontend 1:1 미러). **`FileVersion` entity는 A5 이월 (ADR #29)**.
3. `IbizDrivePermissionEvaluator` 내부가 재귀 CTE 기반 resource-level 평가 — `ADMIN`은 SpEL bypass(role 우선) 보존, 그 외는 명시 grant + 폴더 상속.
4. `POST /api/:resource/:id/permissions` + `DELETE /api/permissions/:permissionId` endpoint 활성, 각각 `permission.granted`/`permission.revoked` audit emit (ADR #26 close).
5. 폴더/파일 CRUD MVP (생성/이름변경/이동/삭제/복원) endpoint + controller + service + transaction (FOR UPDATE) — `docs/02 §6.3~§6.5, §7.5~§7.6`.
6. A2/A3 회귀 가드 통과: audit append-only 깨지지 않음, A3 user-level 평가가 ADMIN bypass로 동등 동작 보존, `effectivePermissionsCacheKey` 형식 보존.
7. PR 2개(A4-data + A4-controllers) 머지 + active→completed archive 완료.

> **out of scope (A5+ 또는 별도 트랙)**: tus 업로드 (`§7.7`) — A5; 검색 (`§7.8`) — A5; 공유 (`§7.9`) — A5; 휴지통 페이지 (`§7.11`) — A7; 부서 LTREE (`§2.2`) — A1.5 후속; resource-level cache store (`v1.x`); 권한 endpoint frontend UI — 별도 worktree; **`file_versions` entity/CRUD — A5 (ADR #29)**; **명시 deny semantics — v1.x (ADR #28)**.

## permissions semantics (ADR #28)

- v1 evaluator = explicit grant lookup. `permissions` 테이블에서 `(subject, resource)` 매칭 → preset → permission set 매핑 → 요청 permission 포함되면 true. 자식에 명시 grant 없으면 부모로 거슬러 올라가 동일 lookup. 어디서도 grant 없으면 false.
- "deny 우선"이 아니라 "**grant 우선**" — `docs/03 §3.4`에 `(v1 deferred — ADR #28)` 마커 추가됨.
- v1.x 도입 경로: V6+ 마이그레이션으로 `permissions.deny BOOLEAN` 컬럼 또는 별도 `denies` 테이블 추가 — 현 schema는 이를 막지 않음 (subject_type/resource_type/preset 컬럼 그대로 재사용).

## file_versions A5 이월 (ADR #29)

- V5 schema에는 `file_versions` 테이블 + DEFERRABLE FK `files.current_version_id` **포함** (backup V5와 동일).
- A4에서는 entity / repository / CRUD endpoint **미작성** — A5에서 추가.
- **V5 schema 보장사항** (A5에서 추가 마이그레이션 없이 작동해야 함):
  - (a) `files.id`는 mutation/restore 시 stable. CRUD가 행 재발급하지 않음.
  - (b) `storage_key` 교체 시 `file_versions` 행 추가 가능 — `version_number` UNIQUE per file_id 제약은 V5에서 적용.
  - (c) `current_version_id` NULL 허용 그대로 유지 — A4 MVP는 최신 버전 단일 가정, A5에서 NOT NULL 강제 검토.
- A5 부트스트랩 시점에 codex backup `FileVersion.java`를 reference로 재검토. **A4에서는 cherry-pick 금지**.

## Phase 분할

> 각 phase의 자세한 작업 항목/참조 블록/AC는 `tasks.md` 트랙별 헤더 참조.

### A4-data 트랙

- **A4.0** docs 정합 + ADR (no-code) — **본 세션에서 ADR #27/#28/#29 commit 완료**, docs/02 정합 patch는 별도 세션 (A4-data 진입점)
- **A4.1** V5 마이그레이션 — folders + files + file_versions + permissions, root UNIQUE COALESCE 보강
- **A4.2** entity + repository + NormalizeUtil + frontend mirror

### A4-controllers 트랙

- **A4.3** IbizDrivePermissionEvaluator 내부 교체 — SpEL 시그니처 보존, A3 13/13 회귀 0
- **A4.4** 권한 4 endpoint + permission.granted/revoked emit — ADR #26 close
- **A4.5** folder 7 + file 5 endpoint — TX + FOR UPDATE
- **A4.6** 통합 E2E + A2/A3 회귀 가드
- **A4.7** closure (PR + archive)

## 게이트 (사용자 보고 지점)

- **게이트 1 (A4.0 직후 / A4-data 진입 직전)**: ADR commit 완료 보고 — **본 세션에서 보고 후 종료**
- **게이트 2 (A4-data PR 머지 직후 / A4-controllers 진입 직전)**: V5 + entity GREEN, A2 회귀 0 보고
- **게이트 3 (A4.6 직후 / A4-controllers PR 생성 직전)**: A4 신규 + A2/A3 회귀 GREEN, DoD 11/11 보고

## 추정 커밋 수

| 트랙 | Phase | commits |
|---|---|---|
| bootstrap | ADR + dev-docs | **1** (본 세션) |
| A4-data | A4.0 docs 정합 patch | 1 |
| A4-data | A4.1 V5 + RED→GREEN IT | 1~2 |
| A4-data | A4.2 entity/repo/normalize/mirror | 2~3 |
| A4-data | PR + closure (active 부분 갱신만) | 1 |
| **A4-data 합계** | | **5~7** |
| A4-controllers | A4.3 evaluator 교체 + 회귀 | 1~2 |
| A4-controllers | A4.4 endpoint + emit | 2 |
| A4-controllers | A4.5 folder/file CRUD | 3~5 |
| A4-controllers | A4.6 E2E + 회귀 가드 | 1~2 |
| A4-controllers | A4.7 closure (PR + archive) | 1~2 |
| **A4-controllers 합계** | | **8~13** |
| **A4 전체** | | **14~21** |

## 리스크와 완화

- **R1: backup branch 오염** — codex의 `com.ibizdrive.security.Permission` 절대 채택 금지. master `com.ibizdrive.permission` 단일 진실. A4.2/A4.5 commit 시 path diff 검증.
- **R2: docs/03 §3.4 semantics 잔류 모호성** — 본 세션에서 "(v1 deferred — ADR #28)" 마커 추가. 다음 세션에서 docs/03 §3.4 본문을 더 명확히 다시 쓸 필요는 없음 (마커만으로 ADR #28 backlink 충분).
- **R3: A3 user-level 회귀** — A4.3 AC에 13/13 회귀 명시. 깨지면 게이트 2(A4-data 머지 후 A4-controllers 진입 시점)에서 복귀.
- **R4: audit append-only 우회** — ADR #24 패턴 재사용, A4.6 회귀 가드 명시.
- **R5: 범위 폭주** — tus / 검색 / 공유 / 휴지통 UI / 권한 frontend UI / file_versions CRUD 모두 out-of-scope (ADR #29).
- **R6: storage(S3) 결합** — A4 MVP는 file 메타데이터만, S3 업로드는 A5. `current_version_id` NULL 허용 유지.
- **R7: PR 분할로 인한 dev/process ownership 충돌 재확인 필요** — A4-data 머지 후 master의 다른 active 세션(`20260428-a3-permission-a4`, `20260428-a3-folder-mutation-service`)이 정리되었는지 게이트 2에서 재검사.

## Accepted-deviation 후보

- `permission.granted/revoked` audit `details.preset` 포함 여부 — A4.4에서 결정.
- folder tree endpoint lazy 미구현 (full subtree 반환) — `docs/02 §9.2` 권고 위반. A4 MVP 한정.
- (확정 deviation) **`file_versions` entity/CRUD A5 이월 — ADR #29**.
- (확정 deviation) **명시 deny semantics v1.x 이월 — ADR #28**.

## DoD 체크리스트 (A4 전체)

1. `V5__folders_files_permissions.sql` master 머지 + RED→GREEN 통합 테스트 GREEN
2. 3 entity (Folder/FileItem/PermissionRow) + 3 JpaRepository + NormalizeUtil + frontend mirror fixtures 통과 *(`FileVersion` entity 제외 — ADR #29)*
3. `IbizDrivePermissionEvaluator` 내부 resource-level 교체 + A3 회귀 0
4. 4 permission endpoint + grant/revoke audit emit GREEN + ADR #26 close 표기
5. folder 7 endpoint + file 5 endpoint(메타) + 트랜잭션 FOR UPDATE 전부 적용
6. audit_log REVOKE 정책 무충돌 (`SQLState 42501` 회귀 가드 유지)
7. PURGE × hasPermission 가드 유지 (ROLE only)
8. `effectivePermissionsCacheKey` 형식 보존
9. `docs/02 §2.3/§2.6/§7.10` + `docs/03 §3.4` 정합 + `docs/00 §5 ADR #27/#28/#29` 등록 *(본 세션에서 ADR + §3.4 마커 완료)*
10. backend / frontend test count + 0 failures 보고
11. A4-data PR + A4-controllers PR 둘 다 머지 + active→completed archive + dev/process 파일 정리
