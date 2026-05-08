# Wave 2 T9 follow-up — Admin Trash 날짜 범위 필터

## 배경

Wave 2 T9 (admin global trash, PR #79) closure에 명시된 v1.x backlog 항목 중 **"날짜 범위 필터"** 트랙. `/admin/trash/all` 페이지에서 `deletedAt` 범위로 필터링할 수 있게 한다.

- 기존 admin trash 필터: `q`, `type`, `ownerId`
- 추가: `deletedFrom`, `deletedTo` (날짜 범위, deletedAt 기준)
- 사용자 트랙(`/api/trash`) 영역은 **이번 트랙 범위 외** — admin 전용 backlog 항목

## 설계 결정

### 1. 와이어 포맷 — `YYYY-MM-DD` (date-only)

- `<input type="date">` UI와 자연스럽게 매칭
- audit-log 페이지의 `fromDate/toDate` 패턴(`AuditFilters.tsx`)과 동일 컨벤션
- backend는 `LocalDate.parse()` → UTC instant boundary로 변환:
  - `deletedFrom = "2026-05-01"` → `2026-05-01T00:00:00Z` (inclusive 하한)
  - `deletedTo   = "2026-05-07"` → `2026-05-08T00:00:00Z` (exclusive 상한, 즉 5/7 종일 포함)
- 잘못된 형식 → `IllegalArgumentException` → 400 VALIDATION_ERROR (기존 핸들러 재사용)

### 2. 조합 가능성

- 하한만, 상한만, 양쪽 다, 어느 쪽도 없음 → 모두 허용
- `deletedFrom > deletedTo` (date 비교) → 400 VALIDATION_ERROR
- 다른 필터(q/type/ownerId) + 날짜 범위 자유 조합

### 3. cursor 호환성

- 기존 `TrashCursor`(deletedAt, id) 그대로 유지
- 날짜 범위는 native query의 별도 WHERE 절 — cursor 비교 절과 독립적으로 결합
- 필터 변경 시 page.tsx의 `updateFilter`가 cursor=null 리셋하므로 자연 처리

### 4. 쿼리 키

- `qk.adminTrashList(filters, cursor)`에 `deletedFrom`/`deletedTo` 포함 필요
- `AdminTrashFilters` 타입을 확장하면 `qk.adminTrashList`도 자동 반영해야 함 (객체 spread X, explicit field)
- 현재 키 함수는 explicit field 추출 → 두 필드 추가 필요

## 산출 매트릭스

### 백엔드
1. `AdminTrashFilters.java` — 필드 추가 (`Instant deletedFromMin`, `Instant deletedToMax`)
2. `AdminTrashController.java` — `@RequestParam("deletedFrom"|"deletedTo") String` 수신, `LocalDate.parse` → Instant boundary 변환
3. `AdminTrashRepository.java` — 양쪽 native @Query에 `(:deletedFromMin IS NULL OR f.deleted_at >= ...)` + `(:deletedToMax IS NULL OR f.deleted_at < ...)`
4. `AdminTrashService.java` — repo 호출에 두 값 전달, `deletedFromMin >= deletedToMax` 거부

### 프론트엔드
5. `frontend/src/types/trash.ts` — `AdminTrashFilters`에 `deletedFrom`/`deletedTo: string | null`
6. `frontend/src/lib/queryKeys.ts` — `adminTrashList` 키에 두 필드 추가
7. `frontend/src/lib/api.ts` — `adminListTrash`가 두 파라미터 송신 (빈 값 skip)
8. `frontend/src/app/admin/trash/all/page.tsx` — 필터 UI에 `<input type="date">` 2개 + 초기값
9. `frontend/src/lib/api.adminTrash.test.ts` — 두 케이스 추가
10. `frontend/src/hooks/useAdminTrash.test.tsx` — 필터 타입 변경 반영 (확장 필드 채우기)

### 문서
11. `docs/02-backend-data-model.md` §7.11 — 쿼리 파라미터 표/예시 갱신, "v1.x deferred" 노트에서 "날짜 범위" 항목 제거 또는 완료 마크

## Acceptance criteria

- [ ] `GET /api/admin/trash?deletedFrom=2026-05-01&deletedTo=2026-05-07` 정상 동작 (5/7 종일 포함)
- [ ] `deletedFrom`만 / `deletedTo`만 / 둘 다 / 둘 다 없음 — 모두 동작
- [ ] 잘못된 형식 → 400
- [ ] `deletedFrom > deletedTo` → 400
- [ ] cursor pagination이 날짜 범위 필터와 함께 동작
- [ ] `/admin/trash/all` UI에서 두 date input으로 즉시 적용
- [ ] backend test (controller HTTP + service unit) 통과
- [ ] frontend test (api wire + hook queryKey) 통과
- [ ] typecheck/lint/build pass
