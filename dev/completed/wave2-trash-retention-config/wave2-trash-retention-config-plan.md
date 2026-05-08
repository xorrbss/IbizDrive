# wave2-trash-retention-config — PURGE_DAYS 외부화

## 목적

`FileMutationService.PURGE_DAYS = 30` / `FolderMutationService.PURGE_DAYS = 30`이 두 서비스에 중복 하드코딩. 운영자가 보존 기간을 조정하려면 코드 변경 + 재배포 필요. `audit-export-cap-config` 패턴(PR #97)을 그대로 미러링해 `@ConfigurationProperties`로 외부화한다.

## 범위

**In-scope (backend-only)**
- `com.ibizdrive.trash.TrashRetentionProperties` (NEW record, default 30 + 0/음수 보정)
- `com.ibizdrive.trash.TrashConfig` (NEW @EnableConfigurationProperties)
- `application.yml`: `app.trash.retention-days: 30`
- `FileMutationService` / `FolderMutationService`: PURGE_DAYS 상수 제거 → 생성자 DI
- `TrashRetentionPropertiesTest` (NEW, 3 cases: 양수/0/음수)
- 기존 `FileMutationServiceTest`/`FolderMutationServiceTest` TestConfig: 5번째 인자 추가
- docs/02 §6.5: 외부화 명시
- docs/04 §9.2: 운영자 제어 가이드

**Out-of-scope (YAGNI)**
- 무중단 변경 (Spring `@ConfigurationProperties`는 부팅 바인딩)
- 운영자 UI(`/admin/trash/policy`) — v1.x
- 파일/폴더별 보존 기간 — v1.x++

## 설계 결정

1. **`audit-export-cap-config` 패턴 동형** — properties record + Config 등록 + service constructor 주입.
2. **0/음수 입력 → default 30 보정** — 운영자 실수로 yml에 잘못 입력 시 즉시 hard purge 후보가 되는 사고 방지 (보안적 가드).
3. **두 서비스 모두 외부화** — duplicated source of truth 제거.
4. **wire 무변경** — 동작은 그대로 30일 default, 외부화만 추가.

## 수용 기준

- [ ] `FileMutationService` / `FolderMutationService` 모두 PURGE_DAYS 상수 제거
- [ ] 두 서비스가 `TrashRetentionProperties.days()`로 보존 기간 계산
- [ ] application.yml에 `app.trash.retention-days: 30`
- [ ] `TrashRetentionPropertiesTest` GREEN (양수/0/음수 보정)
- [ ] 기존 `FileMutationServiceTest` / `FolderMutationServiceTest` 컴파일 통과
- [ ] docs/02 §6.5 + docs/04 §9.2 갱신
- [ ] frontend 0 변경

## 위험 / 롤백

- 위험 ↓: backend-only, wire/DTO 무변경, default 동일
- 롤백: revert
