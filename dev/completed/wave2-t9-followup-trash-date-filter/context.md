# Context

## 진입 경로

PR #79 (Wave 2 T9 admin global trash) 후 progress.md 최상단(2026-05-07)의 "다음 세션 컨텍스트"에서 v1.x backlog로 명시:

> 날짜 범위 필터 / full path resolve / folder subtree size / `deletedBy` 컬럼

이 트랙은 그 중 **날짜 범위 필터**만 다룬다. 나머지는 별도 트랙.

## 핵심 참조

- spec: `docs/02-backend-data-model.md` §7.11 (admin trash API 표/예시)
- 유사 패턴: `frontend/src/components/audit/AuditFilters.tsx` (date input 컴포넌트), `AuditLogFilters` (`fromDate`/`toDate` 필드)
- TrashCursor 와이어 포맷: `backend/src/main/java/com/ibizdrive/trash/TrashCursor.java`
- ADR 추가 불필요 — 기존 `(deletedAt DESC, id DESC)` cursor 정책과 충돌 없음, 단순 WHERE 절 추가

## 결정 요약

- 와이어: `YYYY-MM-DD` (date-only). backend가 UTC 경계 instant로 변환.
- 상한은 exclusive (`< nextDay 00:00Z`) — 사용자가 입력한 종료일 종일 포함.
- 하한 inclusive (`>= 00:00Z`).
- backend 검증: `deletedFromMin >= deletedToMax` 거부 (400).
- 프론트는 빈 값 송신하지 않음 (URLSearchParams skip).
- audit-log 필터처럼 즉시 적용(debounce 없음). cursor=null 리셋은 `updateFilter`가 보장.

## 비범위

- 사용자 트랙 `/api/trash` (일반 사용자 휴지통) — date 필터 미요구 백로그
- bulk restore/purge, deletedBy 컬럼, full path resolve, folder subtree size — 별도 트랙

## 충돌 검사

- `dev/process/`에 다른 활성 세션 없음 (확인 완료, 2026-05-07)
- 영향 파일은 모두 PR #79 결과 산출물 — 안전
