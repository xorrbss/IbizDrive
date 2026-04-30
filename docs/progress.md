# 진행 상황 (Progress Log)

> 각 세션이 완료될 때마다 **최상단에 추가**합니다. 기존 내용은 보존.
> 양식: `CLAUDE.md §7` 또는 각 BRIEF의 회고 섹션 참조.

---

## 2026-04-29 — 🏁 M9 휴지통 + 5초 Undo + /trash 페이지

### 범위
soft-delete 기반 휴지통 (frontend-only mock). M9.0 bootstrap → M9.1 mock api soft-delete → M9.2 hooks → M9.3 BulkActionBar Undo → M9.4 /trash + Sidebar TrashLink.

### 변경
- **types/api (M9.1)**: `FileItem.deletedAt? + originalParentId?` 추가. `api.deleteBulk` hard splice → soft delete (`deletedAt = now`, `originalParentId = parentId` 스냅샷). `api.listTrash` (deletedAt 내림차순) / `restoreBulk` (clear deletedAt + parentId restore from originalParentId, root fallback if parent missing) / `purgeBulk` (hard splice) 신설. `getFilesInFolder`/`searchFiles`에 `!f.deletedAt` 필터 추가.
- **queryKeys (M9.1)**: `qk.trash() / qk.trashList()` 추가. `invalidations.afterDelete` → `[filesListPrefix(folder), trash(), search()]` 확장. `afterRestore(opts.folderIds[])` / `afterPurge` 신설.
- **hooks (M9.2)**: `useTrashList` (staleTime: 0) + `useRestoreBulk({ids, originalParentIds?})` + `usePurgeBulk({ids})` — 옵션 onSuccess/onError forward, invalidations 자동 호출.
- **UI (M9.3)**: `BulkActionBar` Delete onSuccess 시 `toast.success(..., {duration: 5000, action: {label: '되돌리기', onClick: restoreMut.mutate({ids, originalParentIds:[folderIdAtStart]})}})`. onError 시 `toast.error`.
- **UI (M9.4)**: `/app/(explorer)/trash/page.tsx` (section header + TrashTable) + `TrashTable` (role=grid, 컬럼: 이름/삭제 시각/원위치/액션, 로딩/에러/빈 분기, 복원 + 영구삭제 confirm) + `TrashLink` (Sidebar 하단, usePathname 기반 active aria-current). `(explorer)/layout.tsx`에 `<TrashLink />` 추가 (mt-auto pt-2 border-t).

### 검증
- `npm run test`: 44 files / 371 tests passed (M9 신규 27 — api.trash 7 + qk/invalidations 4 + hooks 4 + BulkActionBar Undo 3 + TrashTable 6 + TrashLink 3).
- `npm run typecheck`: clean.
- `npm run lint`: clean.

### 핵심 결정
- **소프트 삭제 = mock 한정** — backend A6 cascade 정책(folder 단위)과 frontend mock(file 단위)은 별개 트랙. 백엔드 file delete endpoint 신설 시 api.deleteBulk fetch만 교체, hook/UI 무수정.
- **Undo는 BulkActionBar 경유 시만** — FileTable Delete 단축키 Undo는 KISS로 분리 (별도 PR).
- **restoreBulk parent fallback** — `originalParentId` 가리키는 폴더가 (사용자 액션으로) 사라진 경우 root로 복원. backend는 `FolderNotFoundException`(A6)으로 강제하지만 frontend mock은 UX 우선.
- **vi.hoisted로 옵션 캡처** — `useDeleteBulk(opts)` 내부에서 `optionsCapture.current = opts`로 저장 → 테스트가 onSuccess 콜백 직접 트리거 가능 (mutate spy + onSuccess 분리 검증).

### 다음 세션 컨텍스트
- 시퀀스 다음: **M8 share dialog + 권한 확장** (docs/01 §14, backend A3 권한 매트릭스 활용).

---

## 2026-04-29 — 🏁 M11 검색 (debounce + normalize + AbortController)

### 범위
TopBar 글로벌 검색 (frontend-only). M11.0 bootstrap → M11.1 lib infra → M11.2 useSearch → M11.3 SearchBar/SearchResults UI.

### 변경
- **lib infra (M11.1)**: `qk.search()` / `qk.searchResults(normalized, filters)` 추가; `api.searchFiles({q, filters}, {signal})` mock (200ms latency + `normalizeForSearch` + AbortSignal); `useDebounce<T>(value, delayMs)` 신설.
- **useSearch (M11.2)**: 300ms debounce → `normalizeForSearch` → `useQuery` (`enabled: normalized.length>=2`, `placeholderData: keepPreviousData`, `staleTime: 30s`, signal forward).
- **UI (M11.3)**: `SearchBar` (searchbox role, `/` 단축키 focus via `FOCUS_SEARCH_EVENT`, Esc → clear+close, blur 120ms 지연); `SearchResults` (1자/에러/로딩/빈/파일·폴더 분기 — 파일 → `useOpenFile().open(id)`, 폴더 → `router.push(buildCanonicalPath)`); `TopBar` 좌측 슬롯 통합 (justify-end → justify-between).

### 검증
- `npm run test`: 40 files / 344 tests passed (신규 28 — useDebounce 4 + api.search 6 + qk.search 2 + useSearch 5 + SearchBar 4 + SearchResults 7).
- `npm run typecheck`: clean.
- `npm run lint`: clean (eslint exit 0).

### 핵심 결정
- mock api는 setTimeout 200ms + AbortSignal 처리 → fake-timer 환경에서는 `vi.spyOn(api, 'searchFiles').mockResolvedValue(...)`로 즉시 resolve, 별도 integration 1건만 real timers로 실제 mock 동작 검증.
- `useGlobalShortcuts`는 이미 `/` 키 → `FOCUS_SEARCH_EVENT` 디스패치 + input focus 가드 보유 → 추가 작업 없음.
- 백엔드 `/api/search` 도입 시 api 내부 fetch 교체만으로 호환 (계약 동일).

### 다음 세션 컨텍스트
- 시퀀스 다음: M9 휴지통 페이지 + Undo (api.listTrash mock 이미 존재 가능성 확인 필요).

---

## 2026-04-30 — 🏁 A6 마일스톤 종료 (Folder Delete/Restore + Descendant Cascade)

### 범위

A6.0 (docs/02 §7.5 cascade 정책 + restore-self 본문 정합, no-code) → A6.1+A6.2+A6.3 통합 (`FolderMutationService.delete/restore` + 후손 BFS cascade(folder + file batch UPDATE) + `FolderController` DELETE/restore endpoint + `FolderRestoreConflictException` + `RESTORE_CONFLICT` envelope) → refactor (cascade 후손 `originalParentId` 스냅샷 + restore가 soft-deleted parent 위로 시도 시 일관 NotFound) → A6.4 (PR #15 squash-merge `4111990` + dev-docs archive).

### 회고

- **commits**: 4 on top of A5 close `d23270e` (worktree branch `feature/a6-folder-mutation-delete`) → squash-merge `4111990` on `master`. PR #15 single, CI green (backend junit 3m9s + frontend vitest 1m10s 모두 SUCCESS).
- **production 파일**: 5 수정 + 1 신설 (`FolderMutationService` +182 lines, `FolderRepository` +36, `FolderController` +endpoints, `FileRepository` +24, `GlobalExceptionHandler` +handler, `FolderRestoreConflictException` NEW). frontend 무수정.
- **test 파일**: 2 수정 (`FolderMutationServiceTest` +5 cases — cascade/not-found/restore/conflict/cascade-child-restore-not-found, `FolderControllerTest` +2 cases — delete/restore endpoint).
- **endpoint 신규**: 2개 — `DELETE /api/folders/{id}` (204) + `POST /api/folders/{id}/restore` (200).
- **envelope code**: `RESTORE_CONFLICT` 매핑 신설 (docs/02 §8 line 1221에 이미 등록되어 있어 본문 patch 불필요).
- **A4 회귀 0**: PermissionEvaluatorIntegrationTest 13/13 GREEN 유지.

### 핵심 결정 (A6 트랙, 확정)

1. **Audit root만** — cascade 후손 FOLDER_DELETED 미발행, root 1건에 `descendantFolders/Files` 카운트 보존 (audit_log 폭증 회피, docs/02 §7.5 + CLAUDE.md §3 원칙 8).
2. **Restore self만** — 자기 자신만 복원, 후손 휴지통 잔존. `original_parent_id`가 soft-deleted면 404 ("부모 먼저 복원" UX 강제).
3. **Service 레벨 BFS** — `assertNoCycle` 패턴 일관성, MAX_CASCADE_NODES=100k 안전 한도. 성능 이슈 시 WITH RECURSIVE 전환 + ADR.
4. **Cascade 후손 originalParentId 스냅샷** — `FileRepository.softDeleteByFolderIds`가 `originalFolderId = folderId`를 set하는 것과 대칭. 후손 폴더도 개별 restore 시도 가능 (소프트-삭제된 부모 위로 복원 시도하면 `FolderNotFoundException`).
5. **Integration class 미신설 (KISS)** — `FolderControllerIntegrationTest` 별도 작성 안 함. PermissionEvaluatorIntegrationTest 13/13가 SpEL `hasPermission(folder, DELETE)` 동일 evaluator 경로 보장 + 회귀 0이 곧 권한 매트릭스 정합.
6. **단일 PR / 통합 commit** — A6.1~A6.3 테스트 상호의존(controller endpoint 미구현 시 controller test 컴파일 실패) → 단일 feature commit으로 처리.
7. **File mutation 트랙은 cascade 미참여** — A4.8(`4e720eb`)에서 닫힘. `FileMutationService.delete` 미호출, `FileRepository.softDeleteByFolderIds` batch UPDATE만 사용 (audit 정책 일관성).

### accepted-deviation (후속 backlog)

- **Hard purge job** — `purge_after` 경과 row 영구 삭제 + S3 객체 삭제. docs/04 §13 배치 트랙.
- **후손 cascade restore endpoint** — `?cascade=true` 또는 별도 path. UX 결정 후 신설.
- **Frontend 휴지통 UI** (docs/01 §13) — backend 계약 안정화 완료, 진입 가능.

### DoD 10/10

1. ✅ `FolderMutationService.delete` + 후손 BFS cascade + `MAX_CASCADE_NODES` 안전 한도
2. ✅ `FolderRepository.softDeleteByIds` (`originalParentId` 스냅샷 포함) + `FileRepository.softDeleteByFolderIds`
3. ✅ Audit root만 발행, `after_state.descendantFolders/Files` 카운트 보존
4. ✅ `FolderMutationService.restore` + parent 활성 검증 + `existsActiveByParentAndNormalizedNameExcludingId` 충돌 검사
5. ✅ `FolderRestoreConflictException` 신설 + `GlobalExceptionHandler` → 409 `RESTORE_CONFLICT` 매핑
6. ✅ `FolderController.delete` (204) + `restore` (200) endpoint + `@PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")`
7. ✅ FolderMutationServiceTest 5 cases + FolderControllerTest 2 cases GREEN
8. ✅ A4 PermissionEvaluatorIntegrationTest 13/13 GREEN, frontend test 회귀 0
9. ✅ docs/02 §7.5 cascade 정책 + restore-self 본문 정합 (line 881~922)
10. ✅ PR #15 CI green + master squash-merge `4111990` + dev-docs `dev/active/a6-folder-mutation-delete/` → `dev/completed/`

### 다음 단계

- **A7 후보**: hard purge job (docs/04 §13) 또는 frontend 휴지통/탐색기 UI (docs/01 §13).
- **docs/03 §5~§8**: 저장소 보안 / Legal Hold / 데이터 보호 / 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지 / 쿼터 / 백업.

---

## 2026-04-29 — A6.0 docs/02 §7.5 cascade 정책 + restore-self 명시 (no-code)

### 범위
A6 마일스톤 진입점. folder delete/restore 트랙의 §7.5 응답 본문 정합 patch.

### 변경
- `docs/02 §7.5` `DELETE /api/folders/:id` 행 SoftDel 컬럼에 `(재귀: 후손 폴더/파일 cascade — root 1회 audit)` 보강.
- `docs/02 §7.5` `POST /api/folders/:id/restore` 행 SoftDel 컬럼에 `(자기 자신만 복원, 후손 잔존)` 보강.
- `docs/02 §7.5` 응답 본문(line ~915) DELETE/restore TX 의사코드에 cascade BFS + audit root-only + restore-self 정책 명시.

### 검증
- A6.0 commit 자체는 docs 1파일만 staged (코드 0줄).
- §8 `RESTORE_CONFLICT`는 line 1221에 이미 등록(A4 마일스톤 closure 시점) — 별도 patch 불필요.
- 다음 phase A6.1 `FolderMutationService.delete` 구현이 본 §7.5 본문과 1:1 정합.

---

## 2026-04-29 — 🏁 A5 마일스톤 종료 (FileVersion Domain — entity + GET /versions)

### 범위

A5.0 (docs/02 §7.6 응답 스키마 + ADR #29 트리거 마커, no-code) → A5.1 (`VersionScanStatus` enum + converter + `FileVersion` @Entity + `FileVersionRepository` + Testcontainers 7건) → A5.2 (`FileVersionDto` record + `FileVersionController` GET `/api/files/{fileId}/versions` + `@PreAuthorize` READ + 권한/404 매트릭스 통합 테스트 7건) → A5.3 (CI green wait + squash-merge `5155e00` + ADR #29 closed + dev-docs archive).

### 회고

- **commits**: 7 on top of A4 close `48e23a3` (worktree branch `feature/a5-file-versions`) → squash-merge `5155e00` on `master`. PR #13 single, CI green (backend junit + frontend vitest 모두 SUCCESS).
- **production 파일**: 5 신설 (backend `file/VersionScanStatus.java`, `file/VersionScanStatusConverter.java`, `file/FileVersion.java`, `file/FileVersionRepository.java`, `file/dto/FileVersionDto.java`, `file/FileVersionController.java`). frontend 무수정.
- **test 파일**: 2 신설 (`FileVersionRepositoryTest` 7건, `FileVersionControllerTest` 7건). A4 401건 테스트 회귀 0.
- **endpoint 신규**: 1개 — `GET /api/files/{fileId}/versions` (DESC 정렬, `isCurrent` 계산, soft-delete 404, `@PreAuthorize` READ).
- **ADR 변경**: **#29 closed** (deferred → "closed (A5, squash-merge `5155e00`)"). FileVersion entity/repo + GET versions까지 도입. POST `/versions` (업로드 commit) + restore는 A6+ 이월.
- **schema-validation issue**: 최초 commit에서 `FileVersion.checksumSha256`을 `columnDefinition="char(64)"`로 매핑 → CI에서 Hibernate logical type VARCHAR ↔ Postgres bpchar(CHAR) 불일치로 ApplicationContext 부트 실패 (Testcontainers 125건 cascade FAILED). `@JdbcTypeCode(SqlTypes.CHAR)` 주입 fix 후 CI green.
- **camelCase 정합**: `FileVersionDto`는 기존 `FileDto`/`FolderDto`와 동일하게 camelCase JSON 키. docs/02 §7.6 본문 예시는 snake_case로 적혀있어 closure에서 §7.6 본문 정합 patch (별도 commit).

### 핵심 결정 (A5 트랙, 확정)

1. **FileVersion entity Lombok-free** — A4 entity 패턴(`Folder`/`FileItem`) 보존. JPA AttributeConverter (`autoApply=false`) + `VersionScanStatusConverter`로 DB lowercase ↔ Java UPPERCASE 변환.
2. **`scan_result JSONB` entity 미매핑** — A5 list endpoint 응답 스키마 미포함 + JSONB↔JPA 의존성 회피 (audit 모듈과 동일 — JdbcTemplate 분리). 스캐너 워커 도입 시점에 별도 매핑 검토 (KISS — YAGNI).
3. **FileItem.currentVersionId 매핑 = UUID 컬럼** — `@OneToOne` 대신 단순 UUID. lazy proxy 비용/cycle 위험 회피 + service layer가 명시적 fetch.
4. **GET /versions 만 도입** — POST `/versions` (업로드 commit), restore, comment 수정 등은 A6+ 이월. ADR #29 deferred는 GET까지로 close 처리.
5. **CHAR vs VARCHAR 매핑 표준화** — `CHAR(N)` 컬럼은 entity에서 `@JdbcTypeCode(SqlTypes.CHAR) + @Column(length=N)`로 매핑. `columnDefinition` string은 logical type 결정에 무력 — 회귀 가드.

### accepted-deviation (A6+ 이월)

- POST `/api/files/{fileId}/versions` (업로드 commit + 멀티파트 + scan-status='pending' enqueue) — A4.8 file mutation 트랙과 별개로 진입 시점 미정.
- `FileVersion.scanResult` JSONB 매핑 — 스캐너 워커 도입과 동시 (위 결정 #2).
- 버전 restore (`PATCH /api/files/{id}/restore-version`) — 사용자 주도 롤백 UX와 함께 후속.
- docs/02 §7.6 본문 snake_case 예시 → camelCase 정합 patch — 본 closure commit에서 동시 처리.

### DoD 10/10

1. ✅ `VersionScanStatus` enum + converter — DB lowercase ↔ Java UPPERCASE 라운드트립 GREEN
2. ✅ `FileVersion` @Entity + V5 schema-validation GREEN (`ddl-auto=validate`)
3. ✅ `FileVersionRepository.findByFileIdOrderByVersionNumberDesc` + `existsByStorageKey` — Testcontainers 7건 GREEN
4. ✅ `FileVersionDto` record + `from(v, currentVersionId)` factory — `isCurrent` 계산 정확
5. ✅ `FileVersionController` GET endpoint + `@PreAuthorize("hasPermission(#fileId, 'file', 'READ')")` + soft-delete 404
6. ✅ Controller 통합 테스트 7건 — ADMIN/AUDITOR/MEMBER(no-grant 403)/MEMBER(grant 200)/missing 404/soft-deleted 404/currentVersionId NULL → 모두 GREEN
7. ✅ A4 evaluator 13건 + audit 회귀 0, audit_log REVOKE 정책 무영향 (read-only endpoint, audit emission 없음)
8. ✅ ADR #29 status "deferred" → "closed (A5, `5155e00`)" 정합. docs/00 §5 갱신
9. ✅ PR #13 CI green (backend junit + frontend vitest 모두 SUCCESS) + master squash-merge `5155e00`
10. ✅ dev-docs `dev/active/a5-file-versions/` → `dev/completed/` archive + docs/02 §7.6 camelCase/`NOT_FOUND` envelope 정합 patch

### 다음 단계 — A6 또는 docs/03~04 본문

- **A6 (이미 active)**: folder delete/restore + descendant cascade soft-delete + RESTORE_CONFLICT envelope (별도 worktree `dev/active/a6-folder-mutation-delete/`).
- **A5 잔여 → A6+ 이월**: POST `/versions` (업로드 commit), 버전 restore, scan worker, scanResult JSONB 매핑.
- **docs/03 §5~§8 본문**: 저장소 보안(KMS/presigned TTL), Legal Hold, 데이터 보호, 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지·쿼터·백업 (별도 트랙).

---

## 2026-04-29 — A5.0 docs/02 §7.6 + ADR #29 트리거 마커 (no-code)

### 범위
A5 마일스톤 진입점. ADR #29 deferred 클리어를 위한 docs 정합 patch.

### 변경
- `docs/02 §7.6 (Files)` GET `/api/files/:id/versions` 응답 스키마 본문 보강 (`versions` 배열 + `is_current` 플래그 + 정렬(version_number DESC)·soft-delete 404 정책 명시). _주: dev-docs(plan/tasks)는 §7.7로 표기되어 있으나 실제 표 위치는 §7.6 — A5 후속 phase에서 dev-docs drift 정정 예정._
- `docs/00 §5 ADR #29` 본문에 "A5 진입 (2026-04-29)" 트리거 마커 1줄 추가 (close는 A5.3 commit hash로 갱신 예정).

### 검증
- A5.0 commit 자체는 docs 3파일만 staged (코드 0줄). _작업 트리에 별도 세션 발 backend 변경 잔재 발견 — 본 commit 범위 외, 사용자 확인 대기._
- 다음 phase A5.1 FileVersion entity 작성이 본 §7.6 스키마와 1:1 정합.

---

## 2026-04-29 — 🏁 A4 마일스톤 종료 (Folder/File Domain + Resource-Level Permissions)

### 범위

A4.0 (docs/02 §2.3/§2.6/§7.10 정합 patch + ADR #27/#28/#29) → A4.1 (V5 마이그레이션 — folders/files/file_versions/permissions + UNIQUE COALESCE + DEFERRABLE FK + V5MigrationIT) → A4.2 (file/permission entity·repo + 재귀 CTE `findEffective`, Folder는 ownership 충돌로 A4.5 흡수) → A4.3 (`IbizDrivePermissionEvaluator` 내부 resource-level 교체 + 상속, A3 13/13 회귀 보존) → A4.4 (PermissionController 4 endpoint + `permission.granted/revoked` 실 emission, ADR #26 close) → A4.5 (Folder JPA entity + FolderRepository, A4.2 deferred 흡수) → A4.6 (FolderMutationService + create/rename/move + audit emit + cycle 가드 — delete/restore는 A6로 이월) → A4.7 (FolderController + 3 REST endpoint + RENAME_CONFLICT envelope + ADR #30 root=ADMIN-only).

### 회고

- **commits**: 9 (no-merges) on top of A3 close `6f0820d` → master HEAD `48e23a3`. PR 6건 (#6 A4-data, #7 evaluator, #8 perm-endpoint, #9 folder-entity, #10 mutation-service, #11 folder-endpoint) 모두 squash-merged + CI green.
- **production 파일**: 26 신설/변경 (backend `folder/` 11 + `permission/` 11 + `file/` 2 + `audit/` 1 + `common/error/` 1) + frontend는 enum mirror 무수정 (A3 1:1 mirror 그대로 유효).
- **test 파일**: 13 신설 (A4 신규 — `V5MigrationIT`, `FolderRepositoryTest`, `FolderMutationServiceTest`, `FolderControllerTest`, `PermissionRepositoryTest`, `PermissionResolverTest`, `IbizDrivePermissionEvaluatorTest`, `PermissionControllerTest`, `PermissionServiceGrantRevokeTest`, `PermissionAuditListenerTest` 등). A3 13개 evaluator 통합 테스트 회귀 보존.
- **마이그레이션**: V5 — `folders` (parent UNIQUE = `COALESCE(parent_id, ZERO_UUID)` 보강, soft delete `WHERE deleted_at IS NULL`) + `files` (`folder_id` FK, normalized_name UNIQUE) + `file_versions` (테이블만 도입, entity는 ADR #29로 A5 이월) + `permissions` (preset 단일 컬럼, ADR #28).
- **endpoint 신규**: 7개 — `POST/DELETE/GET /api/{resource}/{id}/permissions`, `GET /api/me/effective-permissions` (A4.4) + `POST /api/folders`, `PATCH /api/folders/{id}`, `POST /api/folders/{id}/move` (A4.7).
- **ADR 변경**: **#26 closed** (A4.4 — `permission.granted/revoked` 실 emission + 4 endpoint 도입), **#27/#28/#29 신규** (A4 PR 분할 / preset 단일 / FileVersion A5 이월), **#30 신규** (A4.7 — root parent 작업은 ROLE ADMIN-only SpEL 삼항 분기).
- **재귀 CTE**: `PermissionRepository.findEffective(userId, resourceType, resourceId)` — 부모→자식 상속 + grant 우선 lookup (ADR #28). evaluator는 SpEL 시그니처 `hasPermission(#id, 'folder', 'READ')` 그대로 보존, 내부만 교체 (ADR #26 보장사항 충족).
- **트랜잭션**: 모든 mutation = `@Transactional` + `SELECT FOR UPDATE` (FolderMutationService). audit emit은 `REQUIRES_NEW`로 분리 (ADR #24 보존). audit append-only `42501` 회귀 0 (V4 REVOKE 무영향).

### 핵심 결정 (A4 트랙 6+1, 확정)

1. **PR 2개 분할** — A4-data + A4-controllers, 의존 단방향. 추가로 A4-controllers 내부도 evaluator/perm-endpoint/folder-entity/mutation-service/folder-endpoint 5 sub-track으로 분기 (ADR #27)
2. **permissions = preset 단일 컬럼**, deny semantics v1.x 이월 (ADR #28). evaluator는 grant 우선 lookup만 (allow 발견 → true, 어디서도 grant 없으면 false)
3. **FileVersion entity/repo/CRUD A5 이월** — V5 schema는 테이블 + DEFERRABLE FK만 (ADR #29). `current_version_id` NULL 허용 유지
4. **root parent 작업 = ROLE ADMIN-only** — SpEL 삼항 `#req.parentId == null ? hasRole('ADMIN') : hasPermission(...)` (ADR #30). 노드 admin preset이 root 트리 구조 변경으로 번지지 않게 차단
5. **SpEL 호출 시그니처 0변경** — A3 `hasPermission(#id, 'folder', 'READ')` 그대로, evaluator 내부만 user-level → resource-level. controller `@PreAuthorize` 회귀 0 (ADR #26 보장사항 충족)
6. **Folder ownership 충돌 → A4.5 흡수** — A4.2에서 file/permission만 진행, master worktree `dev/process/20260428-a3-folder-mutation-service.md` ownership 해제 후 a4-folder-entity 세션에서 정리 + Folder JPA entity 신설

### accepted-deviation (A5 이월)

- `file_versions` entity/repository/CRUD endpoint — 버전 이력 UI/API는 별도 기능 (ADR #29). V5 schema는 테이블만 도입했으므로 A5는 entity/repo/endpoint만 추가
- 명시 deny semantics — `permissions.deny BOOLEAN` 또는 별도 `denies` 테이블 (ADR #28). 현 schema 호환 (컬럼 추가만으로 확장)
- LTREE 부서 계층 + `includeDescendants` — 부서 모델 자체가 A1.5 후속 (track 미정)
- File mutation/CRUD endpoint — A4는 Folder MVP까지만, File mutation은 A5 또는 별도 트랙 (5 endpoint 미구현)

### DoD 11/11

1. ✅ V5 마이그레이션 GREEN — 4테이블 + UNIQUE COALESCE + DEFERRABLE FK + REVOKE 무충돌 (V5MigrationIT 7+ 케이스)
2. ✅ `Folder`/`FileItem`/`PermissionRow` entity + repository + 재귀 CTE `findEffective`
3. ✅ A3 `PermissionEvaluatorIntegrationTest` 13/13 GREEN 회귀 보존 + resource-level 신규 테스트 GREEN
4. ✅ `PermissionService.grantPermission/revokePermission` + `PermissionGrantedEvent/RevokedEvent` + `PermissionAuditListener` 확장 — `permission.granted/revoked` 실 emission (REQUIRES_NEW)
5. ✅ `PermissionController` 4 endpoint + 가드 + 403 envelope `PERMISSION_DENIED`
6. ✅ `FolderMutationService` create/rename/move — `@Transactional` + `SELECT FOR UPDATE` + cycle 가드 (delete/restore는 A6로 이월)
7. ✅ `FolderController` 3 endpoint (create/rename/move) + `RENAME_CONFLICT` 409 envelope
8. ✅ ADR #26 status "deferred" → "closed (A4.4)" 표기 정합. ADR #27/#28/#29/#30 docs/00 §5 등록
9. ✅ A2 audit append-only 회귀 0 — V4 REVOKE 정책 무영향, `42501` 가드 보존
10. ✅ PR #6/#7/#8/#9/#10/#11 모두 CI green (backend junit + frontend vitest 둘 다 SUCCESS) + master squash-merge
11. ✅ dev-docs `dev/active/a4-folder-file-domain/` + sub-track 5건 → `dev/completed/` archive (parent + 5 sub-tracks 통합 closure)

### 다음 단계 — A5 또는 docs/03~04 본문

- **A5 후보**: `FileVersion` entity/repo + 버전 생성/조회/복원 endpoint (ADR #29 deferred 클리어). File mutation endpoint(rename/move/delete/restore) 흡수 검토.
- **docs/03 §5~§8 본문**: 저장소 보안(KMS/presigned TTL), Legal Hold, 데이터 보호, 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지·쿼터·백업 (별도 트랙).
- **stale PR 정리**: #3 (codex/a3-mutation-domain), #4 (codex/a3-folder-mutation-service) — A3 머지가 다른 경로로 끝났으므로 close 예정.

---

## 2026-04-29 — A4.0 docs/02 정합 patch (no-code)

### 범위
A4-data 트랙 진입점. ADR #27/#28/#29 결정을 docs/02 본문에 반영.

### 변경
- `docs/02 §2.3 folders` UNIQUE 인덱스: `parent_id` → `COALESCE(parent_id, ZERO_UUID)` 보강 + 보강 사유 주석 1줄 (root parent 다중 NULL 차단, ADR #27 보장사항).
- `docs/02 §2.6 permissions` `preset` 컬럼 코멘트에 "preset 단일 — deny v1.x 이월, ADR #28" 주석 1줄.
- `docs/02 §7.10` POST `/api/:resource/:id/permissions` Guard `'ADMIN'` → `'PERMISSION_ADMIN'` 정합 (구표기 alias 명시).

### 검증
- `git diff --stat backend/ frontend/` → 비어있음 (코드 0줄).
- 다음 phase A4.1 V5 SQL이 본 §2.3/§2.6 본문과 1:1 정합.

---

## 2026-04-29 — 🏁 A3 마일스톤 종료 (Permission Matrix + PermissionService)

### 범위
A3.0 (docs/03 §3 정합화 + ADR #26) → A3.1 (`Permission`/`Preset` enum + frontend 1:1 mirror) → A3.2 (`PermissionService` + `IbizDrivePermissionEvaluator` + 403 envelope) → A3.3 (`effectivePermissionsCacheKey` 정적값 → SHA-256 hex prefix 16자) → A3.4 (`permission.changed` audit emission via `RoleChangedEvent` + listener) → A3.5 (full `@SpringBootTest` + Testcontainers E2E — 매트릭스 + role change).

### 회고
- **commits**: 8개 (`ff5156c` dev-docs/ADR #26 → `aec7b74` docs/03 §3 표기 정합 + `permission.ts` placeholder → `4458feb` A3.1 enum + frontend mirror → `e1083e4` A3.2~A3.4 backbone (cache key hash + permission.changed) → `ccd766d` A3.5 E2E + closure 2건). diff vs origin/master 예상: backend +production 7파일 / +test 9파일, frontend +1파일.
- **테스트**: backend +6 단위 클래스 (`PermissionEnumTest`, `PresetMappingTest`, `PermissionServiceTest`, `PermissionServiceChangeRoleTest`, `PermissionCacheKeyServiceTest`, `PermissionAuditListenerTest`) + 1 슬라이스 (`PermissionEvaluatorIntegrationTest` 13 케이스) + 2 E2E (`PermissionEndpointE2ETest` 11 + `RoleChangeE2ETest` 2). 게이트 1 235 → 게이트 2 248 → A3.5 261 (총 +26). frontend 305 → 316 (`permission.test.ts` 11 케이스).
- **production 클래스**: 7 (permission/ 6 + common/error/ 1). `Permission`(9 values) / `Preset`(5 values + `permissions()`) / `PermissionService`(check + changeRole) / `IbizDrivePermissionEvaluator` / `PermissionCacheKeyService` / `PermissionDenyContext` / `RoleChangedEvent` / `MethodSecurityConfig` / `GlobalExceptionHandler.handleAccessDenied` (`PERMISSION_DENIED` envelope).
- **마이그레이션**: 0 — A3는 user-level (Role 기반) MVP, `permissions` 테이블 미도입 (resource-level은 A4 의존).
- **endpoint 신규**: 0 production (권한 grant/revoke endpoint는 file/folder 도메인 의존 → A4 이월). 기존 `/api/auth/login` + `/api/auth/me` 응답의 `effectivePermissionsCacheKey`만 hash로 wire 변경 (shape 동일, 값만 hex).
- **ADR 등록**: **#26** (`PermissionEvaluator` MVP 범위 — user-level만, resource-level 평가는 A4에서 evaluator 내부만 교체, SpEL 호출 시그니처 `hasPermission(#id, 'folder', 'READ')`는 docs/02 §7.10 그대로 채택, `permission.granted/revoked` emit도 A4 이월, `effectivePermissionsCacheKey`는 SHA-256 hex prefix 16자 — A1 deviation #2 해소).
- **frontend 통합**: `frontend/src/types/permission.ts` 신설 (1:1 mirror). 사용처는 UX 게이트 한정 (보안 boundary 아님 — CLAUDE.md §3 원칙 10). `effectivePermissionsCacheKey` 응답 shape 동일 — opaque token으로만 사용.

### 핵심 결정 (트랙 5+1, 확정)
1. user-level (Role 기반) MVP — `permissions` 테이블 + 재귀 CTE 상속 평가는 A4 이월. SpEL 호출 시그니처는 docs/02 §7.10 그대로 동결 (ADR #26)
2. PURGE 가드 = `hasRole('ADMIN')` — `Preset.admin`이 PURGE를 의도적으로 제외해 `hasPermission` 경로로는 어떤 role도 통과 불가 (docs/03 §3.2 line 333). 회귀 가드는 `PermissionEndpointE2ETest`의 `/purge` 매트릭스로 락
3. cacheKey = SHA-256 hex prefix 16자 — 입력 `<userId>:<ROLE>:v1` (`MATRIX_VERSION` bump → 일괄 invalidate hook). frontend는 opaque token으로만 사용 — 역파싱 금지 보장으로 매트릭스 변경 안전 (ADR #26)
4. emission = `RoleChangedEvent` + `PermissionAuditListener` (ADR #24 동형 — `AuthAuditListener` 패턴 1:1 채택). `AuditService.record`의 REQUIRES_NEW로 audit 무결성 (ADR #25 보존)
5. 403 envelope = `{ error: { code: 'PERMISSION_DENIED', message, details: { required, have } } }` — `PermissionDenyContext` (ThreadLocal) 1회 consume으로 evaluator → exception handler 정보 전달 (docs/03 §3.6)
6. enum 단일 진실 = 백엔드, frontend는 mirror — A2 패턴 동일

### accepted-deviation (A4 이월)
- `permission.granted` / `permission.revoked` emission — resource-level grant endpoint (POST/DELETE `/api/:resource/:id/permissions`)는 file/folder 도메인 부재로 A4. 본 phase는 `permission.changed`(role 변경)만 실 emit. enum 자체는 A2에서 등록 완료 (ADR #26)
- LTREE 부서 계층 + `includeDescendants` 평가 — 부서 모델 자체가 A1.5 후속 (A4)
- 권한 상속 재귀 CTE (docs/03 §3.4) — folder tree 부재 (A4)
- `effectivePermissions` resource-level 캐시 store — A3는 user-level hash key만, 실제 캐시 store는 v1.x

### DoD 11/11
1. ✅ docs/03 §3.1~§3.6 표기 정합 + 본문이 docs/02 §7 Guard 컬럼과 1:1 일치 (`aec7b74`)
2. ✅ `Permission` enum 9 values (`PermissionEnumTest`)
3. ✅ `Preset` enum 5 values + preset→permission set §3.2 표 동치 (`PresetMappingTest`)
4. ✅ `frontend/src/types/permission.ts` 1:1 mirror (`permission.test.ts` 11 케이스)
5. ✅ `PermissionService.check` 단일 진입점 + `IbizDrivePermissionEvaluator` SpEL hook (`PermissionEvaluatorIntegrationTest` 13 + `PermissionEndpointE2ETest` 11)
6. ✅ user-level MVP 평가 정책 (ADMIN=all, AUDITOR=READ, MEMBER=∅, PURGE는 hasRole(ADMIN) 가드)
7. ✅ `effectivePermissionsCacheKey` SHA-256 hex prefix 16자 — deterministic + collision-free (`PermissionCacheKeyServiceTest` 7 케이스)
8. ✅ `permission.changed` emission policy + 실 emit (`PermissionAuditListenerTest` 2 + `RoleChangeE2ETest` 2). granted/revoked는 ADR #26로 A4 이월 명시
9. ✅ `@SpringBootTest` E2E 매트릭스 (ADMIN/AUDITOR/MEMBER × READ/EDIT/PURGE) + role change scenario
10. ✅ `gradle test` + `pnpm test` 로컬 GREEN + PR #5 CI 그린 최종 확정 (run `25075778972`: backend junit + frontend vitest 둘 다 SUCCESS, commit `8ecff7d`)
11. ✅ ADR #26 docs/00 §5에 등록 (`ff5156c`)

### 다음 단계 — A4 진입점
- 폴더/파일 도메인 (`folders`, `files`, `permissions` 테이블 + UNIQUE 제약 + LTREE)
- POST/DELETE `/api/:resource/:id/permissions` endpoint → `permission.granted/revoked` 실 emit 호출처
- `IbizDrivePermissionEvaluator` 내부 교체 — resource-level grant + 재귀 CTE 상속 평가 (SpEL 호출 시그니처는 보존)
- `effectivePermissions` resource-level 캐시 store (v1.x 후보)

---

## 2026-04-29 — A3.2~A3.4 (게이트 2)

### 완료
- **A3.2** PermissionService + IbizDrivePermissionEvaluator + 403 envelope
  - `PermissionService.check(userId, role, resource, resourceId, permission)` user-level MVP (ADMIN=ALL, AUDITOR=READ, MEMBER=∅)
  - `IbizDrivePermissionEvaluator implements PermissionEvaluator` — Spring Security `@PreAuthorize("hasPermission(#id,'folder','READ')")` SpEL hook
  - `MethodSecurityConfig` (`@EnableMethodSecurity` + `DefaultMethodSecurityExpressionHandler` 빈에 evaluator 주입)
  - `PermissionDenyContext` (ThreadLocal) — evaluator deny 판정 시 required/have set을 1회 consume 형식으로 ExceptionHandler에 전달
  - `ApiError` (docs/02 §7.2 envelope) + `GlobalExceptionHandler.handleAccessDenied` → 403 `PERMISSION_DENIED` + `details.required`/`details.have`
  - `TestPermissionController` (`src/test/java`) + `PermissionEvaluatorIntegrationTest` 10 케이스 (ADMIN/AUDITOR/MEMBER × READ/EDIT/PURGE + 익명 401)
- **A3.3** effectivePermissionsCacheKey hash 교체
  - `PermissionCacheKeyService.computeKey(userId, role)` — SHA-256 hex prefix 16자 (lowercase), 입력 `<userId>:<ROLE>:v1` (`MATRIX_VERSION` bump → 일괄 invalidate hook). 7 unit tests
  - `LoginResponse.from(User, String cacheKey)`로 시그니처 변경, `AuthService.login` / `AuthController.me`에 service 주입 + 사전 산출 키 사용 (session attribute도 동일 키)
  - `AuthMeLogoutIntegrationTest` plaintext assertion → `[0-9a-f]{16}` regex로 교체
- **A3.4** permission.changed audit emission
  - `RoleChangedEvent(actorId, targetUserId, from, to)` record (publish는 트랜잭션 커밋 직전)
  - `PermissionAuditListener` — `@EventListener` for RoleChangedEvent → `AuditEventType.PERMISSION_CHANGED` row + `before`/`after` JSON `{"role":"..."}` (REQUIRES_NEW 보존, swallow + ERROR 로그)
  - `PermissionService.changeRole(targetUserId, newRole, actorId)` (`@Transactional`) — user.role 갱신 + repository.save + event publish, 같은 role no-op
  - `User.changeRoleTo(Role)` 도메인 mutator
  - 4 unit (`PermissionServiceChangeRoleTest`) + 2 unit (`PermissionAuditListenerTest`) + `PermissionServiceTest` constructor 적응

### 검증
- backend `./gradlew test`: **248 tests, 0 failures, 100% successful** (게이트 1 235 → +13)
- frontend `pnpm test`: **316 tests, 0 failures**

### accepted-deviation
- `permission.granted` / `permission.revoked` emission은 A4 (resource-level grant endpoint 도입 시점). 본 phase는 `permission.changed`만 실 emit (ADR #26)

### 다음 단계 (게이트 2 OK 대기 → A3.5)
- A3.5 E2E: ADMIN→MEMBER 변경 후 다음 요청 403 + audit `permission.changed` 1건 (full SpringBootTest + Testcontainers)
- 권한 매트릭스 전체 E2E: ADMIN/AUDITOR/MEMBER × hasPermission READ/EDIT/PURGE

---

## 2026-04-29 — A3.0 docs 정합 + ADR #26 (no-code phase)

### 완료
- **docs/03-security-compliance.md** 헤더 `현재 상태: 스켈레톤` → `§1·§2·§3·§4 본문 활성, §5·§6·§7·§8 일부 본문 진행 중` (line 4) — §3 본문은 이미 §3.1~§3.6 작성 완료 상태였으나 헤더가 stale했던 표기 정합
- **CLAUDE.md** §2 라우팅 표 "권한 매트릭스 (작성 예정)" → "권한 매트릭스 (§3)" / §4 계약 파일 표 `src/types/permission.ts` "(예정)" 제거
- **docs/00-overview.md** §5에 **ADR #26** 추가 — `PermissionEvaluator` MVP는 user-level (Role 기반) 평가만, resource-level은 A4 이월. SpEL 호출 시그니처(`hasPermission(#id, 'folder', 'READ')`)는 docs/02 §7.10 그대로 채택해 A4에서 evaluator 내부만 교체. `permission.granted/revoked` emit도 A4 이월 (A3는 `permission.changed`만), `effectivePermissionsCacheKey` SHA-256 hex prefix 16자 (A1 deviation #2 해소 예고)
- **frontend/src/types/permission.ts** placeholder 신설 (`export {}` + JSDoc backlink)

### 검증
- `pnpm typecheck` PASS, `pnpm lint` PASS

### 다음 단계 (A3.1 진입)
- backend `com.ibizdrive.permission.Permission` enum 9 values + `Preset` enum 5 values + frontend 1:1 mirror (RED→GREEN)

---

## 2026-04-28 — 🏁 A2 마일스톤 종료 (Audit Log Backbone)

### 범위
A2.0 (V3 audit_log 스키마 + V4 REVOKE) → A2.1a (AuditService + Enums + REQUIRES_NEW) → A2.1b (`@Audited` AOP + WebRequestContextHolder) → A2.2 (append-only 강제 검증, A2.0과 통합) → A2.3 (`GET /api/admin/audit` + role-based scope) → A2.4 (A1 인증 이벤트 emission via `ApplicationEventPublisher` + listener) → A2.5 (E2E 통합 — auth → audit_log → query) → A2.6 (frontend mock → real fetch).

### 회고
- **commits**: 20개 (a6076f0 dev-docs/ADR → 440b0b0 V3+V4 → fd28368/1196e11/cf0be93 A2.1a + fix → a0f9f7e A2.1b → 2fdad2d A2.3 → 7aaea19 A2.4 → 14cec52/3fd8c57/f1ab7a6/b8cc4f2 A2.5 + CI 부트스트랩 → 36896a8 A2.6 → 진행 doc 5건). diff vs origin/master: +3,372 / -131, 36 파일.
- **테스트**: backend +9 클래스 (`AuditLogSchemaTest`, `AuditLogAppendOnlyTest`, `AuditServiceTest`, `AuditedAspectTest`, `AuthAuditListenerTest`, `AuditQueryControllerTest`, `AuditQueryServiceTest`, `AuthAuditE2ETest`, `AuditQueryE2ETest`) — 단위/슬라이스/E2E 모두 그린. frontend `api.audit.test.ts` 7 케이스로 재작성 후 305/305 PASS.
- **production 클래스**: 13 (audit/ 11 + audit/dto/ 2). `AuditEvent`/`AuditEventType`(38) /`AuditTargetType`(7)/`AuditService`/`AuditQueryController`/`AuditQueryFilters`/`AuditQueryService`/`Audited`/`AuditedAspect`/`AuthAuditListener`/`WebRequestContextHolder` + `AuditLogEntryDto`/`AuditLogPageDto`.
- **마이그레이션 2종**: `V3__audit_log.sql` (스키마 + `target_type` CHECK 7값 + 인덱스 4개), `V4__audit_log_revoke.sql` (`app_user` role 생성 idempotent + REVOKE UPDATE/DELETE + GRANT INSERT/SELECT).
- **endpoint 1종**: `GET /api/admin/audit?fromDate&toDate&actorQuery&eventType&page&pageSize` — ADMIN/AUDITOR 전체, MEMBER scope=self, 익명 401 (service 단일 분기, controller `isAuthenticated()`).
- **ADR 등록**: **#24** (AOP `@Audited` + Spring Security `ApplicationEvent` 하이브리드 — `publishEvent(...)` 호출은 cross-cutting 신호로 침투 허용, 비즈니스 로직 0줄), **#25** (DB role 분리 + REVOKE UPDATE/DELETE → `42501`로 append-only 증명).
- **frontend 통합**: `api.getAuditLogs` mock 분기 + 60-row generator 완전 제거 → `fetch('/api/admin/audit?...', { credentials: 'include' })`. `next.config.ts` rewrite로 dev에서 same-origin 쿠키 흐름. wire shape는 M12 mock 표면과 1:1 동치.
- **DoD 10/10 충족**: (1) audit_log + 4 인덱스 ✅ (2) `42501` 증명 (`AuditLogAppendOnlyTest`) ✅ (3) Java enum 38값 = ts mirror 1:1 ✅ (4) `AuditService.record()` 단일 진입점 + AOP + listener 하이브리드 ✅ (5) AuthService 비즈니스 로직 0줄 변경 (publish 4지점만 추가, ADR #24 갱신) ✅ (6) role 기반 scope 분기 ✅ (7) `api.audit.test.ts` 7 케이스 PASS ✅ (8) ADR #24/#25 docs/00 §5 등록 ✅ (9) `gradle test` + `pnpm test` CI 그린 (run 25023235347) ✅ (10) backup 브랜치 `backup/pre-reset-20260427-0036` 보존 ✅.

### 핵심 결정 (트랙 5+1, 확정)
1. emission 위치 = AOP `@Audited`(`@AfterReturning`) + Spring Security `ApplicationEventPublisher` 하이브리드 — annotation grep 가능, 트랜잭션 롤백 자동 처리, AuthService 침투는 publish 호출만 (ADR #24)
2. 보존 3년 + Legal Hold 무기한 — docs/03 §4.3 명시값 채택, 월별 파티셔닝은 v1.x로 deferred (단일 테이블 MVP)
3. append-only 강제 = DB role 분리 (`app_user` INSERT/SELECT only) + REVOKE — `42501` SQLState로 RED 증명 (ADR #25)
4. read 권한 = `Role` enum 재사용. ADMIN/AUDITOR 전체, MEMBER `actor_id=self` — A3 권한 시스템 비의존
5. enum 단일 진실 = 백엔드, ts는 mirror — CI lint는 후속(MVP는 수동 동기 + frontend `audit.test.ts` 계약으로 회귀 가드)
6. frontend는 `api.getAuditLogs`만 fetch 교체 — UI/테스트 변경 0, M12 표면 보존

### 잔여 accepted-deviation 5건 (후속 phase 추적)
| # | 항목 | 추적 phase |
|---|---|---|
| 1 | 월별 파티셔닝 자동화 (현재 단일 테이블, 파티션 SQL 함수만 docs/02 §9.4 주석 보존) | v1.x |
| 2 | 콜드 스토리지 아카이빙 cron | v1.x |
| 3 | `audit.exported` runtime emission (CSV export endpoint 자체가 v1.x — enum만 정의) | v1.x |
| 4 | `file.viewed` emission (`audit_level=strict` 폴더 도입 필요 → 폴더 테이블 부재) | A4 |
| 5 | `user.password.changed` (PW 변경 endpoint 자체가 v1.x) | v1.x |
| 6 | dev seed 60건 + `frontend/e2e/audit.e2e.ts` (admin 로그인 UI 의존) | A1 frontend 인증 + admin 라우팅 선행 |

### 핵심 함정 회고
- **REQUIRES_NEW + `@DataJpaTest` visibility**: 외부 트랜잭션 commit 전 INSERT는 별도 connection에서 미가시 → FK 23503 위반. 해결: seed를 `TransactionTemplate(REQUIRES_NEW)`로 즉시 commit (A2.1a `1196e11`)
- **PostgreSQL inet 호스트 표기**: `203.0.113.42/32` 입력 → `/32` mask 자동 생략. 가정 금지, 실제 SELECT로 검증 (`cf0be93`)
- **CI testLogging 기본 격차**: 로컬 lenient / CI strict (Hibernate Validator email RFC 5321 64자 local-part). `testLogging.exceptionFormat = FULL` 영구 적용으로 향후 회귀 디버깅 도움 (`f1ab7a6` → `b8cc4f2`)
- **AuthService 표준 이벤트 부재**: custom flow(`AuthenticationManager` 미사용)라 자동 publish 없음. Option D = `publishEvent(...)` 호출 명시 추가 + ADR #24 갱신본 (cross-cutting 신호, 비즈니스 로직 0줄)

### 다음 마일스톤 안내
- **A3 — 권한 매트릭스 + `PermissionService`**:
  - `@PreAuthorize` + 권한 시스템 백엔드 권위 (HANDOFF 미정의)
  - `effectivePermissionsCacheKey` 단순 문자열 → 권한 변경 trigger 기반 hash로 교체
  - `permission.granted/revoked/changed` audit 이벤트 emit 시점 (현재 enum만)
- **A4 — 폴더/파일 도메인 + `audit_level=strict` (file.viewed)**
- A3 진입 전 본 PR(#tbd) 머지 + master 동기화

---

## 2026-04-26 — 🏁 A1 마일스톤 종료 (Backend Authentication)

### 범위
A1.0 (User schema + JPA) → A1.1 (PasswordEncoder + DbUserDetailsService) → A1.2 (SecurityConfig 본 wiring + CSRF) → A1.3 (LoginController + in-memory lockout) → A1.4 (`/me` + `/logout` + SecurityContext gap fix) → A1.5 (통합 시나리오 + dev-docs 기반 audit) → **A1.6 (session timeout 정책 — must-fix #1 close)**.

### 회고
- **commits**: 7개 (308c041 / 0dd2d65 / 10a524b / 06b9238 / ca4e309 / c34e640 / A1.6 신규)
- **테스트**: **156 tests** (152 pass + 4 Docker SKIP, 0 fail) — 6 클래스 (`SecurityIntegrationTest` 5 / `LoginAttemptTrackerTest` 4 / `LoginControllerIntegrationTest` 8 / `AuthMeLogoutIntegrationTest` 5 / `AuthScenarioIntegrationTest` 1 / `SessionValidityFilterTest` 4) + slice + repository (152 합산)
- **production 클래스**: 16 (auth/ 7 + auth/dto/ 2 + config/ 1 + user/ 4 + common/error/ 2)
- **endpoint 4종**: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/csrf` (docs/02 §7.4 매트릭스 충족)
- **ADR 등록**: #19 (BCrypt strength=12 + DelegatingPasswordEncoder), #20 (idle 30m sliding + absolute 8h + 5/15min lockout), #22 (`/me` shape — identity + role + permissionsCacheKey), #23 (in-memory lockout backing — MVP 단일 인스턴스). #20은 A1.6에서 full pass.
- **hidden gap fix 1건**: Spring Security 6 `SecurityContextHolderFilter` load-only 동작으로 `AuthService.login`의 명시 `saveContext` 누락 (A1.4 ca4e309에서 수정)
- **must-fix → resolved 1건**: must-fix #1 (세션 timeout 정책) — A1.6에서 `SessionValidityFilter` + `application.yml PT30M`로 close

### 잔여 accepted-deviation 3건 (후속 phase 추적)
| # | 항목 | 추적 phase |
|---|---|---|
| 1 | `audit_log` emission 미구현 — A1 plan에서 명시 deferred (`AuthService.login` 주석 `// (후속) audit insert`) | **A2** (audit + 권한 매트릭스 backbone) |
| 2 | `400 PASSWORD_CHANGE_REQUIRED` 분기 미구현 (현재 `mustChangePassword=true` flag만 응답에 포함) — ADR #21 | PW change endpoint phase (미배정) |
| 3 | `AuthScenarioIntegrationTest` 로컬 SKIP — Windows Docker 미가용. CI ubuntu-latest에서 실행 | PR push 후 `gh pr checks 1` 그린 = close 게이트 |

### 다음 마일스톤 안내 (A2)
- **A2 — Audit log backbone + 권한 매트릭스**:
  - `audit_log` 테이블 + append-only constraint (REVOKE UPDATE/DELETE — docs/03 §4 + CLAUDE.md §3 원칙 8)
  - `AuthService.login`의 `// (후속) audit insert`를 실제 emission으로 채움
  - `@PreAuthorize` + `PermissionService` (HANDOFF의 권한 매트릭스 백엔드 권위)
  - `effectivePermissionsCacheKey` 단순 문자열(`userId:role:v0`) → 권한 변경 trigger 기반 hash로 교체
- A2 진입 전 본 PR(#1) 머지 + master 동기화

---

## 2026-04-26 — A1.6 Session Timeout Policy (must-fix #1 close, TDD)

### 완료
- [A1.6] **`SessionValidityFilter`** — `OncePerRequestFilter`, `Clock` 주입(`Clock systemUTC` `@Bean` 신규). `req.getSession(false)` → 세션 부재면 pass-through. `issuedAt` attribute 부재(또는 Long 아님)면 pass-through (인증 전 요청). `(clock.millis() - issuedAt) >= 8h(ABSOLUTE_TTL_MS)` → `session.invalidate()` + `res.sendError(401)` + chain 차단.
- [A1.6] **`SecurityConfig` 변경** — `Clock` `@Bean`(systemUTC) + `addFilterAfter(sessionValidityFilter, SecurityContextHolderFilter.class)`. SecurityContext 로드 직후 absolute 만료 검사 → 만료 세션의 인증 컨텍스트가 다운스트림 인가 필터에 노출되는 것 회피.
- [A1.6] **`application.yml`** — `spring.session.timeout: PT8H` → `PT30M` (idle 진실 출처). 주석으로 분담 명시 (idle=Spring Session JDBC, absolute=SessionValidityFilter).
- [A1.6] **`SessionValidityFilterTest` 4건** — 단위 (Mockito + mutable `Clock`):
  1. 세션 없음 → pass-through, 응답 미터치
  2. 세션 있고 `issuedAt` 없음 → pass-through, invalidate 호출 안 됨
  3. 7h59m59s 경과 → pass-through, invalidate 호출 안 됨
  4. 정확히 8h 경과 → invalidate(1) + sendError(401) + chain 미호출
- [A1.6] **회귀** — 152 → **156 tests**, 0 fail, 0 err, 4 Docker SKIP 동일

### 핵심 결정 (filter SoT 정책)
- **idle 만료 진실 출처 = `application.yml` `spring.session.timeout: PT30M`** — Spring Session JDBC가 매 요청마다 `lastAccessedTime`을 갱신, 30분 무활동 시 자동 invalidate. 컨테이너 레벨에서 sliding 처리 → 별도 코드 불필요 (KISS).
- **absolute 만료 진실 출처 = `SessionValidityFilter`** — `AuthService.login`이 이미 set 중인 `issuedAt`(epoch millis) attribute + 8h 경계. yml만으로는 ADR #20 absolute 한도 강제 불가 → 본 필터가 must-fix #1의 정확 사유 close.
- **Clock 주입** — production은 `Clock.systemUTC()` `@Bean`, 테스트는 mutable `Clock`을 단위 테스트에서 직접 주입(필터 단독 생성). `@SpringBootTest` 통합 추가 안 함 — 8h 경계를 SpringBootTest로 검증하면 비용만 큼 (단위 4건으로 logic full coverage).
- **`OncePerRequestFilter` + `addFilterAfter(SecurityContextHolderFilter)` 위치** — SecurityContext 로드 직후, 인가 필터 이전. 만료 세션의 `Authentication`이 컨트롤러까지 도달하지 않음.

### 변경 파일
- `backend/src/main/java/com/ibizdrive/auth/SessionValidityFilter.java` (신규)
- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` (Clock @Bean + filter wire + SecurityContextHolderFilter import)
- `backend/src/main/resources/application.yml` (timeout PT8H → PT30M + 주석)
- `backend/src/test/java/com/ibizdrive/auth/SessionValidityFilterTest.java` (신규)
- `dev/active/a1-auth-impl/a1-auth-impl-plan.md` (A1.6 phase 추가)
- `dev/active/a1-auth-impl/a1-auth-impl-audit.md` (must-fix #1 RESOLVED 갱신, 통계 +4 tests)

### 블로커
- 없음

### 다음 세션 컨텍스트
- A1 마일스톤 종료 commit (`chore(A1): close milestone`) + PR body draft 사용자 확인 → push → CI 그린 확인 → A2 진입

---

## 2026-04-26 — A1.5 통합 시나리오 + 마일스톤 audit

### 완료
- [A1.5] **`AuthScenarioIntegrationTest`** — `@SpringBootTest` + Testcontainers Postgres 15-alpine. 9-step 종합 시나리오: CSRF 발급 → 로그인(200) → `/me`(200) → 4회 wrong PW(401×4) → 5회째(401)+카운터 5 → 6회째(423) → mutable Clock 16분 진행 → 재시도 성공(200, lockout TTL 만료 lazy 해제) → logout(204) → 로그아웃 후 `/me`(401). `disabledWithoutDocker=true` (로컬 Windows SKIP / CI ubuntu 실행). `LoginAttemptTracker`를 `@TestConfiguration` `@Primary` 빈으로 mutable Clock 주입 — 운영 빈 영향 0.
- [A1.5] **회귀** — `./gradlew test` 152 tests (148 pass + 4 Docker SKIP, 0 fail). commit `c34e640`
- [A1.5] **dev-docs 기반 수동 audit** — `gsd-audit-milestone` 스킬은 GSD `.planning/ROADMAP.md` + `phases/*/VERIFICATION.md` 구조 전제. 본 프로젝트는 dev-docs(Superpowers) 구조 → 스킬 강행 시 모든 step fail. 사용자 승인으로 plan 목표 상태(line 30~38)를 DoD source로 채택, audit 산출물 `dev/active/a1-auth-impl/a1-auth-impl-audit.md` 작성.
- [A1.5] **audit 결과** — DoD 5/7 pass + must-fix #1 (세션 timeout 정책 미구현) + accepted-deviation 3건. ADR #19/22/23 ✅ / #20 partial. 사용자 결정 (B): A1.6에서 즉시 fix → A1.6에서 #20 full pass + must-fix #1 RESOLVED.

### 핵심 결정
- **dev-docs 기반 수동 audit** — `.planning/` GSD 구조 부재로 `gsd-audit-milestone` / `gsd-sdk` 호출 불가. CLAUDE.md ULTIMATE INVARIANT #10 ("중단 및 보고")에 따라 강행 대신 dev-docs 구조에 맞춘 수동 audit 채택. audit 산출물 위치 = `dev/active/a1-auth-impl/a1-auth-impl-audit.md` (plan/context/tasks와 동일 디렉토리, 마일스톤 archive 시 함께 이동).
- **anti-pattern 기록** — HANDOFF의 `next_action`을 검증 없이 그대로 실행하는 패턴 (이전 HANDOFF가 작성한 `gsd-audit-milestone A1`이 본 프로젝트에서 fail). resume 후 next_action 첫 단계는 도구/구조 가용성 검증부터 (CLAUDE.md #10).

### 블로커
- 없음 (must-fix #1은 사용자 결정으로 A1.6 즉시 fix 채택 → A1.6에서 close)

### 다음 세션 컨텍스트 (A1.6)
- 사용자 결정 (B) — must-fix #1 즉시 fix. SessionValidityFilter RED+GREEN+회귀 + audit.md must-fix #1 RESOLVED 갱신.

---

## 2026-04-26 — A1.4 `/api/auth/me` + `/api/auth/logout` (+ A1.3 SecurityContext gap fix)

### 완료
- [A1.4] **`GET /api/auth/me`** — `AuthController.me(@AuthenticationPrincipal IbizDriveUserDetails)`. 응답 shape는 `LoginResponse` 재사용 (docs/02 §7.4 line 847-868: `{user, departments, roles, effectivePermissionsCacheKey}` — login과 동일). 미인증 → `anyRequest().authenticated()` + `HttpStatusEntryPoint(401)` 자동 차단
- [A1.4] **`POST /api/auth/logout`** — `session.invalidate()` + `SecurityContextHolder.clearContext()` + `Set-Cookie SESSION=; Max-Age=0; Path=/; HttpOnly`. 응답 204 (docs/02 §7.4 line 836-845). CSRF 미제공 → CsrfFilter 403, 미인증 → 401
- [A1.4] **A1.3 hidden gap 수정** — Spring Security 6의 `SecurityContextHolderFilter`는 load-only (5.x의 `SecurityContextPersistenceFilter` auto-save 제거). 인증 메커니즘이 `SecurityContextRepository.saveContext`를 명시 호출해야 세션에 컨텍스트가 영속화됨. `AuthService.login`은 session attribute만 set하여 후속 `/me`가 항상 401이 되는 hidden bug. `DelegatingSecurityContextRepository` 빈(HttpSession + RequestAttribute 동시 저장) + `HttpSecurity.securityContext()` wire + `AuthService`에 `SecurityContextRepository` 주입하여 `changeSessionId()` 직후 `saveContext` 호출
- [A1.4] **`AuthMeLogoutIntegrationTest` 5건** — `@WebMvcTest` slice. `/me` 인증/미인증, `/logout` 인증+CSRF / 인증+CSRF 미제공 / 미인증+CSRF (4 클래스, 22 테스트 PASS, 1m33s)
- [A1.4] commit `feat(A1.4): /api/auth/me + /api/auth/logout` (ca4e309)

### 핵심 결정
- **`/me`는 `LoginResponse` 재사용 (`MeResponse` 미생성)** — docs/02 §7.4 line 847-868의 `/me` 응답 shape는 `LoginResponse`(`{user, departments, roles, effectivePermissionsCacheKey}`)와 완전 동일. 별도 DTO 생성은 YAGNI 위반 (CLAUDE.md ULTIMATE INVARIANTS 원칙 3 — 기존 구조 우선). 추후 `/me` 전용 필드 도입 시점에 분리
- **`DelegatingSecurityContextRepository(HttpSession + RequestAttribute)`** — HttpSession은 영속, RequestAttribute는 단일 요청 캐시. 같은 요청 내 후속 필터/핸들러가 동일 컨텍스트를 일관되게 본다. `AuthService.login`이 `changeSessionId()` 직후 `saveContext(req, res)` 호출하여 새 세션에 컨텍스트 저장
- **logout: `session.invalidate` + `clearContext` + `Cookie SESSION; Max-Age=0`** — `clearContext`는 현재 요청 thread-local 정리. `Set-Cookie`는 클라이언트 브라우저 SESSION 쿠키 즉시 만료. `anyRequest().authenticated()` 가드로 미인증→401, CsrfFilter가 CSRF 미제공→403 자동 처리

### 다음 세션 컨텍스트 (A1.5)
- **A1.5** — `AuthScenarioIntegrationTest` `@SpringBootTest` + Testcontainers Postgres 15-alpine 1건 (`disabledWithoutDocker=true`, 로컬 SKIP / CI ubuntu 실행). CSRF 발급→로그인→/me→5회 wrong PW(401×5)→6회째=423→Clock 16분 진행→재시도 성공→logout(204)→/me=401. `LoginAttemptTracker`는 `@TestConfiguration` `@Primary` 빈으로 mutable Clock 주입
- 마일스톤 종료: `gsd-audit-milestone A1` + `progress.md` 마일스톤 블록 + commit + push + `gh pr checks` 그린

---

## 2026-04-26 — A1.3 LoginController + in-memory lockout (ADR #23)

### 완료
- [A1.3] **`POST /api/auth/login`** — `AuthController` + `AuthService.login` (`@Transactional`). 성공 시 `last_login_at` UPDATE + `changeSessionId()` (session fixation 방어). 실패 시 5/15min lockout 카운트
- [A1.3] **`LoginAttemptTracker`** — in-memory `ConcurrentHashMap<String, Attempt>`, key=lowercased email, 5회 실패 → 15분 잠금. `Clock` 주입 (테스트 시간 제어). 만료는 lazy 검증 (`isLocked` 호출 시점에 시계 기반 판정)
- [A1.3] **timing-safe BCrypt verify** — `@PostConstruct`에서 `passwordEncoder.encode()`로 dummy hash 동적 생성. 미존재 user / `is_active=false` / `locked_at != null` 모두 dummy verify 호출 → 실제 verify와 동일 시간. 응답은 INVALID_CREDENTIALS (계정 상태 누설 금지, docs/03 §2.3 enumeration 방지)
- [A1.3] **flat error shape** — `ErrorResponse(code, reason, retryAfterSec)` + `JsonInclude.NON_NULL`. `AuthExceptionHandler(@RestControllerAdvice)` → InvalidCredentialsException → 401, AccountLockedException → 423 + retryAfterSec. docs/02 §7.4 인증 specific 응답 (일반 envelope §7.2와 별개)
- [A1.3] **`User.recordLoginAt(OffsetDateTime)`** entity 메서드 — `last_login_at` setter (V2 컬럼)
- [A1.3] **`LoginAttemptTrackerTest` 4건 + `LoginControllerIntegrationTest` 8건** — 모두 PASS. @WebMvcTest slice. tracker singleton 격리는 `@BeforeEach`에서 `recordSuccess(테스트 키들)` 호출
- [A1.3] **ADR #23 docs/00 §5 등록** — lockout backing = in-memory ConcurrentHashMap (MVP 단일 인스턴스 가정). 다중 인스턴스/Redis 도입 시 `LoginAttemptTracker` interface 교체

### 핵심 결정
- **timing-safe dummy hash 동적 생성** — 정적 상수는 형식 오류 시 BCrypt iterations 미실행 → timing leak 위험. 부팅 +200ms 수용
- **비활성·관리자 잠금도 INVALID_CREDENTIALS로 매핑** — docs/03 §2.3 enumeration 방지. 잠금 누적은 별개로 ACCOUNT_LOCKED + retryAfterSec 노출
- **@Transactional은 AuthService.login에 적용** — last_login_at UPDATE + (후속) audit insert 단일 단위, docs/02 §7.4 TX=REQUIRED 충족
- **컨텍스트 9% 한계** — 본 세션은 commit만 수행. dev sync (context.md/tasks.md SESSION PROGRESS 갱신)는 다음 세션 시작 시 dev-docs-update로 처리

### 다음 세션 컨텍스트 (A1.4 ~ A1.5)
- **A1.4** — `GET /api/auth/me` (docs/02 §7.4 line 847-868: `{user, departments, roles, effectivePermissionsCacheKey}`. departments는 빈 배열 stub, kind='human' 하드코딩, roles=`[role]`, cacheKey=`userId:role:v0`) + `POST /api/auth/logout` (`session.invalidate()` + `Set-Cookie SESSION=; Max-Age=0`). 모두 `anyRequest authenticated`로 SecurityConfig 매처 변경 불필요
- **A1.5** — Testcontainers Postgres @SpringBootTest 종합 시나리오 1건 + `gsd-audit-milestone A1` + PR 머지

### 진행 정책
- 자율 모드 유지. 사용자 큐: A1.4 종료 시점 컨텍스트 65% 초과 시 자동 pause-work, 아니면 A1.5까지 이어감
- 다음 세션 첫 작업: dev-docs-update (이번 세션의 A1.3 SESSION PROGRESS 동기화) → A1.4 진입 (TDD RED 5건부터)

### 블로커
- 없음

---

## 2026-04-26 — A1.2 SecurityConfig 본 wiring (TDD, dev + Superpowers 첫 적용)

### 완료
- [A1.2] **`SecurityConfig` 본 wiring** — httpBasic/formLogin/logout 모두 disable, custom AuthController(A1.3+) 자리 마련. 매처 분리: `/api/health` + `/api/auth/csrf` + `/api/auth/login` permitAll, anyRequest authenticated. `HttpStatusEntryPoint(401)` — SPA용 (redirect 대신 401, docs/03 §2.4)
- [A1.2] **`CsrfTokenRepository` bean 추출** — `CookieCsrfTokenRepository.withHttpOnlyFalse()`. `CsrfTokenController`에서 `saveToken()` 명시 호출하여 deferred 모드 우회
- [A1.2] **`CsrfTokenController`** (`GET /api/auth/csrf`) — permitAll, `XSRF-TOKEN` cookie + `{ csrfToken }` body 동시 반환. Spring Security 6 deferred CSRF 함정(`getToken()`만으로는 cookie 자동 발급 보장 안 됨) 해결 — `repo.saveToken(token, req, res)` 명시 호출
- [A1.2] **`SecurityIntegrationTest` 5건** (@WebMvcTest slice, DB 무관) — getCsrf+cookie / POST without CSRF→403 / POST valid CSRF→401 / GET /me→401 / GET /health→200. **모두 PASS**
- [A1.2] **dev/active/a1-auth-impl/{plan,context,tasks}.md** + dev/process/{session}.md (dev 스킬 첫 bootstrap)

### 핵심 결정
- **CSRF plain handler** (`CsrfTokenRequestAttributeHandler`) — XOR mask 비활성화. docs/02 §7.1 평문 토큰 계약과 일치 (cookie ↔ header 단순 비교)
- **deferred CSRF 우회** — Spring Security 6에서 GET 응답에 cookie 자동 발급은 보장 안 됨 → controller가 `csrfRepo.saveToken()`을 명시 호출
- **로컬 logout disable** — A1.4에서 자체 endpoint로 처리 (Spring 기본 `LogoutFilter`는 form 기반)
- **A1.5 재정의** — HANDOFF.json의 권한 매트릭스 백엔드 권위는 별도 phase로 분리, A1.5 = 통합 시나리오 + 마일스톤 종료 (사용자 자율 결정)

### 다음 세션 컨텍스트 (A1.3 ~ A1.5)
- **A1.3** — LoginController + in-memory `LoginAttemptTracker` (5회 실패/15분 lockout, ConcurrentHashMap, Clock 주입). **ADR #23 docs/00 §5 추가 필요** (lockout backing store). timing attack 회피 (미존재 user에도 dummy BCrypt verify). `User.recordLoginAt(OffsetDateTime)` setter 추가
- **A1.4** — `GET /me` (ADR #22 응답 — `effectivePermissionsCacheKey = "userId:role:v0"` 임시) + `POST /logout` (HttpServletRequest.session.invalidate())
- **A1.5** — Testcontainers Postgres @SpringBootTest 종합 시나리오 1건 + gsd-audit-milestone

### 진행 정책
- 새 환경: dev(4) + Superpowers(12, TDD 강제) + GSD context-only. 트리거 경합 0
- 자율 모드 유지 — A1.3~A1.5는 새 세션(컨텍스트 클린)에서 동일 정책으로 진입

### 블로커
- 없음. A1.3 진입 시 ADR #23만 기록하면 됨

---

### 완료 (옵션 3 +α — 하이브리드 사이클)
- [A] **backend/ Spring Boot 3.3.4 + Java 21 scaffold** — `build.gradle.kts` (Kotlin DSL, toolchain 21), `settings.gradle.kts`, `gradle.properties`, `.gitignore`. 의존성: spring-boot-starter-web/security/data-jpa/validation, **spring-session-jdbc** (Redis 아님, ADR #12), postgresql, flyway-core + flyway-database-postgresql, software.amazon.awssdk:s3:2.28.16, jackson-databind. test workingDir = `projectDir`로 fixtures 상대경로(`../docs/`) 안정화
- [A] **`IbizDriveApplication.java`** — `@SpringBootApplication` 메인. **`HealthController`** `GET /api/health` → `{"status":"ok"}` (보안 설정 검증용)
- [A] **`SecurityConfig` skeleton** — `CookieCsrfTokenRepository.withHttpOnlyFalse()` (XSRF-TOKEN 쿠키 ↔ X-CSRF-Token 헤더, ADR #11), `/api/health` permitAll, 그 외 authenticated, httpBasic placeholder (A1에서 form/SSO 교체)
- [A] **`CorsConfig`** — `allowCredentials=true`, allowedOrigins from `${ibizdrive.cors.allowed-origins}`, exposed: `Tus-Resumable`/`Upload-Offset`/`Location`/`X-Request-Id`/`X-RateLimit-Remaining`. allowed: `Content-Type`/`X-CSRF-Token`/`X-Request-Id`/Tus 헤더 4종
- [A] **`application.yml`** — Spring Session JDBC (table-name `SPRING_SESSION`, **initialize-schema: never** = Flyway가 schema 관리), datasource localhost:5432/ibizdrive, S3 → MinIO localhost:9000, multipart **disabled** (tus 사용, ADR #13), session timeout `PT8H`, cookie `http-only=true`/`secure=false (dev)`/`same-site=lax`/name=`SESSION`
- [A] **`db/migration/V1__init.sql`** — Spring Session JDBC 스키마(`SPRING_SESSION` + `SPRING_SESSION_ATTRIBUTES`, 인덱스 IX1/IX2/IX3, ON DELETE CASCADE) + `users` stub(id UUID PK, email/display_name, password_hash nullable for SSO, deleted_at, partial unique index on `lower(email) WHERE deleted_at IS NULL`). 도메인 테이블은 V2+
- [A] **`docker-compose.yml`** — postgres:15-alpine + minio (RELEASE.2024-10-13) + minio-init 원샷(버킷 자동 생성). **Redis 없음** (JDBC 세션 + in-process SSE). healthcheck 포함
- [A] **A0: backend `NormalizeUtil.java`** — 7-step pipeline 완전 구현. `normalizeFileName` (1-2-3-6-7), `normalizedNameForDedup` (1-2-3-5-6-7, Locale.ROOT), `normalizeForSearch` (1-2-3-5-4-6, collapse). 제어문자 처리: NUL→throw / C0→space / DEL/C1/ZWSP(0x200B-200F)/BIDI(0x202A-202E)/WJ(0x2060)/BOM(0xFEFF) drop. 공백 통일: NBSP/U+2000-200A/202F/205F/3000→space. 검증: empty/length 255/`/`·`\\` 금지/trailing dot/예약어(CON/PRN/AUX/NUL/COM1-9/LPT1-9, base name 기준 case-insensitive). `NormalizationException` (code 필드, fixtures errorCodes 일치)
- [A] **A0: backend `NormalizeUtilTest`** — JUnit `@TestFactory` 동적 테스트, Jackson으로 `../docs/normalize-fixtures.json` 로드(IDE/Gradle 둘 다 동작하도록 fallback 경로 3종), 38 fixtures × 3 함수 = 114 dynamic test
- [A] **A0: frontend `src/lib/normalize.ts` 정식 작성** — backend `NormalizeUtil.java` 1:1 미러. 같은 7-step, 같은 에러 상수 6종, `NormalizationError extends Error` (code 필드)
- [A] **A0: frontend `src/lib/normalize.test.ts`** — Vitest, `readFileSync(resolve(__dirname, '../../../docs/normalize-fixtures.json'))`로 fixtures 로드, 38×3 = **114 tests PASS** (54ms)
- [A] **`.github/workflows/ci.yml`** — frontend(Node 20 + npm ci + typecheck/lint/test) + backend(Temurin 21 + gradle test) 두 잡. ADR #16 게이트: 어느 한쪽이든 fixtures mismatch면 머지 차단
- [A] **검증** — frontend: typecheck PASS · lint PASS · normalize 114 tests PASS. backend: 로컬 JDK 없어 컴파일 미실행 → CI에서 검증

### 핵심 구현 결정
- **Spring Session JDBC, Redis 미도입** — 사용자 명시(ADR #12). docker-compose에서 Redis 제거, application.yml에서 `store-type: jdbc` + `initialize-schema: never` (Flyway가 권위)
- **fixtures workingDir 처리** — Gradle test는 `workingDir = projectDir`(=`backend/`)로 고정, JUnit 테스트는 `../docs/normalize-fixtures.json` + IDE 케이스 대비 2개 fallback. 단일 진실 출처가 빌드 도구 따라 깨지지 않도록
- **NUL은 step 2에서 throw, step 7 검증 전 차단** — fixtures `control_001` 입력 `"a\u0000b.txt"` → ERR_NUL_CHAR. 빈 문자열로 만든 뒤 ERR_EMPTY로 떨어지면 디버깅 불편
- **JS `for...of` + `codePointAt`** — surrogate pair 안전. 공백 통일/제어문자 strip 모두 동일 패턴, Java `Character.charCount` 루프와 1:1 대응
- **터키어 İ 케이스(`case_002`)는 JS `toLowerCase()` 기본 동작과 Java `toLowerCase(Locale.ROOT)` 결과 일치** — `i̇`(i + U+0307). fixtures가 양쪽 cross-validation으로 보장

### 다음 세션 컨텍스트
- **A1 (인증) 진입 — 수동 모드 유지** — Spring Security `UserDetailsService`, BCrypt, login form, CSRF Cookie 전달, 8h 세션, audit 이벤트(LOGIN_SUCCESS/FAIL/LOGOUT). users 테이블 V2 확장(role/locked_at/last_login_at)
- **CI 미검증 영역** — backend Gradle test가 실제 GitHub Actions에서 통과하는지 확인 전. 첫 push 시 워크플로 실패하면 V1__init.sql/Flyway 의존성 정렬 점검 필요
- **로컬 개발 가이드 미작성** — `docker-compose up -d postgres minio minio-init` → `cd backend && ./gradlew bootRun`. README 또는 docs/00 §6에 추가 검토
- **A1.5 권한 매트릭스 코드화** — docs/03 §3 PermissionEnum 9종을 `com.ibizdrive.security.Permission` enum + Preset 매트릭스로 옮기고 `@PreAuthorize` SpEL helper 작성

### 블로커
- 없음. 사용자가 A1 spec 승인하면 진행

---

## 2026-04-25 — Track A 큐 #3: 정규화 spec + fixtures + SSE enum + PURGE 권한

### 완료 (큐 #3 — docs only)
- [A] **docs/00 §5 ADR 7건 추가** (#11~#17): Spring Boot, 쿠키 세션, tus-java-server, SSE, .env build-time, 정규화 fixtures 공유, 권한 매트릭스 백엔드 권위. ADR #7/#8은 §5.1 Superseded 섹션에 standard ADR 패턴으로 보존
- [A] **docs/00 §1.3 스택 갱신** — TBD → Spring Boot 3.x + Java 21 + 부속 스택 명시
- [A] **docs/00 §4.4 백엔드 마일스톤(A0~A7) 신설** — A6→A1.5 흡수, M5.1→A4 흡수, M12→A4 후반 통합
- [A] **docs/02 §7 endpoint 매트릭스 전면 재작성** — Auth/Folders/Files/Upload(tus)/Search/Shares/Permissions/Trash/Admin/SSE 그룹화. Guard(@PreAuthorize)/TX(REQUIRED + FOR UPDATE)/Norm(NormalizeUtil 적용 지점)/SoftDel(WHERE deleted_at)/Errors 5개 컬럼. tus finalize 7-step TX 상세 명시. 인증 헤더 JWT → 쿠키 세션 + CSRF double-submit 갱신
- [A] **docs/02 §3 정규화 spec 정식 명세화** — 7-step pipeline (NFC → 제어문자 strip → 공백 통일 → collapse → lowercase → trim → validate). 함수 분리표(filename/dedup/search), 단계별 제어문자 표(NUL/C0-C1/ZWSP/BIDI/BOM), 길이/금지문자/예약어/trailing dot 검증 규칙
- [A] **docs/normalize-fixtures.json 신규** (38 케이스): nfc(4) / whitespace(8) / control(4) / case(3) / extension(2) / forbidden(3) / reserved(4) / length(3) / dot(2) / unicode(3) / search(2). errorCodes enum 6종. Vitest+JUnit 양쪽 검증 게이트 (CLAUDE.md §3 원칙 11)
- [A] **docs/01 §15 SSE 본문 재작성** — ADR #14에 의해 폴링 폐기, MVP부터 SSE. SseEventType enum 16종(FILE 7 / FOLDER 6 / PERMISSION 3), useRealtimeSync 훅 구현, 이벤트→queryKeys 무효화 매트릭스, 연결 정책(자동 재연결/heartbeat/다중 폴더 구독)
- [A] **docs/02 §7.13.1 SSE enum 동기화** — FOLDER_UPDATED 분리(RENAMED), PERMISSION_GRANTED/REVOKED 추가, FILE_VERSION_CREATED/FOLDER_PURGED 신설
- [A] **docs/03 §3 권한 매트릭스 정식 명세화** — 권한 enum 9종(READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/**PURGE**), Preset×권한 매트릭스, 시스템 ROLE(MEMBER/AUDITOR/ADMIN), 권한 상속(deny 우선 재귀 CTE), `@PreAuthorize` 패턴 예시, 403 응답 포맷
- [A] **검증** — 코드 변경 없음 → typecheck PASS · lint PASS · 190 tests PASS (회귀 없음)

### 핵심 설계 결정
- **PURGE는 ROLE ADMIN 전용** — 노드 admin preset에도 부여하지 않음. 노드 단위 권한 위임이 영구 삭제로 번지지 않도록 이중 안전장치
- **fixtures 단일 진실 출처** — `docs/normalize-fixtures.json`을 frontend(Vitest) + backend(JUnit) 양쪽이 로드, CI 게이트로 드리프트 차단. 38 케이스로 NFC/공백/제어/대소문자/예약어/길이/다국어 커버
- **터키어 İ는 Locale.ROOT lowercase** — `i̇`(i + U+0307 combining dot above) 결과. JS `toLowerCase()`와 Java `toLowerCase(Locale.ROOT)` 동일 동작 (fixtures `case_002`)
- **SSE 낙관적 setQueriesData 안 씀** — race 회피, 단일 무효화 경로(`invalidateQueries`)로 일관 처리
- **FILE_MOVED는 source+target 양쪽 fan-out** — `scope.folderIds` 배열에 두 폴더 ID 동시 포함, 클라가 양쪽 모두 무효화

### 다음 세션 컨텍스트
- **사용자 결정 대기** — 옵션 1: 로컬 인프라(docker-compose + JDK 21) → #4~#12 자율 / 옵션 2: 백엔드 팀 인계 / 옵션 3: 하이브리드(#4 scaffold만)
- **fixtures 누락 영역** — 현재 38 케이스에서 다루지 않은 영역: combining mark RTL 텍스트 시퀀스, surrogate pair, 일본어 가나/탁점 분리. 백엔드 구현 단계(A0)에서 NormalizeUtil 작성하며 추가 케이스 발견 시 fixtures 확장
- **A0 진입 시 검증** — `frontend/src/lib/normalize.ts` 신규 작성 + 기존 fixtures 로드 테스트. 현 frontend는 `normalizeFileName` 등이 별도 라이브러리에 없음 (mock backend가 모든 정규화 처리) — A0에서 분리 필요

### 블로커
- 없음 (큐 #4 진입은 사용자 결정 게이트)

---

## 2026-04-25 — Track H: docs/03 §1·§2 보강 (위협 모델 + 인증 흐름)

### 완료
- [H] **docs/03 §1 위협 모델** — Assets 표(A1~A7), Trust Boundary 다이어그램, **STRIDE 매트릭스**(Spoofing/Tampering/Repudiation/Info Disclosure/DoS/Elevation 카테고리별 자산·위협·완화책 표), 잔여 위험(out of scope) 명시
- [H] **docs/03 §2 인증** — 인증 방식(SSO/자체+MFA), 토큰 모델(access/refresh/sessionId 표), **시퀀스 다이어그램 3종**(로그인 SSO 콜백 / Access 만료+Refresh 회전 / 로그아웃), 비활성 정책 표, 서비스 계정 정책, audit 이벤트 동기화 메모
- [H] 백엔드 스택 미정 부분은 **"TBD: A 트랙에서 확정"** 으로 명시 (RateLimiter 구현체 / NestJS vs Spring / users.external_id / API 키 회전 주기)
- [H] **검증** — 코드 변경 없음 → typecheck PASS · lint PASS · 190 tests PASS (회귀 없음 확인)

### 핵심 설계 결정
- **클레임 최소화** — JWT는 sub/role/exp/iat/sessionId만, 권한 평가는 항상 DB. 토큰 단독 신뢰 금지(Spoofing 완화)
- **Refresh 1회용 회전 + replay 감지** — 이미 사용된 refresh가 다시 들어오면 세션 전체 강제 종료 + audit
- **app role과 audit role 분리** — DB 레벨 REVOKE UPDATE/DELETE on audit_log (CLAUDE.md §3 원칙 8과 일치)
- **STRIDE를 표 중심으로 정리** — 자산 ID(A1~A7) 참조 가능, 향후 §3 권한 매트릭스/§4 감사 정책에서 cross-link 용이

### 다음 세션 컨텍스트
- **A 트랙 (백엔드 합류)** — TBD 항목 채우기: NestJS or Spring 결정 → users.external_id 매핑 / RateLimiter 구현체 / API 키 회전 정책 / sessions 테이블 스키마
- **§3 권한 매트릭스** — 본 위협 모델의 "프론트 권한 우회"·"권한 상속 버그" 위협을 실제 엔드포인트×권한 매트릭스로 매핑 (현재 §3.1 preset만 존재)
- **§4 감사 정책 보강** — `user.session.revoked` 이벤트 추가 + audit_level=strict 폴더 정의 가이드

### 블로커
- 없음 (A 트랙 합류 전까지는 TBD 표기로 지연 가능)

---

## 2026-04-25 — Track G: BulkActionBar "이름 변경" 버튼

### 완료
- [G] **BulkActionBar 이름 변경 버튼** — 단일 선택 시만 활성, 클릭 시 F2와 동일한 `openRename(id, name)` 호출
- [G] **단일 항목 name lookup** — `useFilesInFolder(folderId, sort, dir)` 캐시에서 단일 선택 항목의 이름 조회. `useSortParams`로 현재 정렬 키 사용 → FileTable과 동일 캐시 슬롯 hit
- [G] **disabled UX** — 다중 선택 시 disabled + `title="단일 선택 시 사용 가능"` tooltip + aria-disabled
- [G] **테스트 4건 신규** — 단일 활성+다이얼로그 오픈 / 다중 비활성+tooltip / cache miss 비활성 / 폴더 단일 활성 (정책)
- [G] **검증** — typecheck PASS · lint PASS · 190 tests PASS (186→+4)

### 핵심 설계 결정
- **정책: count===1 활성, 폴더/파일 구분 없음** — RenameDialog와 백엔드(`api.renameFile`/`renameFile.test`)가 양쪽을 모두 지원하므로 BulkActionBar에서 추가로 막을 이유 없음. 추후 권한 모델(03 §3) 확정 시 `usePermission().edit` 게이트 외 추가 분기 필요 여부 재검토
- **Cache miss 시 안전 비활성** — items=undefined(로딩 중)일 때 `singleItem`이 없으면 disabled로 폴백. 로딩 끝나면 자동으로 활성화
- **`can.edit` 게이트만 사용** — 권한 없는 사용자에게는 버튼 자체 미노출 (BulkActionBar 다른 액션과 동일 패턴)

### 다음 세션 컨텍스트
- **권한 매트릭스(03 §3) 확정 후** — `can.edit` semantic 재검토 (folder rename은 `move` 권한? `edit`?)
- **Rename 외 단일 액션 추가 시** — 같은 패턴(`useFilesInFolder` lookup + count===1 게이트)으로 확장. 다수 단일 액션이면 `useSelectedSingleItem()` 헬퍼 추출 검토

### 블로커
- 없음

---

## 2026-04-25 — Track F: 다크 모드 토글 (TopBar Sun/Moon)

### 완료
- [F] **lib/theme.ts** — `THEME_STORAGE_KEY`/`getStoredTheme`/`getSystemTheme`/`getInitialTheme`/`applyTheme`/`persistTheme`. SSR 안전(window/document 가드), localStorage 에러 swallow (11 tests)
- [F] **hooks/useTheme** — mount effect로 SSR 동기화, toggle/setTheme/theme 반환
- [F] **components/topbar/ThemeToggle** — `lucide-react` Sun/Moon 아이콘, button + aria-pressed + aria-label, 키보드(Enter/Space) 동작 (5 tests)
- [F] **components/topbar/TopBar** — main 상단 banner, 우측 정렬에 ThemeToggle
- [F] **(explorer)/layout** — TopBar 마운트
- [F] **app/layout** — FOUC 방지 inline script (hydration 전 동기 [data-theme] 적용)
- [F] **`lucide-react` 추가** — frontend/package.json dependencies
- [F] **검증** — typecheck PASS · lint PASS · 186 tests PASS (170→+16)

### 핵심 설계 결정
- **`[data-theme="dark"]` on `<html>`** — globals.css가 이미 :root와 [data-theme="dark"]를 정의해 둠. JS는 attribute 토글만 담당
- **localStorage 우선 + prefers-color-scheme fallback** — 사용자가 한 번이라도 토글하면 그 선택을 영속, 그 전까지는 시스템 설정 따라감
- **FOUC 방지 inline script** — Next.js App Router는 RSC 첫 페인트 시점에 React 마운트 전 단계가 있음. `<head>`의 `dangerouslySetInnerHTML` 동기 스크립트로 해결 (theme.ts 로직과 동일 규칙 inline 복제)
- **role=switch 대신 button + aria-pressed** — eslint jsx-a11y 가 role=switch에 aria-checked 요구. button + aria-pressed가 토글 패턴에서 더 보편 (WAI-ARIA Button pattern)
- **lucide-react 도입** — 기존 의존성에 아이콘 라이브러리 없었음. 향후 다른 곳에서도 활용 가능

### 다음 세션 컨텍스트
- **시스템 prefers-color-scheme 변화 감지 미구현** — 사용자가 OS에서 라이트→다크 전환 시 자동 동기화 X. 필요 시 `matchMedia.addEventListener('change', ...)`를 useTheme에 추가
- **다크 모드 시각 검수 필요** — globals.css의 [data-theme="dark"] 토큰이 실제 컴포넌트(FileTable/Breadcrumb/UploadOverlay 등)에서 의도대로 보이는지 e2e 또는 수동 QA 필요
- **/admin 페이지에도 TopBar 적용?** — 현재는 (explorer)/layout만. admin/layout.tsx는 별도 헤더 — 통일 시 공용 컴포넌트로 승격 검토

### 블로커
- 없음

---

## 2026-04-25 — Track D: e2e Playwright 도입

### 완료
- [M_e2e] **@playwright/test 도입** + `playwright.config.ts` (chromium만, webServer 자동 기동, baseURL 3000, retries CI=2)
- [M_e2e] **e2e/routing.e2e.ts** — / → /files 리다이렉트 / 사이드바·breadcrumb / FolderTree 클릭 → URL+breadcrumb 갱신 / `/` 키 → app:focus-search 디스패치 (4 specs)
- [M_e2e] **e2e/move.e2e.ts** — BulkActionBar 이동 다이얼로그 → 대상 선택 → 토스트 / source 폴더 disabled (2 specs)
- [M_e2e] **e2e/trash.e2e.ts** — BulkActionBar 휴지통으로 → 토스트 + bar 사라짐 (1 spec)
- [M_e2e] **e2e/tsconfig.json** — main tsconfig에서 e2e/ 제외 + Playwright types 별도 설정
- [M_e2e] **package.json** — `test:e2e`, `test:e2e:ui` 스크립트 추가
- [M_e2e] **.gitignore** — playwright-report / test-results / playwright/.cache

### 핵심 설계 결정
- **chromium only (v1.0)** — 키바인딩/DnD 안정성 우선, firefox/webkit은 프로덕션 안정화 후
- **DnD 시나리오 → 다이얼로그 경로로 대체** — dnd-kit PointerSensor activationConstraint(distance:5px) + Playwright mouse 시퀀싱이 flaky. DnD 통합은 후속 (E2E_dnd_followup) — `page.mouse.move`를 50ms 단위 다단계 + `force: true` 필요
- **검색 input UI 미구현** — '/' 키 디스패치까지만 검증. 검색 input 도입 (M_search) 후 input.focus 검증 추가
- **휴지통 Undo 미구현** — soft delete + 토스트만. 복원 흐름은 M_trash 후속

### 다음 세션 컨텍스트
- **브라우저 미설치** — 첫 실행 전 `npx playwright install chromium` 필요. CI 통합 시 `actions/setup-node` 후 install step 추가
- **CI 통합은 후속** — `.github/workflows/e2e.yml`에서 webServer reuseExistingServer:false + retries 2
- **mock 데이터 기반** — MOCK_FILES/MOCK_TREE를 변경하면 e2e 셀렉터(영업팀/인사팀/내 드라이브) 동기화 필요
- **vitest 단위 테스트와 분리** — testMatch는 `*.e2e.ts`, vitest는 `*.test.ts(x)` — 충돌 없음

### 블로커
- 검색 input UI / 휴지통 Undo 미구현 — 시나리오 부분 적용. 후속 마일스톤에서 보강 예정

---

## 2026-04-25 — Track B: M12 감사 로그 페이지 (mock)

### 완료
- [M12] **types/audit.ts** — `AuditEventType` (docs/03 §4.1 mirror, audit.exported 포함) + `AuditLogEntry` + `AuditLogFilters` + `AuditLogPage`
- [M12] **api.getAuditLogs** — filters/page/pageSize → 정렬(occurredAt desc) + 60-entry mock 데이터 (6 tests)
- [M12] **lib/auditCsv.ts** — RFC 4180 quoting + `toAuditCsvBlob` (UTF-8 BOM, text/csv MIME) (6 tests)
- [M12] **hooks/useAuditLogs** — useQuery wrapper, queryKey에 filters/page 포함 → 자동 재요청 (2 tests)
- [M12] **/admin/audit/logs** — Filters + Table + Pagination + CSV export 페이지 (docs/04 §7)
- [M12] **components/audit** — AuditFilters (4 tests), AuditTable (6 tests, aria-rowcount/rowindex 포함), AuditPagination
- [M12] **/admin/layout.tsx** — 관리자 헤더 (감사 로그 링크)
- [M12] **docs/03 §4.1** — 클라이언트 mirror 표시 + audit.exported 추가
- [M12] **검증** — typecheck PASS · lint PASS · 170 tests PASS (M10 기준 146 → +24 신규)

### 핵심 설계 결정
- **mock 우선, 백엔드 분리 (A 트랙)** — getAuditLogs는 클라이언트 mock. 실제 연결 시 audit.exported 서버 기록 필요 (docs/04 §7.2)
- **CSV는 현재 페이지만 export (mock)** — 백엔드 연결 후 전체 필터 결과 서버 스트리밍으로 교체
- **필터 변경 시 자동 setPage(1)** — UX 일관성. 페이지네이션 키에 filters 포함되어 자동 refetch
- **UTF-8 BOM Blob** — Excel에서 한글 깨짐 방지. BOM 자체는 toAuditCsv 결과에 prefix
- **resourceType=null 허용** — 시스템 이벤트(system.backup.completed) 처리. UI는 `[type]` 또는 dash로 표시
- **즉시 반영 폼 (Apply 버튼 없음)** — onChange로 즉시 부모 setState. 디바운스는 actorQuery 입력 압박 시 부모에서 추가

### 다음 세션 컨텍스트
- **백엔드 연결 (A 트랙)** — `api.getAuditLogs`를 fetch로 교체 + `audit.exported` 서버 기록 추가
- **상세 뷰 (docs/04 §7.3)** — before/after diff 표시 + 같은 세션 이벤트 연결은 v1.x
- **IP/리소스 필터 (docs/04 §7.1)** — mock 범위 외. 백엔드 연결 후 UI 추가
- **권한 체크** — 감사 로그 접근은 admin role 필수. ProtectedRoute는 §3 권한 매트릭스 작성 후
- **/admin 다른 페이지** — dashboard / users / departments 등 docs/04 §2 라우트 v1.x

### 블로커
- 없음

---

## 2026-04-25 — Track C: 회고/리팩토링 (sonner 분리 + 무효화 헬퍼 + mock 공통화)

### 완료
- [C-1] **sonner 도입** — providers.tsx `<Toaster position="bottom-right" richColors />`
- [C-1] **hooks 토스트 분리** — `useDeleteBulk`/`useMoveBulk`/`useRenameFile`은 결과만 반환, 토스트는 호출부 (`Options.onSuccess/onError`)
- [C-1] **호출부 마이그레이션** — BulkActionBar / MoveFolderDialog / DndProvider / RenameDialog 모두 hook-level 콜백으로 토스트 호출
- [C-2] **lib/queryKeys.ts invalidations** — `afterFilesMoved` / `afterRename` / `afterDelete` 헬퍼. 무효화 매트릭스를 한 곳으로 집결 (3 hooks가 같은 룰 공유)
- [C-2] **filesListPrefix(folderId)** — sort/dir 변종 전체 prefix 매칭용 키 추가 (직접 단일 read는 filesInFolder)
- [C-3] **test/setup.ts** — sonner 글로벌 vi.mock (Toaster=null + toast methods=vi.fn)
- [C-3] **test/mocks/sonner.ts** — `toastSpy(method)` / `resetSonnerToastMock()` 공통 헬퍼
- [C-3] **호출부 테스트 갱신** — MoveFolderDialog.test (toast success/error 추가) / RenameDialog.test (toast success + inline-error-no-toast)
- [C] **검증** — typecheck PASS · lint PASS · 146 tests PASS

### 핵심 설계 결정
- **hook-level Options 패턴** — 호출부가 매번 mutate options을 넘기지 않고 hook 호출 시점에 콜백 등록. mutation 완료 시 컴포넌트가 mount되어 있어야 콜백이 발화 (TanStack Query v5의 onSuccess는 observer 기반)
- **MoveFolderDialog `close()` 즉시 호출** — ClientFilesPage에서 항상 mount되므로 다이얼로그가 isOpen=false로 null 반환해도 hook은 살아있음 → 콜백 발화 보장
- **RenameDialog는 onSuccess만 hook-level** — 실패는 inline alert(setError)로 다이얼로그 유지가 UX 우선
- **vi.mock 글로벌 setup** — vi.mock 팩토리는 호이스트되어 import된 심볼 참조 불가 → setup.ts에서 inline 팩토리로 처리. 헬퍼는 vi.mocked(toast)로 사후 접근

### v1.x 후속 (이번 스코프 제외)
- **ApiError 타입화** — `unknown` throws → 구조화 타입 (status/code/details)
- **vi.hoisted 표준화** — 일부 테스트의 vi.mock 패턴 통일
- **useStorageQuota env-driven** — quota 표시 컴포넌트의 환경변수 readout

### 다음 세션 컨텍스트
- **mutation hooks 추가 시 invalidations 헬퍼 재사용** — 새 패턴 발생 시 헬퍼에 추가
- **ApiError 타입은 백엔드 연결 (A 트랙) 시 필수** — 현재 mock은 `{ status, code }` plain object를 throw

### 블로커
- 없음

---

## 2026-04-25 — M10 완료 (고급 키보드 + 접근성 마무리)

### 완료
- [M10] **api.renameFile mock** — VALIDATION_ERROR (빈 이름) / RENAME_CONFLICT (같은 부모 정규화 중복) / NOT_FOUND, 폴더 시 MOCK_TREE도 갱신 (6 tests)
- [M10] **에러 코드 정합성** — docs/02 §8의 기존 코드 `RENAME_CONFLICT`(409) / `VALIDATION_ERROR`(400) 그대로 사용 (원칙 #12). 신규 코드 추가 없이 계약 유지
- [M10] **stores/renameUi.ts** — `{ isOpen, targetId, targetName, error }` + open/close/setError (3 tests)
- [M10] **useRenameFile** — markPending → renameFile → invalidate(filesInFolder/fileDetail/+folderTree+folder if folder) → unmarkPending + close, 실패 시 setError (5 tests)
- [M10] **RenameDialog** — role=dialog aria-modal, input focus + select-all, 이전 focus 복귀, role=alert 에러, 동일/빈 이름 disabled (7 tests)
- [M10] **useGlobalShortcuts** — `/` 키 → window CustomEvent 'app:focus-search' (input/textarea/contenteditable/modifier 시 무시, JSDOM contentEditable 폴백 포함) (7 tests)
- [M10] **FileTable handleKeyDown 확장** — Shift+↑↓ (anchor 유지 selectRange), Ctrl/Meta+↑↓ (focus only), F2 (단일 선택 또는 focus → openRename), Delete (selection or focus → confirm → useDeleteBulk)
- [M10] **ClientFilesPage 마운트** — RenameDialog + useGlobalShortcuts() 호출
- [M10] **검증** — typecheck PASS · lint PASS · 136 tests PASS (M7 기준 108 → +28 신규)
- [M10] **로드맵** — docs/01 §18 M10 행에 완료 마커(2026-04-25)

### 핵심 설계 결정
- **F2 다이얼로그 (인라인 X)** — 가상화 컨테이너에서 인라인 편집은 row 리렌더링으로 입력 휘발 위험. 다이얼로그는 MoveFolderDialog 패턴 그대로 재사용
- **Shift+↑↓ anchor 안정성** — selectRange가 lastClickedId를 변경하지 않으므로 진동 없음. M4 anchor 폴백(null/pending/폴더 변경) 그대로 동작
- **Ctrl/Meta+↑↓ alias** — 현재 ↑↓가 이미 focus-only이므로 modifier는 §12.1 키맵 명세 만족용 별칭
- **Delete native confirm** — 브라우저 내장 포커스 트랩/스크린리더 지원, MVP zero-cost. 디자인 일관성 필요해지면 ConfirmDialog로 교체 (M14/M15)
- **`/` 트리거는 lazy 이벤트** — 검색 입력 컴포넌트(M11/M14)와 디커플. listener 없으면 no-op
- **원칙 #6 (서버가 진실)** — RenameDialog는 빈 입력 외 client validation X. 충돌은 서버 RENAME_CONFLICT → setError로 다이얼로그 유지
- **에러 코드 계약 준수** — spec 작성 시 `NAME_CONFLICT`/`INVALID_NAME` 가정했으나 docs/02 §8에 이미 `RENAME_CONFLICT`/`VALIDATION_ERROR`가 동일 의미로 존재 → 코드를 docs에 정합 (원칙 #12)

### 다음 세션 컨텍스트
- 검색 입력 컴포넌트(M11) 마운트 시 `useEffect`에서 `window.addEventListener(FOCUS_SEARCH_EVENT, onFocus)` 등록 잊지 말 것 (`useGlobalShortcuts`가 이미 디스패치 중)
- ConfirmDialog 디자인 시스템 컴포넌트화는 M14 Visual Identity와 함께 검토 (현재 native confirm)
- 다중 선택 + F2 일괄 이름 변경(prefix 등)은 v1.x 범위
- 백엔드 연결 시 `api.renameFile`는 `PATCH /files/:id` 또는 `POST /folders/:id/rename`으로 교체
- BulkActionBar에 "이름 변경" 버튼 추가는 M14 Visual Identity와 함께 (단일 선택 시만 활성화)

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7, M10, M13 완료. M8 (권한 UI)는 docs/03 §3 작성 후 진입 가능. M11 검색은 M10의 `app:focus-search` 트리거 활용 가능.

---

## 2026-04-25 — M7 완료 (DnD 이동: dnd-kit + 다이얼로그 듀얼 경로)

### 완료
- [M7] **@dnd-kit/core 6.x 설치** — 이동 전용 (업로드 native DnD와 분리, 원칙 #7)
- [M7] **lib/folderTreeUtils.ts** — `findNode`/`containsNode`/`isSelfOrDescendantOfAny` 순수 유틸 (12 tests)
- [M7] **api.moveFiles mock** — MOCK_TREE/MOCK_FILES 상태 갱신, self/descendant/target 검증, 3 에러 코드 throw (5 tests)
- [M7] **에러 코드 추가 (docs/02 §8)** — `MOVE_INTO_SELF` / `MOVE_INTO_DESCENDANT` / `TARGET_NOT_FOUND` (원칙 #12)
- [M7] **stores/moveUi.ts** — Zustand 다이얼로그 슬라이스 (`isMoveDialogOpen`/`moveIds`/`moveSourceFolderId`) (3 tests)
- [M7] **components/dnd/types.ts** — `MoveDragData` 타입 + droppable id prefix
- [M7] **useDragPayload** — selection vs single-row 결정, containsFolderIds 캐시 조회 (4 tests)
- [M7] **useMoveBulk** — markPending → moveFiles → invalidate(source/target/folderTree/fileDetail) → unmarkPending (3 tests)
- [M7] **MoveDragOverlay** — `role="status" aria-live="polite"` 카운트 배지 (행 복제 X)
- [M7] **useFolderDroppable** — useDndContext로 active 읽음, isInvalid/isSameFolder/isOver/isDragging 플래그
- [M7] **DndProvider** — PointerSensor distance:5px (클릭 vs 드래그 구분), DragEnd 시 self/descendant/같은-폴더 no-op
- [M7] **explorer/layout.tsx** — DndProvider 마운트 (sidebar+main 모두 droppable 영역)
- [M7] **FolderTree / Breadcrumb / FileRow(폴더)** — drop 타겟 통합 + 시각화
- [M7] **FileRow draggable** — useDraggable + dragData 합성, hook 호출 순서 안정화 위해 비폴더 행도 droppable hook 호출 (`__not_a_target__`)
- [M7] **BulkActionBar 이동 버튼** — 스텁 제거, openMoveDialog(ids, folderId) 호출
- [M7] **MoveFolderDialog** — radiogroup 폴더 트리 picker, source/self/descendant disabled, Esc/Enter, role="dialog" aria-modal (5 tests)
- [M7] **ClientFilesPage 마운트** — UploadConflictDialog 옆에 MoveFolderDialog 마운트
- [M7] **린트 정리** — useDragPayload/useMoveBulk 테스트 wrapper에 displayName 부여
- [M7] **검증** — typecheck PASS · lint PASS · 108 tests PASS (M5 기준 76 → +32)
- [M7] **로드맵** — docs/01 §18 M7 행에 완료 마커(2026-04-25) + 핵심 DoD 추가

### 핵심 설계 결정
- **듀얼 진입**: 마우스(DnD) + 키보드(BulkActionBar 다이얼로그). 두 경로 모두 `useMoveBulk` mutation으로 수렴 (단일 책임)
- **DragOverlay = 카운트 배지** (`📎 N개 항목 이동 중`). 행 복제 X — 가상화/접근성 충돌 회피
- **자기/후손 차단 3중 방어**: ① useFolderDroppable.disabled (드롭 자체 불가) ② DndProvider.handleDragEnd 재검증 ③ api.moveFiles에서 throw
- **낙관적 업데이트 X** (원칙 #3): selection.markPending → mutation 완료 후 invalidate. 실패 시 unmarkPending만.
- **무효화 매트릭스 (원칙 #6)**: source/target `filesInFolder` (모든 sort/dir 변종, prefix 매치) + `folderTree` + 각 id의 `fileDetail`
- **`__not_a_target__` 트릭**: FileRow에서 폴더가 아닌 행도 useFolderDroppable을 호출해 React Hook 순서 안정화. 트리에 없는 id이므로 자동으로 드롭 비대상.

### 다음 세션 컨텍스트
- 백엔드 연결 시 api.moveFiles는 `POST /folders/:id/move` 또는 `POST /files/move-bulk`로 교체 (mock fakeXHR 패턴 유지)
- DragOverlay 카운트 배지의 i18n 처리는 v1.x 검색 마일스톤(M11)과 함께 처리
- Task 17 DnDProvider 통합 테스트는 jsdom DnD 한계로 스킵 — Playwright e2e에서 검증 (관련 항목은 M10 접근성/키보드 마일스톤에 함께)
- M8 권한 UI는 `usePermission()` 훅 이미 BulkActionBar에 사용 중 → 권한 매트릭스 (docs/03 §3) 작성 필요

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7 완료. M8(권한 UI)는 docs/03 §3 작성 후 진입 가능. M13(디자인 토큰) 별도 완료.

---

## 2026-04-25 — M13 완료 (Claude 디자인 시스템 토큰 적용)

### 완료
- [M13] `design-reference/` 번들 추가 (Claude 핸드오프: `IbizDrive.html` + `styles.css` 1318줄 + `README.md` + 4 jsx 참고용)
- [M13] `docs/design-system.md` 갱신 — §5에 M5 업로드 컴포넌트 매핑 섹션 추가, §10 Open Questions(다크 모드 토글, variant 범위, 폰트 로딩, 6열 확장)
- [M13] M5 업로드 컴포넌트 4종 className만 styles.css 치수에 맞춰 조정 (JSX/props/handlers/aria 변경 없음):
  - `UploadButton`: h-7 px-2.5 + border-accent (`.btn-primary` 매핑)
  - `UploadOverlay`: inset-2 + backdrop-blur-[2px] + accent 8% + rounded-lg (`.drop-overlay`)
  - `UploadQueueDock`: w-[340px] max-h-[420px] + border-border-strong + header bg-surface-2 + progress h-[2px] bg-surface-3
  - `UploadConflictDialog`: max-w-[460px] + 백드롭 rgba(0,0,0,.32)
- [M13] `.gitignore` 초기 생성 (`.tmp/`, `.claude/`, `node_modules/`, `.next/`, etc.)

### DoD
- ✅ typecheck / lint / test 76/76 통과
- ✅ SSR HTML 검증: `bg-bg`, `bg-surface-1`, `bg-accent`, `text-fg` 등 토큰 클래스 렌더링 확인
- ✅ CSS 번들 검증: `.bg-accent { background-color: var(--accent); }` 등 13종 토큰 유틸 정상 생성
- ✅ 코드베이스 전수 검사: `bg-white` / `bg-gray-*` / `text-gray-*` 0건
- ✅ 사용자 시각 확인 (스크린샷): 따뜻한 회색 배경, 인디고 accent 버튼, surface-1 사이드바 모두 토큰 값 적용 확인

### 원칙 체크
- ✅ 디자인 진실 출처: `design-reference/styles.css` → `globals.css` (이미 prior 세션 0315a04에서 적용) → `@theme inline`으로 Tailwind 유틸 노출
- ✅ "토큰만, 구조 변경 없음" 원칙 준수: JSX 트리, props, handlers, aria 속성 모두 그대로

### 사용자 결정 (2026-04-25 세션)
시각적 임팩트가 큰 추가 작업(TopBar / Lucide 아이콘 / FileRow 밀도 / StatusBar / SortChip / ViewSwitch / 6열 테이블 / RightPanel 탭)은 M13 범위에서 명시적으로 제외하고 후속 마일스톤으로 분리:
- **M14 Visual Identity** — TopBar + Lucide 아이콘 + FileRow 밀도 + StatusBar
- **M15 Layout Extras** — SortChip + ViewSwitch + StorageBar + RightPanel 탭
- **M16 Grid View** — FileTable grid 모드 (M14 ViewSwitch 의존)

→ `docs/01-frontend-design.md §18` 로드맵에 추가됨.

### 다음 세션 컨텍스트
**M14 진입 시 필요**
- 의존성 1개 추가 검토: `lucide-react` (아이콘) — 사용자 confirm 필요
- TopBar는 새 컴포넌트 (`src/components/layout/TopBar.tsx`) — `app/(explorer)/layout.tsx` grid를 `grid-rows-[48px_1fr]`로 재구성
- FileRow는 emoji → SVG 아이콘 매핑 테이블 도입 (mime → 아이콘)
- StatusBar는 사이드바 하단 또는 main 하단 고정 — 디자인 결정 필요
- 테스트 영향: FileRow 테스트가 emoji assertion을 쓰면 수정 필요 (현재 없음 확인 → 영향 0)

**브랜드 다크 모드 토글 UI**
- M13에서 토큰만 정의됨. 토글 UI는 M14 TopBar에 포함 검토.

### 블로커
- 없음

---

## 2026-04-25 — M5 완료 (업로드: multipart + 충돌 + 실패 분류)

### 완료
- [M5] `lib/uploadErrors.ts` 5종 분류 (network/permission/quota/server/conflict) + 단위 테스트 8개
- [M5] `lib/fakeXhr.ts` + 매직 파일명 6종 (normal/conflict.pdf/huge.bin/deny.txt/srv_500.any/net_fail.any) + 단위 테스트 7개
- [M5] `lib/api.ts#uploadFile` — FakeXHR 반환 (교체 경계: 실제 XHR로 교체 시 소비자 변경 없음)
- [M5] `stores/upload.ts` — queue/applyToAll/enqueue/updateTask/resolveConflict/retry/cancel/clearDone + 단위 테스트 8개
- [M5] `hooks/useUpload.ts` — store subscribe 기반 XHR orchestration + done시 `filesInFolder` invalidate + 단위 테스트 8개 (DoD o 포함)
- [M5] `hooks/useNativeFileDrop.ts` — `types.includes('Files')` 가드로 dnd-kit 분리 (원칙 #7), depth counter + 단위 테스트 4개
- [M5] `hooks/useUploadBeforeUnload.ts` — pending(`queued|uploading|conflict`) > 0 시 경고
- [M5] UI 5개: `UploadButton` / `FolderToolbar` / `UploadOverlay` / `UploadQueueDock` / `UploadConflictDialog`
  - UploadOverlay (2 tests), UploadQueueDock (4 tests), UploadConflictDialog (5 tests) — a11y (aria-modal, aria-labelledby/describedby, Esc=skip, Tab 포커스트랩, applyToAll)
- [M5] `FileTable` — `containerRef` + `useNativeFileDrop` + `UploadOverlay` 통합, early return을 body 변수로 리팩토링하여 Empty/Error/Forbidden 상태에도 drop 동작
- [M5] `ClientFilesPage` — `FolderToolbar` + `UploadQueueDock` + `UploadConflictDialog` + `useUploadBeforeUnload` 통합
- [M5] `FileTableEmpty` — `UploadButton` CTA 삽입 (재사용)
- [M5] `docs/01 §5.3` 구현 노트 추가 (paused/tusUrl/overwrite 제외 사유, pendingCount/enqueue/applyToAll/cancel 의미론)

### DoD
- ✅ typecheck / lint / test 전체 통과 (기존 30 + M5 46 = 76 passing)
- ⏳ 수동 검증 a~l, n — 사용자 브라우저 확인 대기 (pnpm dev → /files/root → 시나리오 12종)

### 원칙 체크
- ✅ #1 URL folderId canonical — `task.targetFolderId`는 `enqueue` 시점의 folderId 스냅샷 (Zustand는 "무엇을" 올릴지만 가짐)
- ✅ #3 낙관적 업데이트 비파괴적만 — 업로드 결과 낙관 append 금지, done 시 `invalidateQueries({ queryKey: [...qk.files(), 'list', folderId] })`로 prefix match
- ✅ #7 DnD 분리 — native는 `FileTable` 컨테이너만, `types.includes('Files')` 가드로 dnd-kit 이벤트 무시
- ✅ #12 에러 코드 — `lib/uploadErrors.ts`가 docs/02 §8의 status 코드 매핑 유지

### 다음 세션 컨텍스트
**M5.1 (tus 재개 업로드)**
- `UploadTask`에 `tusUrl`, `paused` 상태 도입
- `useUpload`의 transport만 교체 (store/UI 변경 없음)

**실제 백엔드 연결**
- `api.uploadFile` 내부를 실제 `XMLHttpRequest`로 교체 (인터페이스 동일)
- `FakeXHR` 파일 및 매직 파일명 테스트는 제거 또는 e2e로 이관

**M7 DnD 이동 + 드롭 타겟 확장**
- `useNativeFileDrop`을 `FolderTree` 노드에도 연결 (dnd-kit 이동과 공존)
- 동일 가드(`types.includes('Files')`)로 분리 유지

### 블로커
- 없음

---

## 2026-04-25 — 디자인 시스템 적용 (M5 업로드 구현 진입 전)

### 완료
- [DS] `docs/design-system.md` 신규 — 토큰 2-layer 전략 (base CSS vars + `@theme inline` 매핑), 컴포넌트별 클래스 매핑
- [DS] `frontend/src/app/globals.css` 재작성 — color/typography/radius/shadow/spacing 토큰, `[data-theme="dark"]` 다크 모드, focus-visible 전역 링, 스크롤바 스타일
- [DS] `(explorer)/layout.tsx` — 3-col 레이아웃 + 사이드바 브랜드 마크 (22px accent 사각형 + "IbizDrive" 14px semibold)
- [DS] `FolderTree.tsx` — active: `bg-accent-soft text-accent font-medium`, inactive: `hover:bg-surface-2 hover:text-fg`, 깊이 들여쓰기 유지
- [DS] `Breadcrumb.tsx` — 마지막 노드 `text-[15px] font-semibold text-fg`, 구분자 `›` `text-fg-subtle`
- [DS] `BulkActionBar.tsx` — `bg-accent-soft border-y` 바, `h-7 px-2.5 rounded` 버튼 패턴, 위험: `hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger`
- [DS] `FileTable.tsx` — `GRID_COLS` 상수 추출 후 FileRow에 prop 전달, 헤더 `h-[30px] bg-surface-1 text-[11px] uppercase tracking-[0.04em]`
- [DS] `FileRow.tsx` — `gridCols` prop 수신, 상태별 class 토큰화 (pending/selected/hover), focus-visible은 globals.css 전역 링이 담당
- [DS] `RightPanel.tsx` — `w-[360px] bg-surface-1 border-l`, 상세 그리드 `grid-cols-[80px_1fr] text-[12px]`, 에러 `text-danger`
- [DS] Empty/Error/Forbidden/Skeleton 4개 상태 컴포넌트 — `flex-1 flex flex-col items-center justify-center gap-3 py-[60px]` 공통, danger 변형은 `bg-[color-mix(in_oklch,var(--danger)_10%,transparent)]`
- [DS] `ClientFilesPage.tsx` — 2-pane 래퍼 `flex flex-1 min-h-0`, 메인에 `bg-bg`
- [DS] route-level states — `loading.tsx` / `error.tsx` / `not-found.tsx` 모두 center-layout 상태 패턴으로 통일

### 원칙 준수 체크
- ✅ 구조·로직 변경 없음 (className만 수정)
- ✅ aria 속성 전원 유지 (aria-rowcount/rowindex, role=grid/row/gridcell, role=toolbar, aria-live, role=alert)
- ✅ focus-visible 링 가시성 유지 (globals.css에서 전역 `:focus-visible` 스타일)
- ✅ §19 원칙 1~5 영향 없음 (URL 진실 출처, query-param RightPanel, pending 낙관, DnD 분리 원칙 모두 그대로)
- ✅ 새 의존성 추가 없음 (폰트/아이콘 패키지 생략, 시스템 폰트 + 이모지 유지)

### 토큰 설계 요약
- Base vars는 `:root`에 raw 값(`oklch`/`#hex`)으로 정의, `[data-theme="dark"]`가 오버라이드
- Tailwind 4의 `@theme inline`이 base vars를 util class로 노출: `--color-bg`, `--color-surface-1`, `--color-accent`, `--color-fg-muted` 등
- 결과: `bg-bg` / `bg-surface-1` / `text-fg-muted` / `text-accent` 같은 utility가 `var(--bg)`를 참조 → 다크 모드 전환 시 className 변경 0건

### DoD
- typecheck: 통과
- lint: 통과
- test: 30/30 통과 (기존 테스트 regressions 없음 — className 전용 변경이라 snapshot/DOM 쿼리 영향 없음)
- 브라우저 시각 검증: 대기 (사용자 확인 권장)

### 다음 세션 컨텍스트
**M5 (업로드) 구현 재개**
- 승인된 spec: `docs/superpowers/specs/2026-04-25-m5-upload-design.md`
- 다음 단계: writing-plans skill → implementation plan 작성 후 구현 진입
- 새 UI 요소(UploadButton, UploadToasts, ConflictDialog)는 이번 디자인 토큰 체계 위에 구축

**다크 모드 활성화**
- 현재 `[data-theme="dark"]` 셀렉터로 정의됨. 토글 UI는 M9(설정)로 보류
- 테스트: DevTools에서 `<html data-theme="dark">` 수동 설정 시 전환 확인 가능

### 블로커
- 없음

---

## 2026-04-25 — M6 완료 (RightPanel + useOpenFile + ?file= 자동 제거) ✅ 브라우저 검증 통과

### 완료
- [M6] `hooks/useOpenFile.ts` — §17.5 설계 그대로. `?file=` query param 진실 출처. open/close/fileId 반환, replace + scroll:false, 다른 param 보존
- [M6] `hooks/useFileDetail.ts` — `qk.fileDetail(id)` 캐시 키, enabled:Boolean(id), staleTime 30s
- [M6] `api.getFileDetail(id)` MOCK — 404 throw 포함
- [M6] `components/files/RightPanel.tsx` — role=complementary, 이름/유형/크기/수정일/수정자 dl, 로딩 스켈레톤, 에러 role=alert, 닫기 버튼, document keydown Esc 리스너 (fileId 있을 때만 등록)
- [M6] `ClientFilesPage` 2-pane 레이아웃: 좌측 Breadcrumb+BulkActionBar+FileTable, 우측 RightPanel (fileId 없으면 null 반환해 공간 미차지)
- [M6] `FileTable.handleOpen` 리팩터 — 인라인 URL 조작 제거, `useOpenFile().open()` 사용. 분기 확정: folder → router.push, file → openFile(id)
- [M6] `hooks/useCloseFileOnFolderChange.ts` — 폴더 변경 시 `?file=` 자동 제거. prevRef로 초기 마운트는 건너뜀 (딥링크 보존). M3 "folderId 변경 시 focus/selection reset"과 대칭
- [M6] test/setup.ts에 afterEach cleanup 추가 (testing-library v16 + globals:false 조합에서 자동 cleanup 미작동 → 문서 레벨 리스너/DOM 누적 버그 방지)

### 계약 파일 추가/수정
- frontend/src/hooks/useOpenFile.ts                          신규 (docs/01 §17.5)
- frontend/src/hooks/useFileDetail.ts                        신규 (docs/01 §6.1 키 사용)
- frontend/src/hooks/useCloseFileOnFolderChange.ts           신규 (M3 대칭 정책)
- frontend/src/hooks/useOpenFile.test.ts                     신규 (5 tests)
- frontend/src/hooks/useCloseFileOnFolderChange.test.ts      신규 (5 tests)
- frontend/src/components/files/RightPanel.tsx               신규 (docs/01 §11, §12.1)
- frontend/src/components/files/RightPanel.test.tsx          신규 (5 tests)
- frontend/src/lib/api.ts                                    수정 (getFileDetail 추가)
- frontend/src/components/files/FileTable.tsx                수정 (useOpenFile로 refactor)
- frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx 수정 (RightPanel 통합, 2-pane, useCloseFileOnFolderChange 호출)
- frontend/src/test/setup.ts                                 수정 (cleanup 추가)

### 미결 결정 사항 (이번 세션 확정)
1. **폴더 이동 시 `?file=` 자동 제거: YES** — 파일은 특정 폴더 컨텍스트이므로 폴더가 바뀌면 패널은 의미 없음. M3 focus/selection reset과 대칭. 딥링크는 prevRef=null 조건으로 보존
2. **단일 클릭 = 패널 열기: 유지 (현재 Enter/더블클릭만)** — 단일 클릭은 M4에서 "선택"으로 합의됨. Windows/Mac 파일 탐색기 표준 UX와 일치. 빠른 미리보기는 향후 M10 별도 설계

### 원칙 준수 체크
- ✅ §19 원칙 2 (RightPanel은 query param, parallel route 아님) — `?file=` 유지
- ✅ §19 원칙 1 (URL이 "어디"를 소유) — 파일 선택 상태도 URL query에 존재
- ✅ Esc 정책 (§12.1) — RightPanel 전역 리스너가 닫기 담당. FileTable grid의 Esc(선택 해제)는 독립. 둘 다 누르면 각자 동작 (상호 방해 없음)

### DoD
- typecheck: 통과
- lint: 통과
- test: 30/30 통과 (기존 15 + useOpenFile 5 + RightPanel 5 + useCloseFileOnFolderChange 5 = M6 15개 신규)
- 브라우저 수동 검증: 통과 (A~F 섹션 15 시나리오)

### 회고 — testing-library v16 + vitest globals:false cleanup 이슈
- 증상: RightPanel 테스트에서 "Found multiple elements with role button and name 패널 닫기"
- 원인: vitest.config의 `globals: false` → `@testing-library/react`의 자동 afterEach cleanup이 비활성. 테스트 간 DOM이 `document.body`에 누적되어 이전 테스트의 aside가 남아 있었음. 동시에 RightPanel의 `document.addEventListener('keydown')` 리스너도 누적됨
- 해결: `test/setup.ts`에서 `afterEach(cleanup)` 수동 등록. 앞으로 컴포넌트 테스트 추가 시 자동 적용됨
- 교훈: `globals: false`를 선호한다면 cleanup/matchers는 setup에 명시적으로 넣어야 함

### 회고 — 폴더 변경 시 ?file= 자동 제거 설계
- 자연 네비게이션(FolderTree/Breadcrumb Link 클릭, handleOpen router.push)은 이미 ?file=을 자동 탈락시킴. 따라서 `useCloseFileOnFolderChange`는 엣지 케이스(back/forward, 프로그래밍 navigation, 딥링크 뒤 이동)를 위한 안전망 역할
- 딥링크(`/files/foo?file=x` 초기 마운트) 보존을 위해 `prevRef.current === null`일 때는 건너뜀. 이후 이동부터 동작
- 훅 분리 이유: 단일 callsite이지만 ref+초기 스킵 로직이 비자명 → 단위 테스트로 회귀 방지 (CLAUDE.md의 "premature abstraction 금지" 원칙에 근접하나 테스트 가치로 상쇄)

### 다음 세션 컨텍스트
**M5 (백엔드 API 연결)**
- MOCK(api.ts) 전체 → 실제 fetch로 교체. 계약은 docs/02 §7
- `getFileDetail`은 현재 `FileItem` 그대로 반환. 백엔드 계약에서 권한·버전·공유자 등 포함하면 `FileDetail` 타입 분리 필요
- 에러 코드 (docs/02 §8) 매핑 후 RightPanel 에러 상태 메시지 세분화

**M7 (권한)**
- RightPanel에 권한 기반 액션 버튼 (다운로드/공유/이름변경) 추가 자리 있음
- usePermission 실제 훅 교체 후 확장

**M10 (고급 키보드)**
- 단일 클릭 = 빠른 미리보기 UX 재검토 지점 (이번 세션에서 유지 결정)
- Space로 패널 토글 등 탐색기 스타일 옵션 검토

**후속 개선 (우선순위 낮음)**
- ClientFilesPage의 canonical redirect가 현재 pathname만 비교, `router.replace(canonical)`에서 query를 전부 탈락시킴. `?file=` 및 `?sort=` 등이 의도치 않게 날아갈 가능성. 재현되면 canonical redirect에 query 보존 로직 추가

### 블로커
- 없음

---

## 2026-04-25 — M4 완료 (선택 모델 + BulkActionBar) ✅ 브라우저 검증 통과

### 완료
- [M4] selection store (stores/selection.ts) + Vitest 단위 테스트 12개
  - §5.1 스펙 + markPending이 selection에서 자동 제거 (상호배제)
  - selectRange 앵커 폴백 3케이스 (null / pending / 폴더 외)
- [M4] usePermission 스텁 훅 (§14.2 시그니처, M7 교체 예정)
- [M4] useDeleteBulk 훅 + 3케이스 단위 테스트 (성공 / 실패+같은폴더 / 실패+다른폴더)
- [M4] BulkActionBar (role=toolbar, aria-live=polite, count===0 숨김)
- [M4] FileRow: aria-selected 실제 연결, pending 시 opacity+스피너+aria-disabled, onClick(item, MouseEvent) 시그니처
- [M4] FileTable: aria-multiselectable 복원, 키보드 Space/Ctrl+A/Esc clear, ArrowUp/Down pending 스킵, markPending focus 보정 useEffect, 폴더 변경 시 clear
- [M4] Vitest + jsdom + @testing-library/react 테스트 인프라 세팅
- [M4] api.deleteBulk mock 추가
- [M4] eslint.config.mjs: .next/build ignore 추가 (lint 오경보 수정)
- [M4] BulkActionBar selector 무한 렌더 버그 수정 (a589032)
- [M4] next.config.ts allowedDevOrigins 추가 (127.0.0.1 dev 접근 허용)

### 계약 파일 추가/수정
- frontend/src/stores/selection.ts               신규 (docs/01 §5.1)
- frontend/src/hooks/usePermission.ts            신규 (docs/01 §14.2 스텁)
- frontend/src/hooks/useDeleteBulk.ts            신규 (설계안 §2.5)
- frontend/src/components/files/BulkActionBar.tsx 신규 (docs/01 §8.2)
- frontend/src/components/files/FileRow.tsx      수정 (Props 시그니처 변경)
- frontend/src/components/files/FileTable.tsx    수정 (selection 연결)
- frontend/src/lib/api.ts                        수정 (deleteBulk 추가)
- frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx  수정 (BulkActionBar 렌더)

### 설계 문서 업데이트
- docs/01 §5.1 하단에 구현 노트 (상호배제, 앵커 폴백) 추가
- docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md 신규 작성
- docs/superpowers/plans/2026-04-25-m4-selection-bulkactionbar.md 신규 작성

### DoD
- typecheck: 통과
- lint: 통과
- test: 15/15 통과 (selection 12 + useDeleteBulk 3)
- **브라우저 수동 검증: 12/12 시나리오 통과** (클릭/Ctrl+click/Shift+click/Space/Ctrl+A/Esc/폴더이동 clear/휴지통 pending→삭제→invalidate 포함)

### 버그 재발 방지 — Zustand v5 selector 무한 렌더
- **증상**: "Maximum update depth exceeded" (BulkActionBar 마운트 직후)
- **루트 원인**: `useSelectionStore((s) => Array.from(s.ids))` — selector가 매 snapshot 호출마다 **새 배열 참조** 반환
- **메커니즘**: Zustand v5는 `useSyncExternalStore` 기반. React가 매 render마다 이전/신규 snapshot 참조 비교 → 항상 "변경됨" 판정 → 무한 재렌더
- **올바른 패턴**: selector는 store에 **이미 존재하는 안정 참조**(Set/Map/객체 자체)만 반환. 파생 변환(Array.from, filter, map, spread)은 selector 밖 render 본문에서 수행
  ```tsx
  // ❌ 금지
  const ids = useSelectionStore((s) => Array.from(s.ids))
  // ✅ 권장
  const idsSet = useSelectionStore((s) => s.ids)
  const ids = Array.from(idsSet)
  ```
- **예외**: 변환이 꼭 필요하면 `useShallow` 또는 `zustand/shallow` equality 함수 명시. 하지만 대부분은 위 패턴이 충분하고 단순함

### 다음 세션 컨텍스트
**M5 (백엔드 API 연결)**
- MOCK(api.ts)을 실제 fetch로 교체. 계약은 docs/02 §7
- useDeleteBulk 실패 경로: 현재 console.warn → 토스트 라이브러리 통합 (sonner 후보)
- 에러 코드 (docs/02 §8) 매핑: 409 CONFLICT / 403 FORBIDDEN / 423 LOCKED 등 UI 메시지

**M6 (DnD 이동)**
- BulkActionBar 이동 버튼: 현재 스텁 → dnd-kit + 이동 다이얼로그
- 업로드 DnD(window native)와 컨텍스트 분리 원칙(§19) 유지
- pendingIds 재사용 (이동 중인 row는 pending UI)

**M7 (권한)**
- usePermission 스텁 → `useQuery + api.getEffectivePermissions(nodeId)` 로 교체 (docs/01 §14.2)
- BulkActionBar 버튼 표시 조건(can.download/move/delete) 실제 동작
- docs/03 §3 권한 매트릭스 확정 선행 필요 (현재 스켈레톤)

**M10 (고급 키보드)**
- Shift+↑↓ 범위 선택, Ctrl+↑↓ 포커스-only 이동, F2 rename, Delete 삭제, `/` 검색 포커스
- 설계 §12 참고, M4에서 anchor/pending 인프라 이미 확보됨

### 블로커
- 없음 (M5 진입 가능. docs/03 §3 권한 매트릭스는 M7 시작 전 확정 필요)

---

## 2026-04-25 — M3 브라우저 검증 완료

### 검증 결과
- /files/root — 5개 항목 정상 렌더링 (📁영업팀, 📁인사팀, 📄제안서, 📊예산안, 📝회의록)
- /files/folder_contracts — 계약서 2개 정상
- /files/folder_hr — empty state ("이 폴더는 비어 있습니다") 정상
- 키보드 ↑↓ Enter Esc 모두 정상 동작
- hydration 에러는 브라우저 확장(testim) 주입 문제, 코드 무관

### 다음 세션 컨텍스트 (M4: 선택 모델 + BulkActionBar)
- FileRow aria-selected 항상 false → M4에서 useSelectionStore 연결
- FileRow onClick → M4에서 selectOnly/toggle/range 로직 연결
- aria-multiselectable={true} → M4에서 grid에 다시 추가
- 컬럼 헤더 정렬 토글 UI 미구현 (sort/dir은 URL param으로 동작)
- F2/Delete/Ctrl+A/검색(/) 키보드는 M10으로 미룸
- 3000~4000 포트 대역은 다른 앱이 점유 중 → dev 서버는 4100+ 사용 권장

---

## 2026-04-24 — M3 구현

### 완료
- [M3] FileTable — TanStack Virtual 가상화 (overscan: 10, 10k+ 행 대응)
- [M3] 4가지 상태 컴포넌트: Skeleton, Empty, Error(onRetry), Forbidden(403)
- [M3] FileRow — 아이콘, 크기/날짜 포맷, roving tabIndex
- [M3] WAI-ARIA grid 패턴: role="grid/row/gridcell/columnheader", aria-rowcount/rowindex/selected
- [M3] 기본 키보드: ↑↓ 포커스 이동 + DOM focus 동기화, Enter 열기, Esc 해제
- [M3] useFilesInFolder 훅 (qk.filesInFolder 캐시 키)
- [M3] useSortParams 훅 (URL searchParams에서 sort/dir 읽기)
- [M3] ClientFilesPage에 FileTable 통합

### 계약 파일 추가/수정
- src/types/file.ts            (FileItem, SortKey 타입)
- src/lib/queryKeys.ts 수정     (files, filesInFolder, fileDetail 키 추가)
- src/lib/api.ts 수정           (MOCK_FILES + getFilesInFolder 추가)

### 코드 리뷰 후 수정
- role="grid"를 외부 컨테이너로 이동 (header row가 grid 안에 포함되도록)
- aria-multiselectable 제거 (M4 선택 기능 전까지 premature)
- folderId 변경 시 focusedIndex 리셋 useEffect 추가
- 포커스 시 DOM .focus() 호출 추가 (스크린 리더 대응)
- role="gridcell", role="columnheader" 추가

### 다음 세션 컨텍스트 (M4: 선택 모델 + BulkActionBar)
- FileRow의 aria-selected는 현재 항상 false. M4에서 useSelectionStore 연결 필요
- FileRow onClick prop은 현재 focusedIndex만 설정. M4에서 selectOnly/toggle/range 연결
- aria-multiselectable={true}는 M4에서 다시 추가
- 컬럼 헤더 정렬 토글 UI 미구현 (sort/dir은 URL param으로 동작)
- api.ts mock 데이터: folder_sales/folder_hr가 MOCK_TREE와 MOCK_FILES에 이중 존재 — 실제 API 계약 확정 시 정리 필요
- F2/Delete/Ctrl+A/검색(/) 키보드는 M10으로 미룸

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (docs/01 §4, §6, §11, §12 스펙 그대로 반영)

---

## 2026-04-24 — M1 완료

### 완료
- [M1] folderId 중심 catch-all 라우팅 (`/files/[...parts]`)
- [M1] FolderTree / Breadcrumb URL 동기화
- [M1] canonical redirect (decodeURI 비교, 한글 URL 대응)
- [M1] 프로젝트 기본 셋업 (Providers, 훅, 스토어)
- [M1] `/files` → `/files/root` 리다이렉트
- [M1] Explorer 레이아웃 (사이드바 + 메인)
- [M1] loading / error / not-found 상태 페이지

### 계약 파일 추가
- src/lib/normalize.ts      (docs/02 §3)
- src/lib/queryKeys.ts       (docs/01 §6.1)
- src/lib/folderPath.ts      (docs/01 §17.3)
- src/lib/api.ts             (MOCK — M5에서 실제 API로 교체)

### 다음 세션 컨텍스트 (M2: FolderTree 심화 + TrashLink + QuickAccess 또는 M3: FileTable)
- api.ts는 현재 mock. 백엔드 나오면 실제 fetch로 교체. 계약은 docs/02 §7.3
- 서버 컴포넌트 전환은 M3에서 (notFound/redirect 조합)
- canonical redirect는 클라이언트에서 useEffect. 깜빡임 있으면 M3에서 서버 redirect로
- Next.js 16에서 Windows pnpm EPERM 이슈 발생 → 15.3.2로 다운그레이드, npm 사용
- next-env.d.ts의 `.next/types/routes.d.ts` import는 Next.js 16 전용 → 15에서는 무시됨

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (코드 템플릿 그대로 반영)

---

## 2026-04-26 — A1.0 완료 (User schema + JPA) + 드리프트 정리

### 완료
- [드리프트 정리] ADR #12 Spring Session(Redis) → JDBC 정정 4곳 (docs/00 §1.3/§4.4/§5 + docs/02 §7.1) — Redis는 MVP 인프라 제외, JDBC 단일 백엔드로 통일
- [드리프트 정리] 권한 enum 9종 (READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/PURGE) docs/00 §3.2 ↔ docs/03 §3.1 동기화
- [드리프트 정리] users 테이블 컬럼 정렬: V1 stub의 display_name 유지 + docs/02 §2.1 동일 컬럼명으로 정렬 + role 대문자(MEMBER/AUDITOR/ADMIN)
- [신규 ADR] #18~#22 추가: MVP 인증 범위 / BCrypt 정책 / 세션 만료·잠금 / 관리자 초대 only / `/me` 응답 최소화
- [docs/02 §7.4] /api/auth/login·logout·me·csrf endpoint 상세 (CSRF 헤더, side-effects, 423 ACCOUNT_LOCKED 등)
- [docs/02 §8] 423 LOCKED → ACCOUNT_LOCKED + FILE_LOCKED 분리
- [docs/03 §2] TBD 스캐폴딩 → 본 스펙 (시퀀스 4종, §2.6 만료·잠금, §2.7 BCrypt 정책, §2.8 관리자 초대, §2.10 audit 매트릭스)
- [A1.0] V2 마이그레이션: users 5컬럼 추가 (role + is_active + last_login_at + locked_at + must_change_password)
- [A1.0] User @Entity + Role enum + UserRepository (findActiveByEmail, lowercase 정규화는 caller 책임)
- [A1.0] UserRepositoryTest (Testcontainers Postgres 15-alpine, `disabledWithoutDocker=true`로 로컬 dev pass)
- gradle 8.14.4 → 8.10 정렬 (#4 wrapper 후속)

### 다음 세션 컨텍스트
- A1.1 (PasswordEncoder + UserDetailsService) 진입 — `BCryptPasswordEncoder(12)` 래핑한 `DelegatingPasswordEncoder` + User→UserDetails 어댑터
- A1 잔여: A1.1 → A1.2 (SecurityConfig: CSRF double-submit + 세션 필터 + /api/auth/csrf) → A1.3 (LoginController + lockout) → A1.4 (Logout + /me)
- PR 분기점은 A1.2 종료 시점 권장 (Security 인프라 단위)

### 블로커
- A1.3 진입 전 해결 필요: lockout 카운터 backing store 결정 (docs/03 §2.3 footnote). ADR #12에서 Redis MVP 제외 → 후보 (a) in-memory `ConcurrentHashMap` (b) DB `login_failures` 테이블 (c) v1.x Redis ADR 선결정. A1.1 작업 중 ADR로 결정.

### 설계 문서 업데이트 필요
- docs/03 §2.3/§2.6 — lockout 카운터 Redis 표기를 결정된 backing 표기로 정정 (A1.1 ADR 후)
- docs/03 §4.1 audit enum — `user.login.success/failed/logout/session.expired/locked/unlocked` 추가 (A1.3 진입 시)
