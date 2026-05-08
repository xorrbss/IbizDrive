# Wave 2 T9 follow-up — admin trash 날짜 필터 KST 경계화

## 목적

`/api/admin/trash` 의 `deletedFrom`/`deletedTo` 필터가 현재 `ZoneOffset.UTC`로 변환된다. 한국 운영자가 `2026-05-08`을 입력하면 백엔드는 `2026-05-08T00:00:00Z ~ 2026-05-09T00:00:00Z`로 해석해, 실제로는 KST 09:00 ~ 익일 09:00 데이터를 검색한다 (의도한 KST 0~24시와 9시간 어긋남).

이를 KST(`Asia/Seoul`) 경계로 해석해 운영자 의도와 일치시킨다.

## 범위

**In-scope**
- `AdminTrashController.parseDateBoundary` — `ZoneOffset.UTC` → `ZoneId.of("Asia/Seoul")`
- 관련 테스트: 9시간 시프트 검증 (UTC 경계 입력으로는 KST 0시가 되지 않음을 확인)
- docs/02 §7.11 표/주석에 KST 경계 명시
- progress.md 진행 기록

**Out-of-scope (YAGNI)**
- application.yml 기반 timezone 설정 주입 — 사내 단일 지역 운영, 기존 cron `app.purge.zone: Asia/Seoul`도 하드코딩 패턴이라 정합성 ↑
- 사용자별/세션별 timezone 헤더 지원
- AdminTrashRepository SQL — `timestamptz` 비교는 zone-agnostic, 무변경
- Frontend `api.adminTrash` — wire 입력은 그대로 `YYYY-MM-DD`, 시그니처 무변경
- Audit/감사 export 등 다른 영역의 timezone 처리 — 스코프 밖

## 설계 결정

1. **Hardcoded `ZoneId.of("Asia/Seoul")`** (not env-driven)
   - 사내 단일 지역 + 기존 cron zone 하드코딩과 정합 (KISS, YAGNI)
   - 다중 리전 진입 시 application.yml `app.timezone` 정도로 일원화 — 그땐 cron까지 묶어 처리

2. **Boundary semantics 유지**
   - 하한 inclusive, 상한 exclusive (`d.atStartOfDay(KST)` ~ `d.plusDays(1).atStartOfDay(KST)`)
   - 입력 `YYYY-MM-DD`는 "그 날 KST 24시간을 모두 포함"

3. **Wire 호환**
   - 입력 query string `deletedFrom=2026-05-08&deletedTo=2026-05-08`은 그대로
   - 동작만 KST 기준으로 9시간 시프트 — 기존 사용자가 UTC 의도로 입력했더라도 운영 컨텍스트(한국 사내 시스템)에서는 의도가 KST일 가능성이 압도적

## 수용 기준

- [ ] `parseDateBoundary("2026-05-08", false)` → `2026-05-07T15:00:00Z` (= KST 2026-05-08 00:00)
- [ ] `parseDateBoundary("2026-05-08", true)` → `2026-05-08T15:00:00Z` (= KST 2026-05-09 00:00)
- [ ] `deletedFrom=2026-05-08&deletedTo=2026-05-08` 으로 KST 0~24시에 deleted된 row만 검색됨 (controller-level test)
- [ ] 잘못된 형식(`2026/05/08`) → IAE → 400 (기존 동작 유지)
- [ ] backend gradle test admin.trash slice GREEN
- [ ] frontend test 영향 없음 (시그니처 무변경) — typecheck/lint/test 회귀 0
- [ ] docs/02 §7.11 KST 경계 명시
- [ ] progress.md 신규 트랙 항목

## 위험/롤백

- 위험 ↓: 변경은 controller 1개 메서드의 ZoneId 1줄. SQL/DTO/wire 무변경.
- 롤백: 단순 revert.
