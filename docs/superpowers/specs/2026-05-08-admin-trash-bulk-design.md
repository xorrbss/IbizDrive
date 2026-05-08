# Spec — `/admin/trash/all` bulk restore/purge

**Status**: Brainstorming approved (2026-05-08).
**트랙명**: `admin-trash-bulk`
**Wave**: Wave 2 T9 follow-up (admin global trash 정리 시리즈 — `wave2-t9-admin-global-trash` → `wave2-t9-followup-trash-date-filter` → `wave2-t9-deleted-by`의 다음).

## 1. 배경

`/admin/trash/all`은 현재 단건 [복원]/[영구 삭제] 액션만 제공한다. 사내 베타 운영 시나리오 두 가지에서 가치가 큰데 부재하다:

1. **30일 만료 직전 일괄 정리** — 보존 기간 만료 직전 행을 일괄 영구삭제(또는 일괄 복원).
2. **사용자 대량 오삭제 후 일괄 복원** — 한 사용자가 폴더 전체를 잘못 삭제한 뒤 관리자가 일괄 복원.

두 시나리오 모두 단건 클릭으로 처리 시 비효율 + 운영 실수 위험. BETA-RELEASE §7 v1.x deferred 항목 "bulk restore·purge"의 정공법 closure.

## 2. 비목표

- **정책 UI**(`/admin/trash/policy`): 별도 트랙. 본 트랙은 항목 액션만.
- **2인 승인 워크플로**: 별도 트랙. 본 트랙은 단일 ADMIN 즉시 실행.
- **사용자 트랙 `/trash` bulk**: 본 트랙은 admin 전용. owner-facing 변경 없음.
- **새 audit enum**: per-item 기존 emit으로 충분.
- **Cap 200 초과 (page-and-go)**: 본 트랙은 단일 request cap만. 페이지 단위 반복 정리는 클라이언트 책임.

## 3. 기능 요구사항

### 3.1 API

**신규**: `POST /api/admin/trash/bulk`

```http
POST /api/admin/trash/bulk
Authorization: <ADMIN session>
Content-Type: application/json

{
  "action": "restore" | "purge",
  "items": [
    {"type": "file",   "id": "11111111-..."},
    {"type": "folder", "id": "22222222-..."}
  ]
}
```

**Response 200** (부분 성공 허용):

```json
{
  "succeeded": [
    {"type": "file", "id": "11111111-..."}
  ],
  "failed": [
    {"type": "folder", "id": "22222222-...", "error": "NAME_CONFLICT"}
  ]
}
```

- `action`은 `restore` | `purge` (소문자 정확 일치).
- `items`는 1~200개. 0개 또는 201+개 → `400 BAD_REQUEST`.
- 동일 `(type, id)` 중복 허용(idempotent — 중복 항목은 그대로 fan-out, 두 번째 호출은 NOT_FOUND/NO_OP로 failed에 들어가거나 idempotent하게 succeeded). 중복 제거 책임은 클라이언트.
- 응답 status는 부분 실패에도 항상 `200`. 전체 실패도 `200` + 모든 항목이 `failed`. 운영 진단은 응답 body로.
- `action`이 `restore`/`purge` 외 값 → `400 BAD_REQUEST` (`IllegalArgumentException` → `GlobalExceptionHandler.handleBadRequest`).

**기존 단건 endpoint 무변경** — `POST /api/files|folders/{id}/restore`, `DELETE /api/trash/{type}/{id}`. 본 트랙 endpoint는 service layer에서 단건 mutation을 fan-out 호출.

### 3.2 권한

- `@PreAuthorize("hasRole('ADMIN')")` (단건과 동일). MEMBER/AUDITOR → `403`. 미인증 → `401`.
- 본 endpoint는 cross-owner 작용 — `docs/03 §3` 권한 매트릭스 admin row를 따른다(별도 권한 enum 추가 없음).

### 3.3 Audit emit

- **새 enum 0**. 항목별로 기존 단건 mutation이 emit하는 enum을 그대로 활용:
  - restore: `FILE_RESTORED` / `FOLDER_RESTORED`
  - purge: `FILE_PURGED` / `FOLDER_PURGED`
- `actor_id`는 단건과 동일하게 SecurityContext에서 추출(`AdminTrashController` 또는 service layer가 전달, 직전 `wave2-t9-deleted-by` 트랙 V10 패턴 일치).
- bulk 자체를 추적하는 별도 `metadata.bulk: true`도 미추가 — 동일 actor + 근접 timestamp로 audit_log SELECT에서 식별 가능. KISS.

### 3.4 동시성 / 트랜잭션

- 항목별 독립 트랜잭션. 한 항목의 `NAME_CONFLICT`(restore) / `NOT_FOUND`(purge) 등이 다른 항목 처리를 막지 않는다.
- 단건 mutation service의 트랜잭션 경계를 그대로 재사용 — `FileMutationService.restore`/`purge`, `FolderMutationService.restore`/`purge` 각각이 자기 트랜잭션을 가진다(`@Transactional`).
- bulk service 자체는 트랜잭션을 열지 않음(`AdminTrashService`는 `@Transactional(readOnly = true)`로 listing만, mutation은 단건 service에 위임).
- DB lock window: 항목별 독립 → 한 번에 200개를 직렬 처리 시 최대 200개의 짧은 트랜잭션. 운영 부하는 작음.

### 3.5 에러 코드

기존 단건 에러 그대로:

- `404 NOT_FOUND` — 항목이 trash에 없음(restore/purge 모두). 응답 body의 failed[].error에 문자열로.
- `409 NAME_CONFLICT` — restore 시 원위치에 동명 활성 항목 존재. 단건 endpoint와 동일 시나리오.
- `403 FORBIDDEN` — fan-out 호출이 단건 권한 가드(SpEL)에서 거부될 때. ADMIN role이라 사실상 발생 안 함, defensive.

응답에는 단건 에러 코드 enum의 wire 문자열(`docs/02 §8`)만 노출. 새 에러 enum 추가 0.

### 3.6 Frontend UI

#### 3.6.1 행 선택 모델

- `/admin/trash/all` 행 좌측 체크박스(컬럼 신규).
- 헤더 좌측 select-all 체크박스 — **현재 페이지(cursor 결과) 한정**. cursor 다음 페이지로 이동 시 선택 초기화(다음 페이지 항목 자동 선택은 위험 — 운영자가 의도하지 않은 항목 영구삭제 방지).
- 선택 상태는 페이지 컴포넌트 local state(`Set<string>` 키는 `${type}:${id}`). Zustand 슬라이스 신설 안 함.
- 필터/정렬 변경 시 선택 초기화(직전 패턴 재사용 — `updateFilter` 가 cursor 리셋 보장하므로 기존 흐름에 합류).

#### 3.6.2 BulkActionBar (admin trash 전용)

선택 1개 이상일 때 페이지 상단 sticky 영역에 노출:

```
선택 N개 [전체 해제] | [일괄 복원] [일괄 영구삭제]
```

- 컴포넌트: `frontend/src/components/admin/AdminTrashBulkActionBar.tsx` 신설. explorer의 `BulkActionBar`(`docs/01 §8`)와 패턴 정합하지만 별 컴포넌트 — admin/trash 전용 props/액션이라 공유 안 함.
- "일괄 영구삭제" → ConfirmDialog (단건과 동일 텍스트). "이 동작은 되돌릴 수 없습니다. 선택한 N개를 영구 삭제하시겠습니까?"
- "일괄 복원"은 비파괴적이므로 즉시 mutate(`docs/01 §19` 원칙 3 — 비파괴는 낙관 가능, 본 트랙은 pending 로딩 상태로 처리).

#### 3.6.3 결과 toast

```
복원 N개 성공, M개 실패
```

`failed.length > 0`이면 toast에 "자세히" 링크 → details 펼치기(failed 항목 목록 + 에러 코드).

성공만 있는 경우 단순 toast.

#### 3.6.4 query invalidation

- mutation 성공 시 `qk.adminTrash()` prefix invalidate(직전 패턴 그대로 — useAdminTrash hook).
- 부분 실패도 동일하게 invalidate(succeeded 항목은 trash에서 빠졌으므로).

### 3.7 Limit / cap

- Server-side hard cap 200 — `400 BAD_REQUEST`(`items must be 1..200`).
- Client-side는 cap 인지하고 200 초과 시 미리 거부 또는 chunk(본 트랙은 chunk 미구현 — 단순히 페이지당 max 50/100을 따라가게 두면 자연스럽게 cap 안. select-all이 100 미만이면 API cap 안전).

## 4. 영향 범위

### 4.1 Backend

| 파일 | 변경 |
|---|---|
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java` | `bulk` 핸들러 추가 (`POST /bulk`), request body record + dispatch |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java` | `bulk(action, items, actorId)` 추가 — items fan-out, succeeded/failed 누적 |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashBulkRequestDto.java` (NEW) | request record (`action`, `items: List<{type, id}>`) |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashBulkResponseDto.java` (NEW) | response record (`succeeded`, `failed: List<{type, id, error?}>`) |
| `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashServiceBulkTest.java` (NEW) | unit: 일부 실패 분기, cap 위반, action enum 검증, idempotency |
| `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashControllerBulkTest.java` (NEW) | sliced WebMvc: 200/401/403/400 매트릭스 |

**무변경**: `AdminTrashRepository`, `AdminTrashItemDto`, `FileMutationService`, `FolderMutationService`, V10 schema, audit emit, 권한 enum.

### 4.2 Frontend

| 파일 | 변경 |
|---|---|
| `frontend/src/types/trash.ts` | `AdminTrashBulkAction = 'restore' \| 'purge'`, `AdminTrashBulkRequest`, `AdminTrashBulkResponse` 추가 |
| `frontend/src/lib/api.ts` | `adminBulkTrash(action, items)` 신규 |
| `frontend/src/lib/queryKeys.ts` | `qk.adminTrashBulk()` 신규? — 현 mutation은 별도 키 불필요(invalidate는 `qk.adminTrash()` prefix). 키 추가 0 |
| `frontend/src/hooks/useAdminTrash.ts` | `useAdminBulkTrash()` mutation 신규(invalidate `qk.adminTrash()` prefix) |
| `frontend/src/app/admin/trash/all/page.tsx` | 행 좌측 체크박스 컬럼 + select-all 헤더 + BulkActionBar 통합 + 결과 toast |
| `frontend/src/components/admin/AdminTrashBulkActionBar.tsx` (NEW) | 선택 N개 / 일괄 복원 / 일괄 영구삭제 |
| `frontend/src/app/admin/trash/all/page.test.tsx` | 다중 선택 / select-all / BulkActionBar 노출 / 부분 실패 toast 시나리오 |
| `frontend/src/lib/api.adminTrashBulk.test.ts` (NEW) | wire 송신/수신 (succeeded/failed shape) |

**무변경**: explorer `BulkActionBar`(공유 안 함), 단건 mutation hook, owner-facing trash 페이지/타입.

### 4.3 Docs

| 문서 | 변경 |
|---|---|
| `docs/02-backend-data-model.md` §7.11 | `POST /api/admin/trash/bulk` row + 부분 실패 모델 명시 |
| `docs/02-backend-data-model.md` §8 | 변경 없음 (새 에러 enum 0) |
| `docs/04-admin-operations.md` §8.3 | bulk UI 명시 (cap 200, 결과 toast 형식) |
| `BETA-RELEASE.md` §7 | "bulk restore·purge" v1.x 항목 closure (트랙명 backlink) |
| `docs/progress.md` | 최상단 closure entry |

## 5. 트레이드오프 및 대안

### 5.1 단일 endpoint vs action별 분리

- 단일 (`POST /bulk` + body action) **선정**: 라우트 1개, 클라이언트 fan-out 동일.
- 분리 (`POST /bulk/restore` + `POST /bulk/purge`): RESTful 더 명확하지만 라우트 2개, 클라이언트 wire 코드 중복. 본 endpoint는 admin 전용이라 RESTful 정통성보다 KISS 우선.

### 5.2 부분 실패 vs all-or-nothing

- **부분 실패 선정**: 운영 가치 高. 한 항목 NAME_CONFLICT가 다른 199개를 막지 않음.
- all-or-nothing(단일 트랜잭션): 단순하지만 큰 batch에서 운영 marshalling 어려움. 사내 베타 시나리오에서 NAME_CONFLICT 흔함(특히 restore).

### 5.3 새 audit enum (e.g., `ADMIN_BULK_TRASH_PERFORMED`)

- **미추가 선정**: per-item enum이 이미 있고 actor_id로 묶을 수 있음. 운영자 audit_log 조회 시 동일 actor + 근접 timestamp로 식별 가능. 새 enum 추가는 docs/03 §4 spec 갱신 + listener 신설 — 가치 대비 비용 ↑.
- 추가 시 장점: bulk 추적 명시화. 단점: 47 → 48 enum, listener 신설.

### 5.4 Cap 200 vs 무제한

- **200 cap 선정**: q 길이 cap 200과 정합, 단일 request lock window 보호. 사내 베타 페이지당 max 100/cap 50과 자연 호환.
- 무제한: 클라이언트 부담 ↑(느린 요청). 200 정도면 운영 만료 정리 1 페이지 단위로 충분.

### 5.5 진행 표시 (progress)

- **미추가 선정**: 200 항목 직렬 처리 ≈ 수 초. 진행 바보다 단순 spinner + 완료 toast.
- 추가 시: SSE / WebSocket 도입 비용 큼. 본 트랙 범위 초과.

## 6. 보안 / 안전 고려

- **파괴적 액션 재검증**(`docs/01 §19` 원칙 10): bulk endpoint는 단건 mutation service를 그대로 호출하므로 기존 검증 로직(SpEL, NAME_CONFLICT 검사, FOR UPDATE lock)이 그대로 적용된다.
- **Cap**: DOS 방어(단일 request 직렬 처리 시간 cap = 200 항목).
- **ConfirmDialog**(영구삭제): 클라이언트 layer 안전망. 서버는 항상 즉시 실행.
- **Idempotency**: 동일 항목 중복 요청 시 NOT_FOUND/NO_OP fallback. 서버 재시도(network retry) 안전.

## 7. Rollout / 운영

- DB schema 변경 0. 즉시 배포 가능.
- 기존 단건 endpoint 무변경 — 회귀 위험 작음.
- audit_log 영향: per-item emit 그대로 — 운영자 관측 행동에 변화 없음 (단지 한 번에 많이 들어옴).

## 8. 검증 / 게이트

- backend `./gradlew test --tests "com.ibizdrive.admin.trash.*"` BUILD SUCCESSFUL
- backend 영향 범위 (folder/file/purge/admin.trash) BUILD SUCCESSFUL
- frontend `pnpm test --run` skipped 0
- frontend `pnpm typecheck && pnpm lint && pnpm build` exit 0
- docs drift check (spec ↔ plan ↔ 코드 ↔ docs)

## 9. Out-of-scope (v1.x++)

- 정책 UI(`/admin/trash/policy`)
- 2인 승인 워크플로
- progress streaming (SSE/WS)
- chunked client (200 초과 자동 분할)
- 사용자 트랙 `/trash` bulk
