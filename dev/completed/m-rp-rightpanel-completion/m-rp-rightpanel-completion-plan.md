# M-RP — RightPanel 완성 + 버전 다운로드/복원 (Plan)

Last Updated: 2026-05-02
Branch base: master `a00a31c`
Track parent: 사내 베타 GO 차단 해소 — 직전 트랙(`mvp-qa-security-week-11-12`)이 BETA-RELEASE.md §1 코드 게이트는 ✓로 닫았으나, 실제 사용자가 보는 RightPanel 4탭 중 3탭은 `<ComingSoon />` placeholder. 이 트랙이 닫히면 §6 감사/권한 + §11 버전 관리가 UI 측면에서도 "보이는" 상태가 된다.

## 요약

RightPanel.tsx의 `versions`/`activity`/`permissions` 3개 placeholder 탭을 실제 데이터로 wiring하고, 버전별 다운로드/복원 backend endpoint 2개를 신설한다. 신규 endpoint는 `VERSION_DOWNLOADED` / `VERSION_RESTORED` audit emission을 함께 활성화한다 (현재 enum 정의됨, runtime emission 0).

목표는 **MVP 사내 베타에 필요한 수준**으로 한정 — admin frontend·legal hold·MFA 같은 v1.x 항목은 손대지 않는다 (사용자 명시 결정 2026-05-02).

## 현재 상태 분석 (master a00a31c)

### Frontend gap (확인 완료)

- `frontend/src/components/files/RightPanel.tsx:110-112` — `versions`/`activity`/`permissions` 탭 모두 `<ComingSoon label="..." />` 반환.
- `RightPanel.tsx:103-108` — `detail` 탭은 `useFileDetail` 훅으로 이미 wiring됨 (PanelBody 정상 동작).
- 권한 hook은 이미 존재: `frontend/src/hooks/usePermission.ts` → `api.getEffectivePermissions(nodeId)` (`frontend/src/lib/api.ts:502-517`). M-RP.3에서 그대로 재사용.
- 버전/활동용 hook 없음 — M-RP.1/M-RP.4에서 신설.

### Backend gap (확인 완료)

- `backend/src/main/java/com/ibizdrive/file/FileVersionController.java` — `GET /api/files/{fileId}/versions` (list) 만 활성. 버전별 다운로드, 복원 endpoint 없음.
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java:32-33` — `VERSION_RESTORED`/`VERSION_DOWNLOADED` 정의됨, grep 결과 emission 호출부 0건.
- `backend/src/main/java/com/ibizdrive/audit/AuditQueryFilters.java` — `fromDate`/`toDate`/`actorQuery`/`eventType` 4필드만. **`targetType`/`targetId` 필터 없음** → M-RP.4 활성 탭은 endpoint 또는 필터 확장 필요.
- 기존 다운로드 reference: `FileDownloadController.java` + `FileDownloadService.java` (RFC 5987 Content-Disposition, audit emit, READ 권한). M-RP.2의 패턴 source.

### 계약 파일

- 권한 enum: `frontend/src/types/permission.ts` ↔ `backend Permission` (docs/03 §3) — 변경 없음 가정.
- audit enum: `frontend/src/types/audit.ts` ↔ `AuditEventType` (docs/03 §4.1) — 이미 정의됨, emission만 신설.
- query keys: `frontend/src/lib/queryKeys.ts` — `fileVersions(id)` / `fileActivity(id)` 신설 필요.

## 목표 상태

1. RightPanel `versions` 탭에서 버전 리스트가 보이고, 각 버전을 다운로드 / 현재로 복원 가능.
2. RightPanel `permissions` 탭에서 현재 사용자의 effective permission 9종이 chip 형태로 표시.
3. RightPanel `activity` 탭에서 해당 파일에 대한 최근 audit 이벤트 N개가 시간역순으로 표시.
4. 버전 다운로드/복원이 `audit_log`에 `VERSION_DOWNLOADED`/`VERSION_RESTORED`로 남는다.
5. backend test GREEN, frontend test GREEN, typecheck/lint/build exit 0 — 직전 트랙 baseline(75 classes / 723 tests / 522 PASS / 201 skip, 563/563)과 동일하거나 신규 케이스만큼 증가.

## Phase별 실행 지도

phase 단위 = 독립 PR 가능한 출하 단위. G2/G4 게이트는 사용자 sign-off, 그 외는 자율 진행.

### Phase M-RP.1 — versions 탭 read-only wiring

대상: frontend only. backend 신규 코드 없음.

- `frontend/src/lib/api.ts` → `listFileVersions(fileId): Promise<FileVersionDto[]>` 추가.
- `frontend/src/types/file.ts` 또는 신규 `frontend/src/types/version.ts` → `FileVersionDto` 타입 (backend `FileVersionDto.java` 정확히 미러).
- `frontend/src/lib/queryKeys.ts` → `qk.fileVersions(id)` 추가.
- `frontend/src/hooks/useFileVersions.ts` 신설 — `useQuery({ queryKey: qk.fileVersions(id), queryFn: ... })`.
- `RightPanel.tsx:110` — `<ComingSoon />` → `<VersionsTab fileId={fileId} />` (같은 파일 내 컴포넌트 또는 별도 파일).
- 표시: 버전 번호 / 업로드 시각 / 업로드자 / 크기 / `isCurrent` badge.

검증: 단위 테스트 추가 (`useFileVersions.test.tsx`, `VersionsTab.test.tsx` 또는 RightPanel 통합 테스트 확장).

### Phase M-RP.2 — 버전별 다운로드/복원 endpoint + UI 버튼 (G2 게이트)

대상: backend 2 endpoint 신설 + frontend UI 버튼.

#### Backend

- `FileVersionController.java`:
  - `GET /api/files/{fileId}/versions/{versionId}/download` — `FileDownloadController` 패턴 정확히 답습 (RFC 5987, ETag, READ 권한). `FileDownloadService`에 `downloadVersion(fileId, versionId, actorId)` 메소드 추가 — `version.fileId == fileId` 검증 + `VERSION_DOWNLOADED` audit.
  - `POST /api/files/{fileId}/versions/{versionId}/restore` — 응답 200 `{ file: FileDto }`. 대상 version을 새 current로 만든다.

#### 복원 의미론 (트랙 결정 RP-1)

**옵션 A (선택)**: `current_version_id`만 재지정. 새 version row 생성 안 함. storage I/O 0.
- 장점: KISS, idempotent, 빠름.
- 단점: 복원 사실이 file table만 보고는 audit log를 봐야 알 수 있음.

**옵션 B**: 대상 version의 storage_key/content를 복사한 새 version (`versionNumber = max+1`)을 생성하고 current로 지정.
- 장점: 버전 히스토리가 시간순으로 단조 증가.
- 단점: storage 복제 + DB INSERT 추가, race condition 표면적 ↑.

**현 결정 = A**. 이유: docs/02 §6 "complex restore semantics" 명시 없음, 사내 베타 사용자 학습곡선 최소화. closure ADR로 기록 (#39 예정).

#### Backend (계속)

- `FileMutationService` 또는 신규 `FileVersionMutationService`에 `restore(fileId, versionId, actorId)`:
  - `@Transactional` + `SELECT FOR UPDATE` (file row).
  - `version.fileId == fileId` 검증, soft-delete 상태 차단.
  - `file.currentVersionId == versionId`이면 멱등 200 (audit emit X — duplicate 이벤트 회피).
  - 그 외 `currentVersionId` 업데이트 + `VERSION_RESTORED` audit (before/after에 versionId 양쪽).
- 권한: `@PreAuthorize("hasPermission(#fileId, 'file', 'EDIT')")` — restore는 mutation. download는 `READ`.

#### Frontend

- `api.downloadVersion(fileId, versionId)` — fetch + blob, RightPanel UI는 `<a>` 또는 window.location 사용.
- `api.restoreVersion(fileId, versionId)` — POST.
- `useRestoreVersion` mutation hook — `invalidations.afterRename`과 유사하게 `qk.fileDetail(id)` + `qk.fileVersions(id)` invalidate.
- `VersionsTab` 각 row에 "다운로드" / "현재로 복원" 버튼. 현재 버전은 "복원" 비활성.

#### G2 게이트 (사용자 sign-off 필요)

- 복원 의미론 옵션 A 확정 OK?
- ADR #39로 기록 OK?

### Phase M-RP.3 — permissions 탭 wiring

대상: frontend only.

- `RightPanel.tsx:112` — `<ComingSoon />` → `<PermissionsTab fileId={fileId} />`.
- `usePermission(fileId)` 호출 (이미 구현됨).
- 표시: 9개 권한 enum을 chip으로, 보유 = 강조 / 미보유 = 흐리게. Read-only (grant UI는 별도 트랙).

검증: 단위 테스트 추가 + RightPanel 통합 테스트 확장.

### Phase M-RP.4 — activity 탭 wiring (G4 게이트)

대상: backend 필터 확장 + frontend.

#### Backend

- `AuditQueryFilters` — `targetType`(String) + `targetId`(UUID) 필드 추가. 기본 null = 미필터.
- `AuditQueryService` — 두 필드 SQL WHERE 추가.
- `AuditQueryController` — `@RequestParam` 추가.
- 기존 M12 audit logs 페이지는 새 필터 미사용으로 무영향 (additive change).

#### 권한 정책 (트랙 결정 RP-2)

기존 `AuditQueryController`는 ADMIN/AUDITOR 전체, MEMBER는 actor_id=self. 파일 활동 탭은 그 정책으로는 MEMBER가 자기 액션만 보고 다른 사람의 file edit/share는 못 봄 → UX 결함.

**결정 = 추가 정책**: `targetType=file` + `targetId` 지정 시 — 호출자가 해당 파일에 `READ` 권한 보유 시 모든 actor의 이벤트 노출. 이유: 활동 탭은 "이 파일의 history" 자체가 의미. closure ADR #40로 기록.

#### Frontend

- `api.listFileActivity(fileId, page, pageSize)` — 기존 `AuditQueryController` 재사용, query에 `targetType=file&targetId=<id>` 추가.
- `qk.fileActivity(id, page, pageSize)`.
- `useFileActivity(fileId)` 훅.
- `RightPanel.tsx:111` → `<ActivityTab fileId={fileId} />`. 시간역순 list, eventType + actor + occurredAt + 짧은 description.

#### G4 게이트 (트랙 closure)

- 직전 트랙(`mvp-qa-security`)과 동일한 closure 절차: docs/progress.md 회고 + ADR index 업데이트 + dev/completed/ archive + master 단일 commit.
- BETA-RELEASE.md §6 (감사/권한)와 §11 추가 라인업데이트.

## Acceptance Criteria

각 phase별 / 트랙 종합:

- [ ] M-RP.1: 버전 탭에서 list 표시, 현재 버전 isCurrent badge, 0 버전 케이스 빈 상태 메시지.
- [ ] M-RP.2: 버전 다운로드 — Content-Disposition: attachment, ETag = versionId, audit `VERSION_DOWNLOADED` emit. 복원 — current_version_id 변경, 멱등 (이미 current면 noop), audit `VERSION_RESTORED` emit.
- [ ] M-RP.3: permissions 탭에서 9개 chip 렌더링, 현재 사용자 권한 정확.
- [ ] M-RP.4: activity 탭에서 해당 파일 audit 이벤트 N개, 시간역순.
- [ ] backend test GREEN: `cd backend && ./gradlew test` — 기존 PASS 수 + 신규 케이스만큼 증가, fail 0.
- [ ] frontend test GREEN: `cd frontend && pnpm test --run` — 기존 PASS + 신규.
- [ ] typecheck/lint/build exit 0.
- [ ] BETA-RELEASE.md §1 게이트는 여전히 ✓ 유지 (회귀 0).

## 검증 게이트

- 매 phase 종료 직전: `cd backend && ./gradlew test` + `cd frontend && pnpm test --run` 둘 다 GREEN.
- M-RP.2 종료 시: 추가로 통합 테스트 — 다운로드 응답 헤더 정확성, 복원 후 `current_version_id` 변경 확인.
- M-RP.4 종료 시: AuditQueryFilters 변경이 M12 audit logs 페이지에 회귀 없음 검증 (M12 통합 테스트 재실행).
- G4 closure 직전: docs/progress.md + ADR index + BETA-RELEASE.md 갱신 + master commit.

## 리스크와 완화 전략

| 리스크 | 영향 | 완화 |
|---|---|---|
| 복원 옵션 A → 사용자가 옵션 B를 기대 | UX 혼란 | G2 게이트 사용자 sign-off, ADR #39 기록 |
| AuditQueryFilters 변경이 M12에 회귀 | 기존 audit UI 깨짐 | additive nullable 필드, M12 통합 테스트로 회귀 차단 |
| 활동 탭 권한 정책이 너무 관대 (RP-2) | 정보 노출 | 트랙 결정 RP-2 ADR #40 기록, READ 권한 보유 + targetId 매칭 시만 활성, 그 외 기존 정책 유지 |
| RightPanel 4 탭 동시 mount → 4 query 동시 실행 | 네트워크 burst | 탭 비활성 시 query disabled (`enabled: tab === 'versions'`), 활성 탭만 fetch |
| 버전 다운로드 시 file/version 일치 검증 누락 | 다른 파일 버전 다운로드 가능 | `FileDownloadService.downloadVersion`에서 `version.fileId == fileId` 명시 검증, 위반 시 404 |

## 단계 외 결정 사항 (현 시점)

- 버전 삭제 / 압축 / 단일 버전 audit 상세 = 본 트랙 범위 외 (v1.x).
- 버전별 thumbnail / preview = v1.x.
- 활동 탭 무한 스크롤 = MVP는 페이지 1만 표시 (page=1, pageSize=20). 더보기 버튼은 v1.x.
- permissions 탭에서 grant/revoke UI = `m8-permission-ui` 트랙 영역, 본 트랙은 read-only.

## 참조

- BETA-RELEASE.md (직전 트랙 closure)
- dev/completed/mvp-qa-security-week-11-12/ (closure 컨벤션)
- dev/completed/a5-file-versions/ (버전 schema 결정)
- dev/completed/a11-effective-permissions-endpoint/ (권한 endpoint)
- dev/completed/m12-audit-ui-closure/ (audit query 패턴)
- docs/01-frontend-design.md §11, §14, §15, §17.5
- docs/02-backend-data-model.md §6.1, §7.6, §7.10, §7.12
- docs/03-security-compliance.md §3, §4
- CLAUDE.md §3 11개 핵심 원칙
