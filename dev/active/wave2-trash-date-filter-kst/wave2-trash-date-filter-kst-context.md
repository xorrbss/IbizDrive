# Context — admin trash KST date filter

## 진실의 출처 — 변환 위치 1곳

`backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java:95-105`

```java
private static Instant parseDateBoundary(String raw, String fieldName, boolean exclusiveUpperBound) {
    if (raw == null || raw.isBlank()) return null;
    try {
        LocalDate d = LocalDate.parse(raw);
        LocalDate boundary = exclusiveUpperBound ? d.plusDays(1) : d;
        return boundary.atStartOfDay(ZoneOffset.UTC).toInstant();   // ← 이 한 줄 KST로
    } catch (DateTimeParseException ex) {
        throw new IllegalArgumentException(
            "invalid " + fieldName + " (expected YYYY-MM-DD)", ex);
    }
}
```

## 영향받지 않는 곳

- `AdminTrashRepository`: SQL은 `f.deleted_at >= :deletedFromMin AND f.deleted_at < :deletedToMax` (timestamptz 비교, zone-agnostic). 무변경.
- `AdminTrashFilters`: 필드는 `Instant`. 무변경.
- `AdminTrashService.list`: 이미 instant로 받음. 검증 로직(`!deletedFromMin.isBefore(deletedToMax)`)도 instant 비교라 무변경.
- Frontend `api.ts adminTrash` / `useAdminTrash` / `app/admin/trash/all/page.tsx`: wire input은 `YYYY-MM-DD`. 무변경.

## 테스트 위치

- `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashControllerTest.java` (또는 신규 KST-경계 검증을 service 레벨이 아닌 controller 슬라이스 테스트로 추가)
- 기존 PR #83의 deletedFrom/deletedTo 테스트 케이스 — UTC 가정이면 KST 변경 후 expected값을 시프트
- Repository / Service 테스트는 instant 입력이라 영향 없음

## 관련 문서

- `docs/02-backend-data-model.md` §7.11 — admin trash endpoint 표 + 상세 블록
- `docs/04-admin-operations.md` §8.3 — 휴지통 운영 항목
- `BETA-RELEASE.md` §7 — v1.x deferred 목록 (KST 항목 closure 표시 가능 여부 검토)

## 참조 PR

- PR #83 (date filter 도입), PR #84 (archive)
- PR #79 (admin global trash T9), PR #82 (Wave 2 closure)
