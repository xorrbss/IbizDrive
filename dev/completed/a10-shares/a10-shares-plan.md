---
Last Updated: 2026-05-01
Status: ✅ CLOSED — PR #21 squash-merge `24a78b2`, dev-docs archived
---

# A10 — Shares Endpoint Backend — Plan

## 요약

A9 closure(`f9200dc` PR #20 머지 + `9875fe9` F1 closure) 다음 백엔드 트랙. **공유(shares) endpoint 4건**(docs/02 §7.9)을
backend에 신설한다. 기존 `permissions` 테이블·`PermissionService.grantPermission/revokePermission` 자산(A4.4)을 재사용하고,
`shares` 테이블만 신규 도입한다. AuditEventType의 `SHARE_CREATED`/`SHARE_REVOKED`/`SHARE_EXPIRED` enum (이미 정의됨,
사용처 0)을 본 트랙에서 처음 활성화한다.

## 단위 분할 — 단일 PR (A2/A3/A5/A6/A7/A8/A9 패턴)

추정 6~9 commits. KISS — 단일 PR.

- **A10.0** ADR #34 + docs/02 §2.7/§7.9 정합화 + docs/03 §3 backlink — **no-code**
- **A10.1** V6 마이그레이션(`shares` 테이블 + `expires_at` 컬럼) + `Share` entity + `ShareRepository` (Testcontainers 1건 — V6 schema 검증; 그 외 단위는 A8/A9처럼 Mockito 위주)
- **A10.2** `ShareDto` + `ShareCreateRequest` + `ShareCommandService.createShares` (1 request → N subjects → N permissions + N shares + N audit) + Mockito 단위 테스트
- **A10.3** `ShareCommandService.revokeShare` (soft-revoke `revoked_at` + permission row delete + `SHARE_REVOKED` audit emit) + Mockito 단위 테스트
- **A10.4** `ShareQueryService` (by-me / with-me) + cursor 페이지네이션 + READ 후처리 + Mockito 단위 테스트
- **A10.5** `ShareController` (4 endpoints) + `@PreAuthorize` (`SHARE` for create, `share.owner OR ADMIN` for revoke, `isAuthenticated` for query) + 400/403/404 + Mockito 통합 테스트
- **A10.6** closure (PR + dev-docs archive)

**out-of-scope (별도 트랙)**:
- ~~외부(이메일/링크 토큰) 공유~~ — docs/03 §3.3 subject types(`user`/`department`/`role`/`everyone`)만 채택. 외부 토큰 다운로드는 별도 ADR + storage 모듈 도입 후 트랙.
- ~~`SHARE_EXPIRED` 자동 발행 cron~~ — `expires_at` 도과 row를 `revoked_at` 로 자동 전환하는 배치는 별도 트랙(A7 `purge.expired` 패턴 미러). enum은 정의만 활성화하되 emit 호출처는 미구현.
- ~~Frontend 공유 UI~~ — docs/01 §6/§14 미명시. backend stack only. M11 검색·M9 휴지통과 동일하게 mock→real 후속 PR.
- ~~bulk revoke (`DELETE /api/shares?fileId=`)~~ — 운영 요구 발생 시 별도 트랙.
- ~~SSE `share.created/revoked` emission~~ — A6/A7/A8과 동일하게 SSE 인프라 milestone deferred.
- ~~`POST /api/folders/:id/share` (folder 공유)~~ — docs/02 §7.9 표는 `POST /api/files/:id/share` 단일. shares 테이블은 file_id/folder_id 양립이지만 endpoint MVP는 file 한정. folder 공유는 ADR #34에 backlog 박제.

## 현재 상태 분석

### master HEAD `9875fe9` (F1 closure) 자산

- **`permissions` 테이블 + `PermissionRow` entity + `PermissionRepository` + `PermissionService.grantPermission/revokePermission`** (A4.4 도입). INSERT 시 V5 `idx_permissions_unique` 위반은 `PermissionConflictException` 변환.
- **`PermissionGrantedEvent` + `PermissionRevokedEvent` + `PermissionAuditListener`** (REQUIRES_NEW 트랜잭션 분리, ADR #24/#25). 본 트랙은 `permission.granted`/`permission.revoked` audit는 그대로 받고, **추가로** `share.created`/`share.revoked` audit를 별도 emit.
- **`AuditEventType`** (`backend/.../audit/AuditEventType.java`):
  - line 48 `SHARE_CREATED("share.created")` — **enum 정의됨, 사용처 0** (A10 활성화 대상)
  - line 49 `SHARE_REVOKED("share.revoked")` — **enum 정의됨, 사용처 0**
  - line 50 `SHARE_EXPIRED("share.expired")` — **enum 정의됨, 사용처 0** (배치 트랙 deferred)
- **`AuditTargetType`** — `target_type='share'`는 V3 CHECK에 이미 포함 (line 26). enum 매핑 점검만.
- **`Preset.java`** — 5 preset (`read`/`upload`/`edit`/`share`/`admin`). docs/02 line 1126 ADR #28 본문 "preset 4값(share 제외)"과 drift 존재. **A10.0에서 정합화 필요**: 코드 = 5 preset이 진실의 출처(원칙 6) → docs ADR #28 미세 정정 또는 ADR #34에서 명시.
- **Cursor 패턴** — A8 `TrashCursor` (`{deletedAt}|{id}` base64) / A9 `SearchCursor` (`{updatedAt}|{type}|{id}` base64). A10은 `{createdAt}|{id}` base64로 채택.
- **Mockito 단위 테스트 위주** — A8/A9 패턴 일관. Testcontainers는 마이그레이션 V6 schema 검증 1건만 한정 (V1~V5 IT가 이미 cascade/FK 검증 패턴 보유).

### docs gap (A10.0 정합화 대상)

| 항목 | 현재 | 정정 |
|---|---|---|
| docs/02 §2.7 `shares` 테이블 | `expires_at` 컬럼 누락 | V6에서 컬럼 추가 + §2.7 SQL 갱신 (or §7.9 spec에서 expiresAt drop) — 결정: **컬럼 추가** (frontend permission expiresAt UX 패리티) |
| docs/02 §7.9 POST request `preset: 'VIEW'\|'EDIT'\|'FULL'` | 코드 Preset.java = `read\|upload\|edit\|share\|admin` | wire format을 lower-case 5종으로 정정 (CLAUDE.md §3 원칙 6 — 코드 진실의 출처) |
| docs/02 §7.9 POST request `subjects: [{type: 'USER'\|'DEPT'}]` | PermissionService = `user\|department\|role\|everyone` | 4종 모두 채택 + lower-case 정정. (`role`/`everyone` 도입 비용 0 — 동일 PermissionService 진입) |
| docs/02 §7.9 표 row 4: `share.owner OR ADMIN` SpEL 표현 | abstract | `@PreAuthorize("@shareCommandService.canRevoke(#shareId, principal)")` 또는 `share.shared_by == authentication.principal.id or hasRole('ADMIN')` 결정 — A10.0에 명시 |
| docs/03 §3 SHARE permission 사용처 | enum 정의만 | §3.5 endpoint 표 row 추가 (POST `/api/files/:id/share` → SHARE). docs/02 §7.9는 이미 명시 — backlink만 추가 |
| ADR #28 본문 "share 는 별도 shares 테이블" | 코드 5-preset과 미세 drift | ADR #34 보강: shares 테이블이 share row를 owning, **permission row는 read/upload/edit/admin/share 5 preset 중 매핑된 1건** + share row가 `permission_id` 참조. SHARE preset (read+upload+edit+move+download+delete+share) → "공유 받은 사람"의 권한 셋 |

## 목표 상태 (DoD)

1. ✅ ADR #34 + docs/02 §2.7/§7.9 정합 + docs/03 §3 backlink
2. ✅ V6 마이그레이션(`shares` + `expires_at`) + `Share` entity + `ShareRepository`
3. ✅ `POST /api/files/:id/share` 201 — `{ shares: ShareDto[] }` + N개 permissions/shares/audit
4. ✅ `DELETE /api/shares/:shareId` 204 — `revoked_at` set + permission row delete + `SHARE_REVOKED` audit
5. ✅ `GET /api/shares/by-me` + `GET /api/shares/with-me` 200 — cursor 페이지네이션 + 비revoked 필터
6. ✅ `SHARE_CREATED` / `SHARE_REVOKED` audit 실 발행 (A10 첫 사용)
7. ✅ Mockito 단위 + 통합 테스트 ≥15건 GREEN — V6 schema(Testcontainers 1) / Share entity round-trip / create N subjects / revoke / by-me / with-me / 권한 거부 / 비존재 404
8. ✅ A1~A9 회귀 0
9. ✅ PR 1개 squash-merge + dev-docs `dev/active/a10-shares/` → `dev/completed/`

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + worktree `a10-shares` 생성 + 사용자 OK | A10.0 진입 |
| 1 | A10.0 docs commit (ADR #34 + §2.7/§7.9 patch + §3 backlink) | A10.1 진입 |
| 2 | A10.1 V6 + Share entity GREEN + 회귀 0 | A10.2 진입 |
| 3 | A10.2 createShares GREEN + 회귀 0 | A10.3 진입 |
| 4 | A10.3 revokeShare GREEN + 회귀 0 | A10.4 진입 |
| 5 | A10.4 ShareQueryService GREEN + 회귀 0 | A10.5 진입 |
| 6 | A10.5 ShareController GREEN + full suite GREEN | **사용자 PR 승인 게이트** |
| 7 | PR 생성 + 사용자 OK + CI green + master squash-merge | A10.6 archive |

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| 1 request → N permissions/shares insert 트랜잭션 일부 실패 | 단일 트랜잭션(REQUIRED) + UNIQUE 충돌은 `PermissionConflictException` → 409 enveloped — 부분 성공 금지(전체 rollback) |
| `permission_id` FK + share row → permission delete cascade 위험 | docs/02 §2.7 `permission_id ON DELETE CASCADE` 그대로. revoke 시 share row를 `revoked_at` set만, permission row는 별도 `revokePermission` 호출(audit `permission.revoked` + `share.revoked` 둘 다 발행). 또는 share만 soft-revoke + permission row 삭제 — **결정**: share `revoked_at` set + permission row delete (단일 audit `share.revoked`만 발행, `permission.revoked`는 emit 안 함 → 대신 share.revoked의 metadata에 permissionId 포함). 이중 audit 회피, KISS |
| ADR #28 docs와 Preset.java 5-preset drift | ADR #34에서 "share preset = 공유 받은 사람의 권한 셋, shares 테이블은 share 메타(메시지·만료·revoke 추적)" 라는 의미 분리 명시 |
| frontend 미사용 spec(`subjects: [{type: USER\|DEPT}]`) | 4종(user/department/role/everyone) 모두 받되 docs §7.9 본문은 4종 lower-case로 정정 — ADR #34에 채택 사유 박제 |
| `expires_at` 컬럼 추가 시 V6 마이그레이션 영향 | 단일 ALTER ADD COLUMN(NULL 허용) — V5 schema와 호환. share row는 `created_at`/`expires_at` 시점 검증 controller 진입 즉시(미래 시각) |
| LIKE/grep 후처리 비용(by-me / with-me 결과 N×M) | by-me: `WHERE shared_by = :actorId AND revoked_at IS NULL` (인덱스 `idx_shares_active`). with-me: subject 매칭 — **MVP**: subject_type='user' AND subject_id=actorId 만 1차 지원. department/role/everyone은 향후 확장(별도 ADR) — A10에서는 frontend 미사용으로 가정 + ADR #34에 backlog |
| 공유 audit 누설(메시지 PII) | docs/03 §4 audit 정책 — share.created `metadata.message`는 admin/auditor 한정 노출, ADR #24 일관 |

## 다음 세션 읽기 순서

1. 이 plan
2. `a10-shares-context.md` SESSION PROGRESS
3. `a10-shares-tasks.md` 현재 active phase
4. `docs/02-backend-data-model.md` §2.7 (shares table) + §7.9 (shares endpoints) + §7.10 (permissions endpoint — 패턴 1:1 변형)
5. `docs/03-security-compliance.md` §3.1 (SHARE permission) + §3.2 (preset 매트릭스) + §3.3 (subject types)
6. `docs/00-overview.md` §5 ADR (마지막 #33 → A10 = #34 후보)
7. `dev/completed/a9-search-endpoint/a9-search-endpoint-plan.md` (A8/A9 backend 트랙 패턴)
8. `dev/completed/a8-trash-manage/a8-trash-manage-plan.md` (controller/service 분할 + audit emit + cursor)
9. `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` (grantPermission/revokePermission 재사용 진입점)
10. `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (SHARE_* enum 정의 위치)
