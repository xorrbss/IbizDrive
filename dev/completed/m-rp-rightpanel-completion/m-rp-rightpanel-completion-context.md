# M-RP — Context

Last Updated: 2026-05-02 (M-RP.3 closure)

## SESSION PROGRESS

### 2026-05-02 (bootstrap)
- 직전 트랙 `mvp-qa-security-week-11-12` 가 master `a00a31c`로 closure 완료.
- 사용자 결정: admin frontend 트랙은 진행하지 않음. MVP 마무리 + 사내 베타 오픈에 필요한 기능에 집중.
- 코드베이스 gap 조사 → RightPanel 3탭 (`versions`/`activity`/`permissions`) 모두 `<ComingSoon />` placeholder.
- 본 트랙 bootstrap (plan/context/tasks 3파일).
- 구현 코드 변경 없음.

### 2026-05-02 (M-RP.3 구현 완료)
- 신규 파일: `frontend/src/components/files/PermissionsTab.tsx` + `PermissionsTab.test.tsx` (4 케이스).
- 수정: `RightPanel.tsx`(권한 탭 placeholder → PermissionsTab conditional mount + 헤더 주석 갱신),
  `RightPanel.test.tsx`(api mock에 `getEffectivePermissions` 추가 + 권한 탭 활성화 / fetch 차단 신규 2 케이스).
- 검증: `pnpm test --run` = **79 files / 639 tests pass** (M-RP.2 baseline 78/633 + 1 file/6 tests),
  `pnpm typecheck` exit 0, `pnpm lint` exit 0.
- 의사결정: 기존 `usePermission` 훅 그대로 재사용 — `Record<Permission, boolean>` 보수적 디폴트(로딩 중 모두 false)로
  깜빡임 회피. PermissionsTab은 별도 skeleton/error UI 없이 9 chip 항상 렌더 + held/unheld 시각 구분 (KISS).
- backend 변경 0 — frontend-only 트랙. 권한 enum/계약 변경 없음.
- 커밋 단위: M-RP.2와 분리된 단일 커밋 (`feat(m-rp.3): RightPanel permissions 탭 wiring`).
- 다음: M-RP.4 (activity 탭 wiring + AuditQueryFilters 확장 — G4 closure 게이트, ADR #40 RP-2 권한 정책 결정 포함).

### 2026-05-02 (M-RP.2 구현 완료)
- 신규 파일: `backend/.../file/FileVersionMutationService.java` + 단위 테스트, `backend/.../file/ContentDispositionHeaders.java`(공유 helper),
  `frontend/src/hooks/useRestoreVersion.ts` + 테스트, `frontend/src/components/files/VersionsTab.test.tsx`.
- 수정: `FileVersionController.java`(+download +restore endpoints), `FileDownloadController.java`(helper로 위임),
  `FileVersionControllerTest.java`(+restore 매트릭스 7케이스), `frontend/src/lib/api.ts`(+downloadVersion +restoreVersion),
  `frontend/src/lib/api.versions.test.ts`(+download/restore describe), `VersionsTab.tsx`(다운로드/복원 버튼).
- 검증: backend `./gradlew test` BUILD SUCCESSFUL (전체 suite, Testcontainers silent skip — Docker 부재).
  frontend `pnpm typecheck && pnpm lint && pnpm test --run` = **78 files / 633 tests pass**.
- **자체 리뷰 핵심 발견**: 옵션 A의 `current_version_id` 재지정만으로는 `files.size_bytes`/`mime_type`이 stale.
  `FileUploadService:214-217`이 새 version 업로드 시 file row에 size/mime를 denormalize → restore도 같은 invariant
  유지를 위해 target version의 값으로 동기화 필요. `restoreVersion` 메소드 + 단위 테스트 + 통합 테스트 + 무효화
  매트릭스(`qk.files()` 보수 무효화 추가) 모두 보강. ADR #39 옵션 A 의미론에 sub-requirement로 포함.
- 의사결정 RP-1 (옵션 A) closure-ready: "current_version_id 재지정 + denormalized 메타 동기화 + 새 version row 생성 X".
- 다음: M-RP.3 (permissions 탭 frontend wiring) — backend usePermission/permissions endpoint는 기존, frontend-only 트랙.

### 2026-05-02 (M-RP.1 구현 완료)
- 신규 파일: `frontend/src/types/version.ts`, `frontend/src/lib/api.versions.test.ts`,
  `frontend/src/hooks/useFileVersions.ts`, `frontend/src/hooks/useFileVersions.test.tsx`,
  `frontend/src/components/files/VersionsTab.tsx`.
- 수정: `frontend/src/lib/api.ts` (+listFileVersions), `frontend/src/lib/queryKeys.ts` (+qk.fileVersions),
  `frontend/src/components/files/RightPanel.tsx` (versions 탭 conditional render 활성),
  `frontend/src/components/files/RightPanel.test.tsx` (mock 확장 + 신규 케이스 4).
- 검증: pnpm test --run = **75 files / 610 passed**, typecheck/lint/build exit 0.
- 의사결정: VersionsTab은 RightPanel에서 `tab === 'versions'` 조건부 mount → `enabled` prop 불필요 (KISS).
- backend 변경 0 — read-only frontend 트랙.
- 다음: M-RP.2 (G2 게이트 — 복원 의미론 옵션 A sign-off 필요).

## Current Execution Contract

- **Branch base**: master `a00a31c` (working tree clean).
- **자율 모드**: G2/G4 게이트만 사용자 sign-off, 그 외 phase는 자율 진행.
- **컨텍스트 임계값 보고**: 60/70/75/80% 도달 시 핸드오프 체크리스트 + 재개 명령 9줄 박스 (memory: feedback_context_limit).
- **결정 스타일**: A/B/C 분기 질문 최소화, 추천안 + 근거로 묶어 진행 (memory: feedback_decision_style).
- **트랙 결정 ID**: RP-1 (복원 의미론), RP-2 (활동 탭 권한 정책). closure 시 ADR #39, #40로 승격.

## 현재 active

- **active phase**: M-RP.3 (permissions 탭 frontend wiring) — 직전 phase M-RP.2 완료 (78 files / 633 tests).
- **active task**: M-RP.3.1 (PermissionsTab 컴포넌트) — frontend-only.
- **G2 sign-off 처리됨**: 복원 의미론 = 옵션 A (current_version_id 재지정 + denormalized 메타 동기화 + 새 version row 생성 X).
  closure 시 ADR #39 본문은 옵션 A의 의미론에 denormalization 동기화를 명시 — `FileUploadService:214-217` invariant 보존.

## 다음 세션 읽기 순서

1. `dev/active/m-rp-rightpanel-completion/m-rp-rightpanel-completion-plan.md`
2. 본 파일 (context.md)
3. `dev/active/m-rp-rightpanel-completion/m-rp-rightpanel-completion-tasks.md`
4. `BETA-RELEASE.md` — 본 트랙이 닫히는 베타 게이트 위치 확인
5. `frontend/src/components/files/RightPanel.tsx` — 1차 변경 대상
6. `backend/src/main/java/com/ibizdrive/file/FileVersionController.java` — 신규 endpoint 추가 위치
7. `backend/src/main/java/com/ibizdrive/file/FileDownloadController.java` + `FileDownloadService.java` — M-RP.2 패턴 source
8. `backend/src/main/java/com/ibizdrive/audit/AuditQueryFilters.java` + `AuditQueryController.java` — M-RP.4 필터 확장 위치
9. `frontend/src/lib/api.ts`, `frontend/src/lib/queryKeys.ts`, `frontend/src/hooks/usePermission.ts` — frontend 계약 위치

## 핵심 파일과 역할

| 파일 | 역할 | 트랙 단계 |
|---|---|---|
| `frontend/src/components/files/RightPanel.tsx` | 4탭 컨테이너, ComingSoon → 실 컴포넌트 교체 | M-RP.1, .3, .4 |
| `frontend/src/lib/api.ts` | `listFileVersions`, `downloadVersion`, `restoreVersion`, `listFileActivity` 추가 | M-RP.1, .2, .4 |
| `frontend/src/lib/queryKeys.ts` | `qk.fileVersions`, `qk.fileActivity` 추가 | M-RP.1, .4 |
| `frontend/src/hooks/usePermission.ts` | 기존 — M-RP.3에서 호출만 | M-RP.3 |
| `frontend/src/hooks/useFileVersions.ts` (신설) | versions 탭 데이터 훅 | M-RP.1 |
| `frontend/src/hooks/useFileActivity.ts` (신설) | activity 탭 데이터 훅 | M-RP.4 |
| `frontend/src/hooks/useRestoreVersion.ts` (신설) | 복원 mutation | M-RP.2 |
| `backend/.../file/FileVersionController.java` | 다운로드 + 복원 endpoint 신설 | M-RP.2 |
| `backend/.../file/FileDownloadService.java` | `downloadVersion` 메소드 추가 | M-RP.2 |
| `backend/.../file/FileMutationService.java` 또는 신규 | `restoreVersion` 트랜잭션 + audit emit | M-RP.2 |
| `backend/.../audit/AuditQueryFilters.java` | targetType/targetId 필드 추가 | M-RP.4 |
| `backend/.../audit/AuditQueryController.java` | @RequestParam 추가 | M-RP.4 |
| `backend/.../audit/AuditQueryService.java` | WHERE 확장 | M-RP.4 |

## 중요한 의사결정

### RP-1 — 복원 의미론 = 옵션 A (current_version_id 재지정)
- 새 version row 생성 안 함, storage 복제 없음.
- 멱등: 이미 current인 version 복원 시 200 noop, audit emit X.
- closure 시 ADR #39로 기록.
- G2 게이트에서 사용자 sign-off 필요.

### RP-2 — activity 탭 권한 정책 확장
- 기존 `AuditQueryService` 정책: ADMIN/AUDITOR 전체, MEMBER actor=self.
- 본 트랙 추가: `targetType=file` + `targetId` 지정 + 호출자가 해당 파일 `READ` 보유 → 모든 actor 노출.
- 이유: "이 파일의 활동" 탭은 history 자체가 의미.
- closure 시 ADR #40로 기록.

### 범위 확정 (사용자 명시)
- admin frontend 트랙 = 만들지 않음.
- 외부 출시는 v1.x 별도 트랙.
- 본 트랙은 사내 베타 GO를 위한 마지막 UI 보완.

## 빠른 재개 안내

1. 본 파일의 SESSION PROGRESS 마지막 항목 확인.
2. tasks.md 미완료 체크박스 첫 줄로 이동.
3. 해당 task의 참조 블록(작업 전 필독 / 원본 코드 / 구현 대상 / 검증 / 문서 반영)을 그대로 따른다.
4. 구현 시작 전 `dev/process/[session-id].md` 작성 (working_files에 실제 수정 파일만).
5. phase 종료 시 `cd backend && ./gradlew test` + `cd frontend && pnpm test --run` GREEN 확인 후 commit.
6. G2 / G4 게이트 도달 시 사용자 sign-off 대기.

## 참조

- 직전 closure 컨벤션: `dev/completed/mvp-qa-security-week-11-12/`
- 베타 게이트: `BETA-RELEASE.md`
- 핵심 원칙: `CLAUDE.md` §3 (11개 invariant)
- 자율 모드: memory `feedback_autonomous_mode`
