# Context — trash retention externalization

## 진실의 출처

- `FileMutationService:61` `PURGE_DAYS = 30` (현재 위치, 제거 대상)
- `FolderMutationService:71` `PURGE_DAYS = 30` (현재 위치, 제거 대상)
- 두 서비스 모두 `target.setPurgeAfter(now.plus(PURGE_DAYS, ChronoUnit.DAYS))` 형태

## 미러링 패턴

- `com.ibizdrive.audit.AuditExportProperties` — `@ConfigurationProperties(prefix="app.audit.export")` record
- `com.ibizdrive.audit.AuditConfig` — `@Configuration @EnableConfigurationProperties(...)`
- 동일하게 trash 패키지에 `TrashRetentionProperties` + `TrashConfig` 신설

## 영향받지 않는 곳

- `AdminTrashItemDto.purgeAfter` 필드 — 동작 무변경 (그대로 deletedAt + 30일)
- frontend types/trash.ts — wire 동일
- A7 hard purge cron — `purge_after`만 보고 hard delete, 보존 기간 정책과 분리
- DB schema V5 — purge_after 컬럼 그대로

## 테스트 인프라

- `FileMutationServiceTest` — `@SpringBootTest` + Testcontainers Postgres + `@TestConfiguration`이 `new FileMutationService(...)` 직접 호출. 5번째 인자 추가 필요.
- `FolderMutationServiceTest` — 동일 패턴
- 다른 곳에서 manual 생성하는 코드 0 (grep 확인)
