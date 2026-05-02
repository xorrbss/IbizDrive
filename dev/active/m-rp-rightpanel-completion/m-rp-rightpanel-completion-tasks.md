# M-RP — Tasks

Last Updated: 2026-05-02

## Phase 상태

- [x] **M-RP.1** — versions 탭 read-only wiring (frontend only) ✅ 2026-05-02 (75 files / 610 tests)
- [ ] **M-RP.2** — 버전별 다운로드/복원 endpoint + UI (G2 게이트)
- [ ] **M-RP.3** — permissions 탭 wiring (frontend only)
- [ ] **M-RP.4** — activity 탭 wiring + AuditQueryFilters 확장 (G4 closure)

---

## M-RP.1 — versions 탭 read-only wiring

### M-RP.1.1 — backend wire format 미러 type 추가

- [x] `frontend/src/types/version.ts` 신설 — `FileVersionDto` + `VersionScanStatus` 타입.

**작업 전 필독**
- `backend/src/main/java/com/ibizdrive/file/dto/FileVersionDto.java` (record 시그니처 1:1 미러 — camelCase).

**원본 코드 참조**
- `backend/.../file/dto/FileVersionDto.java:25-50`

**구현 대상**
- TypeScript record 구조: `{ id, versionNumber, sizeBytes, checksumSha256, mimeType, scanStatus, uploadedBy, uploadedAt, comment, isCurrent }`. 모든 옵셔널은 `JsonInclude.NON_NULL`이므로 `?:`로 노출.

**검증 참조**
- `cd frontend && pnpm typecheck` exit 0.

**문서 반영**
- 없음 (계약은 backend record가 진실).

### M-RP.1.2 — api.listFileVersions + queryKey 추가

- [x] `frontend/src/lib/api.ts` — `listFileVersions(fileId: string): Promise<FileVersionDto[]>` 추가.
- [x] `frontend/src/lib/queryKeys.ts` — `qk.fileVersions(id)` 추가.
- [x] `frontend/src/lib/api.versions.test.ts` — 9 케이스 (URL, encoding, envelope, 401/403/404/5xx, queryKey shape).

**작업 전 필독**
- `frontend/src/lib/api.ts:502-517` (`getEffectivePermissions` 패턴, fetch + 401 handling).
- `frontend/src/lib/queryKeys.ts:37` (`fileDetail` 키 패턴).

**원본 코드 참조**
- backend endpoint: `GET /api/files/{fileId}/versions` → 응답 `{ versions: FileVersionDto[] }` (`FileVersionController.java:55-68`).

**구현 대상**
- `listFileVersions`는 envelope 풀어서 `versions` 배열만 반환.
- queryKey: `qk.fileVersions = (id: string) => [...qk.files(), 'versions', id] as const`.

**검증 참조**
- `frontend/src/lib/api.permissions.test.ts` 패턴 참조 — `api.listFileVersions.test.ts` 추가.

**문서 반영**
- 없음.

### M-RP.1.3 — useFileVersions 훅

- [x] `frontend/src/hooks/useFileVersions.ts` 신설.
- [x] `frontend/src/hooks/useFileVersions.test.tsx` — 3 케이스 (null disabled, 정상 fetch, error).
- 결정: `enabled` 인자 미도입 (RightPanel 조건부 렌더가 mount/unmount 책임 — KISS).

**작업 전 필독**
- `frontend/src/hooks/useFileDetail.ts` (동일 패턴).
- `frontend/src/hooks/usePermission.ts` (enabled 가드 패턴).

**구현 대상**
- `useQuery({ queryKey: qk.fileVersions(fileId), queryFn: () => api.listFileVersions(fileId), enabled: !!fileId })`.
- staleTime: detail 훅과 동일.

**검증 참조**
- `useFileVersions.test.tsx` — mock api, 캐시 키 매칭, enabled 동작.

### M-RP.1.4 — VersionsTab 컴포넌트 + RightPanel wiring

- [x] `frontend/src/components/files/VersionsTab.tsx` 신설 — 4상태(로딩/에러/빈/리스트) + isCurrent badge + comment.
- [x] `RightPanel.tsx:113` `<ComingSoon />` → `tab === 'versions' && <VersionsTab fileId={fileId} />`.
- [x] `RightPanel.test.tsx` 갱신 — listFileVersions mock 추가 + 4 신규 케이스.

**작업 전 필독**
- `RightPanel.tsx:103-108` (PanelSkeleton/PanelError/PanelBody 패턴).

**구현 대상**
- 로딩: PanelSkeleton 재사용.
- 에러: PanelError 재사용 또는 단순 메시지.
- 빈: "버전이 없습니다" 빈 상태.
- 정상: `<ul>` versionNumber desc, 각 row에 `versionNumber` / `formatSize(sizeBytes)` / `formatDate(uploadedAt)` / `uploadedBy` / `isCurrent` badge.
- 가상화 불필요 (보통 < 50 버전).
- 탭 비활성 시 fetch 안 함 (`enabled: tab === 'versions'`).

**검증 참조**
- 통합 테스트: RightPanel에서 versions 탭 클릭 → fetch 호출 → row 렌더 확인.
- `cd frontend && pnpm test --run` GREEN.
- `cd frontend && pnpm typecheck && pnpm lint && pnpm build` exit 0.

**문서 반영**
- `docs/01-frontend-design.md` §11/§17.5 RightPanel 섹션에 versions 탭 활성화 기록 (1줄).

### M-RP.1 검증 게이트

- [~] `cd backend && ./gradlew test` — backend 변경 0 → 본 phase 미실행. M-RP.2에서 baseline 검증.
- [x] `cd frontend && pnpm test --run` — **75 files / 610 passed**, fail 0.
- [x] `pnpm typecheck` exit 0.
- [x] `pnpm lint` exit 0.
- [x] `pnpm build` exit 0.
- [ ] commit: `feat(m-rp.1): RightPanel versions 탭 wiring` — phase별 commit은 M-RP.2 G2 sign-off 후 일괄 또는 단계별 결정.

---

## M-RP.2 — 버전별 다운로드/복원 endpoint + UI (G2 게이트)

### G2 게이트 항목

- [ ] 사용자 sign-off: 복원 의미론 = 옵션 A (current_version_id 재지정, 새 version 생성 안 함).
- [ ] 사용자 sign-off: closure 시 ADR #39로 기록.

### M-RP.2.1 — FileDownloadService.downloadVersion + 다운로드 endpoint

- [ ] `FileDownloadService.java`에 `downloadVersion(UUID fileId, UUID versionId, UUID actorId): DownloadHandle` 추가.
- [ ] `FileVersionController.java`에 `GET /api/files/{fileId}/versions/{versionId}/download` 추가.

**작업 전 필독**
- `backend/.../file/FileDownloadController.java:64-84` (RFC 5987, ETag, READ 가드).
- `backend/.../file/FileDownloadService.java:70-114` (트랜잭션 + audit emit + storage stream).

**원본 코드 참조**
- `FileDownloadService:99-113` (`emitDownloadAudit`) — `VERSION_DOWNLOADED` 변형 추가.

**구현 대상**
- `downloadVersion`:
  - file active 검증 (`findByIdAndDeletedAtIsNull`).
  - version load (`fileVersionRepository.findById`) + `version.getFileId().equals(fileId)` 검증, 위반 시 `FileNotFoundException`.
  - storage open → `DownloadHandle` 반환.
  - audit emit `VERSION_DOWNLOADED`, `after = { versionId }`.
- controller: `@PreAuthorize("hasPermission(#fileId, 'file', 'READ')")`. 응답 헤더는 `FileDownloadController` 동일 (`buildContentDisposition` 메소드 추출 또는 동등 재사용).

**검증 참조**
- `FileDownloadServiceTest`/`FileDownloadControllerIT` 패턴 답습.
- 통합 테스트: 다른 파일의 version으로 호출 → 404. 정상 호출 → 200 + ETag = versionId, audit row 1 새로 생성.

**문서 반영**
- `docs/02-backend-data-model.md` §7.6 — 새 endpoint 라인업.
- `docs/03-security-compliance.md` §4.1 — `VERSION_DOWNLOADED` emit 활성화.

### M-RP.2.2 — restoreVersion service + endpoint

- [ ] `FileVersionMutationService.java` 신설 또는 `FileMutationService` 확장 — `restoreVersion(fileId, versionId, actorId): FileItem`.
- [ ] `FileVersionController.java`에 `POST /api/files/{fileId}/versions/{versionId}/restore` 추가.

**작업 전 필독**
- `backend/.../file/FileMutationService.java` — `@Transactional` + `SELECT FOR UPDATE` 패턴.
- `backend/.../file/FileRepository.java` — `findByIdAndDeletedAtIsNullForUpdate` 또는 동등 메소드 확인.

**구현 대상**
- 트랜잭션 안에서:
  - file active + lock.
  - version load + `fileId` 매칭 검증.
  - `file.currentVersionId == versionId`이면 멱등: audit emit X, 현 file 반환.
  - 그 외 `file.currentVersionId = versionId` 업데이트, save.
  - audit `VERSION_RESTORED`, `before = { versionId: oldCurrent }`, `after = { versionId: newCurrent }`.
- controller: `@PreAuthorize("hasPermission(#fileId, 'file', 'EDIT')")`. 응답 200 `{ file: FileDto }`.

**검증 참조**
- `FileVersionMutationServiceTest`:
  - 정상 복원 → currentVersionId 변경 + audit 1건.
  - 같은 versionId 재호출(멱등) → audit 추가 0.
  - 다른 파일 versionId → 404.
  - soft-deleted file → 404.
  - 권한 없음 (READ만 보유) → 403.

**문서 반영**
- `docs/02-backend-data-model.md` §7.6 — 새 endpoint 라인업.
- `docs/03-security-compliance.md` §4.1 — `VERSION_RESTORED` emit 활성화.
- closure 시 ADR #39 본문 작성 (옵션 A 선택 근거).

### M-RP.2.3 — frontend api + mutation hook + UI 버튼

- [ ] `api.downloadVersion(fileId, versionId)` — 단순 `window.location` 또는 `<a href download>`로 충분 (별도 fetch 불필요, 브라우저가 stream 처리).
- [ ] `api.restoreVersion(fileId, versionId): Promise<FileItem>`.
- [ ] `frontend/src/hooks/useRestoreVersion.ts` — mutation, onSuccess에서 `qk.fileVersions(id)` + `qk.fileDetail(id)` invalidate.
- [ ] `VersionsTab` 각 row에 "다운로드" + "복원" 버튼. 현재 버전은 "복원" 비활성.

**작업 전 필독**
- `frontend/src/hooks/useRenameFile.ts` 또는 동등 mutation 패턴.

**검증 참조**
- `useRestoreVersion.test.tsx` — invalidation 매트릭스.
- `VersionsTab.test.tsx` — 현재 버전 복원 버튼 disabled, 비-current 클릭 시 mutation 호출.

**문서 반영**
- `docs/01-frontend-design.md` §17.5 RightPanel — versions 탭 액션 기록.

### M-RP.2 검증 게이트

- [ ] backend test GREEN (신규 케이스 포함).
- [ ] frontend test GREEN.
- [ ] typecheck/lint/build exit 0.
- [ ] audit log 수동 확인: 복원 1회 + 다운로드 1회 → audit_log 2 row.
- [ ] commit: `feat(m-rp.2): version download/restore + audit emit`.

---

## M-RP.3 — permissions 탭 wiring (frontend only)

### M-RP.3.1 — PermissionsTab 컴포넌트

- [ ] `RightPanel.tsx:112` 또는 `frontend/src/components/files/PermissionsTab.tsx`.

**작업 전 필독**
- `frontend/src/hooks/usePermission.ts` (이미 구현, `useQuery({ queryKey: qk.permissions(nodeId), ... })`).
- `frontend/src/types/permission.ts` — Permission enum.

**구현 대상**
- `usePermission(fileId)` 호출 → `Permission[]` 또는 loading/error.
- 9개 권한 enum 모두 chip으로 렌더, 보유는 강조 / 미보유는 흐리게.
- 탭 비활성 시 fetch 안 함 (`enabled: tab === 'permissions'`).

**검증 참조**
- `PermissionsTab.test.tsx` — mock usePermission, 보유/미보유 chip 시각 구분 확인.
- 회귀: `usePermission.test.tsx` 기존 케이스 GREEN 유지.

**문서 반영**
- `docs/01-frontend-design.md` §14 (권한) — RightPanel permissions 탭 활성 기록.

### M-RP.3 검증 게이트

- [ ] frontend test GREEN.
- [ ] typecheck/lint/build exit 0.
- [ ] commit: `feat(m-rp.3): RightPanel permissions 탭 wiring`.

---

## M-RP.4 — activity 탭 wiring + AuditQueryFilters 확장 (G4 closure)

### M-RP.4.1 — backend AuditQueryFilters + Service + Controller 확장

- [ ] `AuditQueryFilters.java` — `targetType`(String, nullable), `targetId`(UUID, nullable) 필드 추가. `empty()` 갱신.
- [ ] `AuditQueryService.java` — WHERE에 `(targetType IS NULL OR audit_log.target_type = :targetType)` + `(targetId IS NULL OR audit_log.target_id = :targetId)` 추가.
- [ ] `AuditQueryController.java` — `@RequestParam` 2개 추가.
- [ ] 권한 정책 (RP-2): `targetType="file"` + `targetId` 지정 시 호출자가 해당 파일 `READ` 보유면 actor 제한 우회. 그 외 기존 정책 유지.

**작업 전 필독**
- `backend/.../audit/AuditQueryService.java` — 기존 권한 분기 + SQL 빌드.
- `backend/.../audit/AuditLogRepository.java` — 쿼리 메소드.

**원본 코드 참조**
- `AuditQueryFilters:18-27`, `AuditQueryController:36-60`.

**구현 대상**
- AuditQueryFilters는 record라 필드 추가 시 모든 호출부 수정 필요. 검색해서 fix.
- service의 권한 분기:
  ```
  if (filters.targetType() != null && filters.targetId() != null
      && "file".equals(filters.targetType())
      && callerHasReadOnFile(filters.targetId(), callerId, callerRole)) {
    // actor filter 우회
  } else { 기존 분기 }
  ```
- `callerHasReadOnFile`는 `IbizDrivePermissionEvaluator` 또는 `PermissionService` 재사용.

**검증 참조**
- `AuditQueryServiceTest`:
  - 새 필터 nullable 미지정 → 기존 결과 동일 (회귀 0).
  - targetType=file + targetId + READ 보유 → 다른 actor 이벤트 노출.
  - targetType=file + targetId + READ 미보유 → 403 또는 빈 결과 (정책 결정).
- `AuditQueryControllerIT` — query string 통합.
- M12 audit logs 페이지 회귀: `cd frontend && pnpm test --run` GREEN.

**문서 반영**
- `docs/02-backend-data-model.md` §7.12 — 새 필터 명시.
- `docs/03-security-compliance.md` §4 — RP-2 정책 명시.
- closure 시 ADR #40 본문.

### M-RP.4.2 — frontend api.listFileActivity + 훅 + ActivityTab

- [ ] `api.listFileActivity(fileId, page=1, pageSize=20): Promise<AuditLogPageDto>` — `?targetType=file&targetId=<id>&page=...&pageSize=...`.
- [ ] `qk.fileActivity(id, page, pageSize)`.
- [ ] `useFileActivity(fileId)` 훅.
- [ ] `RightPanel.tsx:111` → `<ActivityTab fileId={fileId} />`.

**작업 전 필독**
- `frontend/src/lib/api.ts` 의 audit 관련 fetch 패턴.
- `frontend/src/lib/queryKeys.ts:83-86` (`auditLogs` 키 패턴).

**구현 대상**
- ActivityTab: 시간역순 list, 각 row `eventType` + `actor` 또는 `actorId` (이름 lookup은 v1.x) + `occurredAt`. 페이지 1만, "더보기" v1.x.
- 탭 비활성 시 fetch 안 함.

**검증 참조**
- `useFileActivity.test.tsx`, `ActivityTab.test.tsx`.
- 통합: M12 페이지 영향 없음.

**문서 반영**
- `docs/01-frontend-design.md` §17.5 — activity 탭 활성화.

### M-RP.4.3 — Closure (G4 게이트)

- [ ] `BETA-RELEASE.md` §6 (감사/권한) + 신규 §11 (버전 관리) 갱신.
- [ ] `docs/progress.md` 상단에 `2026-MM-DD — M-RP 트랙 closure` 회고 + 핵심 결정.
- [ ] `docs/00-overview.md` §5 ADR — #39 (복원 의미론) + #40 (활동 탭 권한 정책) 추가.
- [ ] `dev/active/m-rp-rightpanel-completion/` → `dev/completed/m-rp-rightpanel-completion/` archive.
- [ ] master 단일 commit: `feat: M-RP — RightPanel 4탭 완성 + 버전 다운로드/복원 (#XX)`.

### M-RP.4 / 트랙 검증 게이트 (최종)

- [ ] `cd backend && ./gradlew test` — 직전 baseline + 신규, fail 0.
- [ ] `cd frontend && pnpm test --run` — 직전 baseline + 신규.
- [ ] typecheck/lint/build exit 0.
- [ ] BETA-RELEASE.md §1 코드 게이트 ✓ 유지.
- [ ] dev/process/[session-id].md 삭제 확인.

---

## 미완료 항목 참조 매트릭스

전 phase 미완료. 첫 진입 = M-RP.1.1 (frontend FileVersionDto 타입 추가).
