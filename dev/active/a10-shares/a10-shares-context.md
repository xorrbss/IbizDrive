---
Last Updated: 2026-05-01
Status: 🟢 OPEN — A10.0~A10.3 done, A10.4 active
---

# A10 — Shares Endpoint Backend — Context

## SESSION PROGRESS

### 2026-05-01 (bootstrap + A10.0 ~ A10.3)

- A9 closure(`f9200dc` PR #20) + F1 closure(`9875fe9`) 다음 백엔드 트랙 시작.
- worktree `.claude/worktrees/a10-shares` (branch `feature/a10-shares`) master `9875fe9` 기준 생성.
- dev-docs 3파일 작성 (plan / context / tasks). `dev/process/a10-shares-2026-05-01.md` ownership 고정.
- **A10.0** (`c28bea3` + `e0a74f0`): ADR #34 본문 + docs/02 §2.7 expires_at 컬럼 + §7.9 wire format(preset 4값 lower-case + subjects 4종 + revoke SpEL) + docs/03 §3 backlink. Preset.SHARE → enum-only(persistable 아님) 정합.
- **A10.1** (`3064667`): V6__shares.sql + Share JPA entity + ShareRepository(by-me/with-me cursor query) + V6MigrationIT 9 schema tests. 485 tests GREEN.
- **A10.2** (`e924aea`): ShareDto/ShareCreateRequest/ShareCreatedEvent + ShareCommandService.createShares (single TX, N subjects → N shares) + 10 Mockito tests. 495 tests GREEN.
- **A10.3** (this turn): ShareRevokedEvent + ShareRepository.lockByIdAndRevokedAtIsNull + ShareCommandService.revokeShare(snapshot → revoke set → permission delete CASCADE) + canRevoke SpEL + ShareAuditListener(SHARE_CREATED/REVOKED first activation). +13 tests → 508 GREEN.
- 핵심 발견 (bootstrap):
  - `shares` 테이블은 docs/02 §2.7에만 존재, V1~V5 마이그레이션에 미도입 → V6 신규 작성 필요. (A10.1에서 해결)
  - `AuditEventType.SHARE_CREATED/REVOKED/EXPIRED` 정의됨, 사용처 0 → A10.3이 첫 활성화.
  - `PermissionService.grantPermission/revokePermission` (A4.4) 자산 그대로 재사용 — share row는 그 위에 메타(message/expiresAt/revoke 추적) 추가. revoke는 `permissionRepository.deleteById` 직접 호출 → V6 CASCADE → share row 삭제 (PermissionRevokedEvent 미발행, 이중 audit 회피).

## Current Execution Contract

- **자율 모드 유지** (메모리 `feedback_autonomous_mode`). 게이트 통과 시 자동 다음 phase 진입. **PR 생성 직전 게이트만 사용자 승인 대기**.
- **단일 PR / squash merge** — A8/A9 패턴 일관. 추정 6~9 commits.
- **TDD 순서**: A10.1~A10.5 RED→GREEN. **Mockito 단위 위주** (A8/A9 일관). V6 schema 검증 1건만 Testcontainers (V1~V5 IT가 이미 cascade/FK 검증 패턴 보유).
- **CLAUDE.md §3 원칙 강제**: 편법 금지 (8) — `share.created/revoked` audit는 enum이 정의된 자리이므로 emit 활성화가 정상 경로. 문제 은폐 금지 (9) — folder 공유/외부 토큰/SSE emit 미구현은 ADR #34에 명시 박제 + endpoint 미신설 (스켈레톤 안 둠).
- **컨텍스트 보고**: phase 종료 + commit + 다음 phase 진입 시 한 줄 보고. 60% 임계 도달 시 dev-docs-update 후 핸드오프.

## 현재 active task

- **A10.4** — `ShareQueryService` (by-me / with-me) + `ShareCursor` (`{createdAtEpochMs}|{id}` base64 url-safe codec).
- 게이트 조건: Mockito 단위 ≥5건 + 회귀 GREEN → 자동 A10.5 진입.

## 다음 세션 읽기 순서

1. 이 `a10-shares-context.md` SESSION PROGRESS 최신
2. `a10-shares-plan.md` 요약 + 단위 분할
3. `a10-shares-tasks.md` 현재 active phase 체크박스
4. `docs/02-backend-data-model.md` §2.7 (shares table) + §7.9 (shares endpoints) + §7.10 (permissions — 패턴 base)
5. `docs/03-security-compliance.md` §3.1 (SHARE permission) + §3.2 (preset 매트릭스) + §3.3 (subject types)
6. `docs/00-overview.md` §5 ADR (마지막 #33 → A10 = #34)
7. `dev/completed/a8-trash-manage/` + `dev/completed/a9-search-endpoint/` (controller/service 분할 + audit emit + cursor 패턴)
8. `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` (grantPermission/revokePermission)
9. `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (SHARE_* enum 정의 위치)
10. `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` (`permissions` 테이블 + UNIQUE index 패턴 — V6 작성 base)

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 영향 |
|---|---|---|
| `docs/00-overview.md` §5 | ADR 로그 | A10.0 — #34 신설 |
| `docs/02-backend-data-model.md` §2.7 | shares 테이블 schema | A10.0 — `expires_at` 컬럼 추가 |
| `docs/02-backend-data-model.md` §7.9 | shares endpoints 표/의사코드 | A10.0 — preset/subject wire format 정정 + revoke SpEL 명시 |
| `docs/03-security-compliance.md` §3.5 | endpoint × permission 매핑 | A10.0 — POST `/api/files/:id/share` → SHARE backlink |
| `backend/src/main/resources/db/migration/V6__shares.sql` | NEW | A10.1 — shares 테이블 + `expires_at` + 인덱스 |
| `backend/.../share/Share.java` (entity) | NEW | A10.1 |
| `backend/.../share/ShareRepository.java` | NEW | A10.1 — by-shared-by + by-subject 쿼리 |
| `backend/.../share/ShareDto.java` | NEW (record) | A10.2 |
| `backend/.../share/ShareCreateRequest.java` | NEW (record) | A10.2 |
| `backend/.../share/ShareCommandService.java` | NEW — create/revoke 트랜잭션 | A10.2 + A10.3 |
| `backend/.../share/ShareQueryService.java` | NEW — by-me / with-me + cursor + 후처리 | A10.4 |
| `backend/.../share/ShareCursor.java` | NEW (codec) | A10.4 — A8/A9 cursor 패턴 변형 |
| `backend/.../share/ShareController.java` | NEW | A10.5 — 4 endpoints |
| `backend/.../permission/PermissionService.java` | A4.4 자산 | 참조만 — `grantPermission` 호출 |
| `backend/.../audit/AuditEventType.java` | A2 자산 | SHARE_CREATED/REVOKED 첫 사용처 활성화 |
| `backend/.../audit/AuditService.java` | A2 자산 | `record(...)` 호출 — REQUIRES_NEW 트랜잭션 |

## 중요한 의사결정 (A10.0 ADR #34 후보)

1. **shares 테이블 = 별도 row + permission row 위에 메타** — `permission_id` FK ON DELETE CASCADE 유지. share `revoked_at` set 시 permission row는 별도 delete (단일 audit `share.revoked`만 발행, `permission.revoked` emit 안 함, KISS). metadata에 permissionId 보존.
2. **POST /api/files/:id/share request wire format 정정**:
   - `preset` ∈ `read|upload|edit|share|admin` (Preset.java wire format 그대로)
   - `subjects[].type` ∈ `user|department|role|everyone` (PermissionService 도메인 그대로). 4종 모두 받되 with-me 쿼리 MVP는 `user`만 매칭. 나머지는 backlog ADR.
   - `expiresAt` ISO8601 미래 시각 (PermissionService.validateGrantInput과 동일)
   - `message` 선택 (TEXT, 길이 cap 1000자 controller 검증)
3. **`expires_at` 컬럼** — `shares` 테이블에 추가 (V6). frontend 권한 expiresAt UX 패리티 + permissions 테이블의 `expires_at`과 의미 분리 (share 종료 vs permission 종료 — share만 expire되면 permission row는 남고 with-me 쿼리에서 제외).
4. **DELETE /api/shares/:shareId 권한** — `@PreAuthorize("@shareCommandService.canRevoke(#shareId, principal)")` 채택 (A4.4 `canRevokePermission` 패턴 재사용). 내부 평가: `share.shared_by == principal.userId || principal.role == ADMIN`.
5. **GET /api/shares/by-me 쿼리** — `WHERE shared_by = :actorId AND revoked_at IS NULL` + cursor `{createdAt}|{id}` base64 + `ORDER BY created_at DESC, id DESC LIMIT :limit + 1`.
6. **GET /api/shares/with-me 쿼리** — MVP: `INNER JOIN permissions ON shares.permission_id = permissions.id WHERE permissions.subject_type='user' AND permissions.subject_id=:actorId AND shares.revoked_at IS NULL`. department/role/everyone은 backlog ADR.
7. **READ 후처리 미적용** — by-me는 actor 자기 자산, with-me는 actor가 받은 share. 별도 권한 게이트 불필요. (단, with-me 결과는 `permissions.expires_at < NOW()` 도과 row 제외 — shared_at 기준이 아닌 permission 만료 기준).
8. **SHARE_CREATED audit metadata** — `{ shareId, fileId, permissionId, subjectType, subjectId, preset, expiresAt, message? }`. message는 admin/auditor 노출 한정 (docs/03 §4).
9. **SHARE_REVOKED audit metadata** — `{ shareId, permissionId, originalSharedBy, revokedReason? (선택 — body parameter 추가 안 함, MVP) }`. before_state에 share row snapshot.
10. **folder 공유 endpoint 미도입** — shares 테이블 schema는 file_id/folder_id 양립이지만, MVP endpoint는 `POST /api/files/:id/share` 단일. ADR #34에 backlog 박제 — endpoint 신설 시 V_(컬럼 추가 없음) + ShareCommandService overload만으로 hook.

## 빠른 재개

```text
1. 현재 phase = A10.4 (ShareQueryService + ShareCursor)
2. 다음 commit = "feat(A10.4): ShareQueryService by-me/with-me + ShareCursor"
3. ShareCursor: A8 TrashCursor / A9 SearchCursor 패턴 변형 — `{createdAt epoch ms}|{uuid}` Base64.getUrlEncoder().withoutPadding()
4. ShareRepository.findActiveBySharedBy / findActiveWithMeBySubjectUser 이미 cursor query로 작성됨 (A10.1) — service 레이어만 추가
5. limit+1 패턴 (A8 일관) — pageSize+1 row 받아 hasMore 판정 + nextCursor 발급
6. SharePage record(items, nextCursor)
7. Mockito ≥5: by-me 빈 리스트 / by-me 페이지(nextCursor 발급) / with-me 빈 / with-me 페이지 / cursor round-trip
```
