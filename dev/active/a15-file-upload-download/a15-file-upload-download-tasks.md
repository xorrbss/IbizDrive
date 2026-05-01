---
Last Updated: 2026-05-01
---

# A15 Tasks — Storage 모듈 + 파일 업로드/다운로드 endpoint

## Phase별 상태

| Phase | Status |
|---|---|
| A15.0 bootstrap | ✅ done |
| A15.1 StorageClient + LocalFs | ✅ done |
| A15.2 FileUploadService RED | ✅ done |
| A15.3 FileUploadService GREEN | ✅ done |
| A15.4 POST /api/files controller | ✅ done |
| A15.5 GET /api/files/:id/download controller | ✅ done |
| A15.6 Frontend api.uploadFile 실 XHR | 🟡 next |
| A15.7 closure (docs sync + PR + archive) | ⬜ pending |

---

## ✅ A15.0 — bootstrap (in progress)

- [x] 워크트리 생성 (`.claude/worktrees/a15-file-upload-download`, 브랜치 `feature/a15-file-upload-download`)
- [x] dev-docs 3파일 초안 작성 (`plan` / `context` / `tasks`)
- [ ] bootstrap commit

---

## ✅ A15.1 — StorageClient + LocalFs (DONE)

**Commit**: TBD (this phase commit pending below).
**산출물**:
- `backend/src/main/java/com/ibizdrive/storage/StorageClient.java` (interface)
- `backend/src/main/java/com/ibizdrive/storage/StorageProperties.java` (record `ibizdrive.storage.*`)
- `backend/src/main/java/com/ibizdrive/storage/LocalFsStorageClient.java` (impl, atomic rename, traversal guard)
- `backend/src/main/java/com/ibizdrive/storage/StorageConfig.java` (`@EnableConfigurationProperties`)
- `backend/src/main/resources/application.yml` (`ibizdrive.storage.{type, local.root}` 추가)
- `backend/src/test/java/com/ibizdrive/storage/LocalFsStorageClientTest.java` (9 cases GREEN)

**검증**: `./gradlew test --tests "com.ibizdrive.storage.*"` 9/9 GREEN. full suite 회귀 0.

**핵심 결정**:
- 네임스페이스 = `ibizdrive.storage.*` (infra), `app.*`은 cron/business 보존.
- `matchIfMissing=true` → 기본 활성. type=s3 시 v1.x.
- atomic move (`StandardCopyOption.ATOMIC_MOVE` + `REPLACE_EXISTING`) — 동일 key race + 부분 쓰기 차단.
- traversal 방어 (resolve+normalize startsWith root).
- size 불일치 시 IOException + tmp 정리.
- delete idempotent (`Files.deleteIfExists`).
- `s3` block은 application.yml에 잔존하지만 v1.x까지 unused (`StorageProperties`는 type+local만 노출).

---

## ⬜ A15.1 — StorageClient + LocalFs (HISTORY ANCHOR — 위 done 블록 참조)

### 작업 전 필독
- `docs/02 §5` 저장소 정책 (객체 키 구조, RFC 5987 다운로드 헤더, MVP는 checksum/scan v1.x 분리).
- ADR #5 (storage_key UUID, 한글/특수문자 객체 키 미포함).
- 기존 properties 패턴: `backend/src/main/java/com/ibizdrive/share/ShareExpirationProperties.java` 또는 `PermissionExpirationProperties.java` (record + `@ConfigurationProperties`).

### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/share/ShareExpirationProperties.java` — properties record 패턴.
- `backend/src/main/resources/application.yml` — 기존 `app.share.expiration.*` 블록 위치.

### 구현 대상
1. `backend/src/main/java/com/ibizdrive/storage/StorageClient.java` — interface
   - `void write(String key, InputStream src, long sizeBytes, String contentType) throws IOException`
   - `InputStream read(String key) throws IOException`
   - `void delete(String key) throws IOException`
   - `boolean exists(String key)`
   - 객체 키 형식 = `{YYYY}/{MM}/{UUID}` (caller 책임)
2. `backend/src/main/java/com/ibizdrive/storage/StorageProperties.java` — `@ConfigurationProperties("app.storage")` record `(String type, String root)`. type=`local` only MVP.
3. `backend/src/main/java/com/ibizdrive/storage/LocalFsStorageClient.java` — `@Component @ConditionalOnProperty("app.storage.type"=local)`. root 디렉터리 자동 생성. write는 임시 파일에 stream 후 atomic rename. read는 `Files.newInputStream`. exists는 `Files.exists`.
4. `backend/src/main/resources/application.yml` — `app.storage: { type: local, root: ./uploads }` 추가.
5. `backend/src/test/java/com/ibizdrive/storage/LocalFsStorageClientTest.java` — RED 5+ 케이스
   - write 성공 시 파일 존재
   - read returns 동일 바이트
   - delete 후 exists=false
   - 동일 key 재 write → overwrite (또는 rejection — MVP 결정 필요. 현재 plan: overwrite 허용 = caller가 storage_key UUID 보장으로 충돌 불가)
   - root 자동 생성

### 검증 참조
- `./gradlew :backend:test --tests "com.ibizdrive.storage.LocalFsStorageClientTest"` GREEN.
- `./gradlew :backend:test` 전체 GREEN (회귀 0).

### 문서 반영
- 본 phase에서 docs 변경 0 (closure 시점에 일괄).

---

## ✅ A15.2 — FileUploadService RED (시그니처 + 단위 테스트) — DONE

**Commit**: TBD (this phase commit pending below).
**산출물**:
- `backend/src/main/java/com/ibizdrive/file/UploadResolution.java` (enum NEW_VERSION/RENAME)
- `backend/src/main/java/com/ibizdrive/file/UploadResult.java` (record file/version/newFile)
- `backend/src/main/java/com/ibizdrive/file/FileUploadService.java` (skeleton — `upload(...)` throws UnsupportedOperationException)
- `backend/src/test/java/com/ibizdrive/file/FileUploadServiceTest.java` (Testcontainers, 7 cases — Docker 미가용 시 SKIPPED)

**테스트 커버리지(7 cases)**: happy(new file) / folder-missing / folder-soft-deleted / conflict-null / conflict-NEW_VERSION / conflict-RENAME / blank-filename.

**deviation from plan**:
- `FileUploadedEvent.java` 미생성 — file/ 패키지 기존 emitAudit 직접 호출 convention 답습 (FileMutationService:298-315). KISS+§3 — 동일 패키지 내 패턴 혼재 회피.
- 테스트는 Mockito-only가 아닌 Testcontainers + AuditService/StorageClient mock (FileMutationServiceTest 패턴 답습) — folder lock + DB unique index 가드의 의미를 RED 시점부터 잠근다.

### 작업 전 필독 (참고용 — RED 완료)

### 작업 전 필독
- `docs/02 §6.1` 업로드 트랜잭션 의사코드 (folder lock → conflict 검사 → INSERT files → INSERT file_versions → UPDATE current_version_id → audit).
- `docs/02 §3` 정규화 함수 (`NormalizeUtil.normalizeForDedup` — V5 unique 컬럼 정합).
- `FileMutationService.rename` (이중 가드 패턴), `FolderMutationService.create` (생성 패턴).
- ADR #24 (audit listener), ADR #29 (file_versions, A5 closure).

### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` (rename/move/delete + 이중 가드 + audit emit 패턴).
- `backend/src/main/java/com/ibizdrive/file/FileRepository.java` — `existsActiveByFolderAndNormalizedName(...)` 등 이미 존재.
- `backend/src/main/java/com/ibizdrive/file/FileVersion.java` + `FileVersionRepository.java`.
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` (folder lock 메서드).

### 구현 대상
1. `backend/src/main/java/com/ibizdrive/file/FileUploadService.java` — class skeleton, 메서드만 throws-not-implemented:
   ```java
   public UploadResult upload(UUID folderId, UUID actorId, String filename, String contentType,
                              long sizeBytes, InputStream content,
                              UploadResolution resolution /* NEW_VERSION | RENAME | null */)
   ```
   `UploadResult` = record `(FileItem file, FileVersion version)`. `UploadResolution` = enum.
2. `backend/src/main/java/com/ibizdrive/file/FileUploadedEvent.java` — record (fileId, folderId, actorId, versionId, sizeBytes, mimeType).
3. `backend/src/test/java/com/ibizdrive/file/FileUploadServiceTest.java` — RED 케이스 (Mockito-only):
   - happy path 신규 업로드 (folder active, no conflict) → INSERT files + file_versions, current_version_id 갱신, storage write 호출, FileUploadedEvent publish
   - folder not found → `FolderNotFoundException`
   - folder soft-deleted → `FolderNotFoundException`
   - conflict + resolution null → `FileNameConflictException` (storage write 호출 0)
   - conflict + resolution=NEW_VERSION → file_versions row append, files row 동일 id, current_version_id 갱신
   - conflict + resolution=RENAME → 새 files row, name=`{base} (1).{ext}`
   - normalize: 입력 filename `안녕.txt` → `normalized_name=안녕.txt` (NFC + lowercase + trim, NormalizeUtil 호출 검증)

### 검증 참조
- `./gradlew :backend:test --tests "com.ibizdrive.file.FileUploadServiceTest"` 전부 RED (서비스 미구현이므로 expected).
- 기존 `FileMutationServiceTest` 등 회귀 0 (테스트만 추가).

### 문서 반영
- 없음 (GREEN 후 closure 시점에 일괄).

---

## ⬜ A15.3 — FileUploadService GREEN

### 작업 전 필독
- A15.2 RED 테스트 케이스.
- ADR #24 (audit listener — service에서 emit 호출 X, ApplicationEventPublisher 또는 AOP).

### 원본 코드 참조
- A15.2와 동일.

### 구현 대상
1. `FileUploadService.upload(...)` 구현:
   - `@Transactional` (REQUIRED).
   - `folderRepository.lockActiveById(folderId)` (필요시 메서드 추가).
   - normalized = `NormalizeUtil.normalizeForDedup(filename)`.
   - extension allowlist check (`docs/02 §5.4` 화이트리스트 = 도메인별 결정 → MVP는 deny list만: `exe`/`bat`/`cmd`/`com`/`scr`/`msi`/`dll`/`sh`/`ps1`/`vbs`/`js` 차단). 415 `UNSUPPORTED_MEDIA_TYPE`.
   - existing = `fileRepository.findActiveByFolderAndNormalizedName(folderId, normalized)`.
   - if existing == null: 신규 INSERT path (`FileItem`+`FileVersion` v=1, current_version_id 설정).
   - if existing != null && resolution == NEW_VERSION: append version (max_version_number+1), `current_version_id = new`, `files.size_bytes = new` etc.
   - if existing != null && resolution == RENAME: `name = uniqueRename(folderId, base, ext)` (limit 100 시도 후 fallback 409).
   - if existing != null && resolution == null: throw `FileNameConflictException`.
   - storage_key = `UUID.randomUUID()`.
   - `storageClient.write(storageKeyToObjectKey(storageKey), content, sizeBytes, contentType)`.
   - DataIntegrityViolationException → `FileNameConflictException` (이중 가드).
   - `applicationEventPublisher.publishEvent(new FileUploadedEvent(...))`.
2. `FileAuditListener.onFileUploaded(@TransactionalEventListener)` — `AuditService.record(FILE_UPLOADED, ...)`.
3. `objectKey(UUID storageKey)` helper — `{YYYY}/{MM}/{UUID}` 형식 (createdAt 기준).
4. `FolderRepository.lockActiveById(UUID)` — pessimistic write, 추가 필요시 작성.

### 검증 참조
- A15.2 케이스 모두 GREEN.
- `./gradlew :backend:test` 전체 GREEN (회귀 0).
- `audit_log`에 `file.uploaded` row 발행 검증 (Testcontainers integration test 1 케이스).

### 문서 반영
- 없음 (closure 시점).

---

## ⬜ A15.4 — POST /api/files controller

### 작업 전 필독
- `docs/02 §6.1`, `§7.6`, `§8` (에러 코드).
- 기존 controller 패턴: `FileController` (rename/move/delete), `FolderController` (create).

### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/file/FileController.java`.
- `backend/src/main/java/com/ibizdrive/file/dto/` (FileDto, FileVersionDto).
- `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` (409 / 403 / 415 매핑).

### 구현 대상
1. `FileUploadController` (또는 `FileController`에 메서드 추가):
   - `POST /api/files` `consumes = MULTIPART_FORM_DATA`.
   - 요청: `@RequestParam UUID folderId`, `@RequestParam(required=false) UploadResolution resolution`, `@RequestParam(required=false) String newName`(rename 시 사용자 명시 옵션 — 본 트랙 deferred? or 자동 명명만), `@RequestPart MultipartFile file`.
   - Guard: `@PreAuthorize("hasPermission(#folderId, 'folder', 'UPLOAD')")`.
   - 호출: `fileUploadService.upload(...)`.
   - 응답: `201` `{ file: FileDto, version: FileVersionDto }`.
2. `MultipartFile` → InputStream + sizeBytes + originalFilename + contentType 매핑.
3. 에러 코드 매핑 (GlobalExceptionHandler):
   - `FileNameConflictException` → 409 `UPLOAD_CONFLICT` (rename일 경우 `UPLOAD_CONFLICT_RENAME_FAILED`).
   - `UnsupportedMediaTypeException` → 415 `UNSUPPORTED_MEDIA_TYPE`.
4. `application.yml` — `spring.servlet.multipart.{max-file-size, max-request-size}` 명시 (MVP 2GB).
5. `FileUploadControllerTest` — MockMvc 스타일 (또는 WebMvcTest):
   - happy path 201
   - missing folderId → 400
   - UPLOAD 권한 부재 → 403
   - 동일 이름 + resolution null → 409 `UPLOAD_CONFLICT`
   - blocked extension → 415

### 검증 참조
- `./gradlew :backend:test` GREEN.

### 문서 반영
- 없음 (closure).

---

## ✅ A15.5 — GET /api/files/:id/download

### 작업 전 필독
- `docs/02 §5.2` RFC 5987 헤더, `§7.6` download row.
- `docs/03 §3` 권한 매트릭스 (DOWNLOAD vs READ 정합 — 결정 필요).
- ADR #24 audit listener.

### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/file/FileController.java` (GET /api/files/:id 패턴).

### 구현 대상
1. `FileController.download` (또는 `FileDownloadController`):
   - `GET /api/files/:id/download`.
   - Guard: `@PreAuthorize("hasPermission(#id, 'file', 'READ')")` (MVP 결정 — 매트릭스 재확인 후 'DOWNLOAD'로 교체 가능).
   - 조회: `fileRepository.findActiveById(id)` → 404 if missing.
   - `fileVersion = fileVersionRepository.findById(file.currentVersionId)`.
   - `storageClient.read(objectKey(fileVersion.storageKey, fileVersion.uploadedAt))` → InputStream.
   - 응답:
     - `Content-Disposition: attachment; filename*=UTF-8''<percent-encoded(file.name)>`
     - `Content-Type: file.mimeType` (default `application/octet-stream`)
     - `Content-Length: fileVersion.sizeBytes`
     - `ETag: "<file_version.checksum_sha256>"` (MVP는 checksum 미계산 — null이면 헤더 생략)
     - body = InputStream → `StreamingResponseBody` 또는 `InputStreamResource`
   - audit: `FileDownloadedEvent` publish → listener `FILE_DOWNLOADED` emit.
2. `FileDownloadedEvent` record + listener 핸들러.
3. `FileDownloadControllerTest` — MockMvc:
   - happy path 200 + headers + body bytes
   - file not found → 404
   - file soft-deleted → 404
   - 권한 미보유 → 403

### 검증 참조
- `./gradlew :backend:test` GREEN.
- 다운로드 audit row 검증.

### 문서 반영
- 없음 (closure).

---

## ⬜ A15.6 — Frontend api.uploadFile 실 XHR

### 작업 전 필독
- `frontend/src/lib/api.ts:307-` (현재 FakeXHR 분기).
- `frontend/src/lib/fakeXhr.ts` — 인터페이스 (`addEventListener('progress'|'load'|'error'|'abort'|'upload-conflict'|'http-error', ...)`, `abort()`, `simulateConflict?` 등).
- `frontend/src/stores/upload.ts`, `frontend/src/components/files/UploadDock.tsx`, `frontend/src/components/files/ConflictDialog.tsx` — 인터페이스 변경 0 검증.
- 기존 useUpload 테스트 / UploadStore 테스트.

### 원본 코드 참조
- `frontend/src/lib/api.ts:307-` (M5 진입점).
- `frontend/src/lib/fakeXhr.ts` (인터페이스 1:1 매칭 대상).

### 구현 대상
1. `frontend/src/lib/api.ts` `uploadFile`:
   - 분기 교체: `XMLHttpRequest` 직접 사용.
   - `FormData` append: `file`, `folderId`, `resolution?`, `newName?`.
   - `xhr.upload.addEventListener('progress', ...)`.
   - `xhr.addEventListener('load', ...)` → status 201 → 'load' 발행 / 409 → 'upload-conflict' 발행 / 기타 → 'http-error'.
   - `xhr.addEventListener('error', ...)` / `'abort'`.
   - 반환: 동일 인터페이스의 wrapper 객체 (`addEventListener` + `abort`).
   - `credentials: 'include'` (쿠키 세션 ADR #12).
2. `frontend/src/lib/fakeXhr.ts` 처리 결정:
   - Option A: 삭제 (M5 메모리 패턴 위반? — backend 존재 시 실 XHR 우선).
   - Option B: 유지 (test 전용). UploadStore 테스트가 fakeXhr 의존하면 유지 필요.
   - 결정: A15.6 진입 시 grep으로 import 위치 확인 후 결정.
3. 인터페이스 1:1 보존 검증 — 기존 `UploadStore`/`UploadDock`/`ConflictDialog` 테스트 0 변경 GREEN.
4. 신규 단위 테스트 `frontend/src/lib/api.upload.test.ts` — XHR 모킹 (`vi.spyOn(window, 'XMLHttpRequest')` 또는 `MockXMLHttpRequest`):
   - 201 → 'load' 이벤트
   - 409 + body `{code: 'UPLOAD_CONFLICT', existingFileId}` → 'upload-conflict' 이벤트
   - 500 → 'http-error'
   - abort() 호출 시 'abort'

### 검증 참조
- `pnpm test --run` GREEN (전체).
- `pnpm typecheck` GREEN.
- `pnpm lint` GREEN.
- `pnpm build` GREEN.

### 문서 반영
- 없음 (closure).

---

## ⬜ A15.7 — closure (docs sync + PR + archive)

### 작업 전 필독
- 모든 phase GREEN 확인.
- 모든 phase의 closure 영향 파일 list.

### 구현 대상
1. **docs sync**:
   - `docs/00-overview.md §5 ADR #13` — Status: Superseded by ADR #36 (가칭, A15 마커). 본문 추가: "MVP는 단일-POST multipart로 회귀 (ADR #36), tus 재이월 v1.x".
   - `docs/00-overview.md §5` — 신규 ADR #36: "A15 = StorageClient abstraction(LocalFs MVP) + 단일-POST multipart `/api/files` + GET `/api/files/:id/download` + 권한 UPLOAD/READ + audit FILE_UPLOADED/FILE_DOWNLOADED + frontend api.uploadFile 실 XHR. tus + S3 + checksum + scan + quota = v1.x"
   - `docs/02 §6.1` — 트랜잭션 의사코드는 그대로, 구현 메모 추가 (storage write 위치 = INSERT 사이).
   - `docs/02 §7.6` — 본문 download Guard `READ`로 정정 (또는 그대로 'DOWNLOAD' 유지하고 evaluator가 READ 통합 평가).
   - `docs/02 §7.7` — 본문 추가: MVP 단일-POST multipart spec (POST /api/files request/response 스키마). tus 본문은 v1.x deferred 마커.
2. **progress.md** entry 최상단 (회고 + 핵심 결정).
3. **PR 작성**: title `feat(a15): file upload + download endpoint MVP — storage abstraction + multipart + audit`. body = 회고 + commit list + 검증 결과.
4. **dev-docs archive**: `dev/active/a15-file-upload-download/` → `dev/completed/a15-file-upload-download/`.
5. **closure commit**: `chore(a15): closure — A15 트랙 종료 + dev-docs archive`.

### Destructive 게이트 (사용자 승인 필수)
- `gh pr merge --squash --delete-branch` — 사용자 승인 후 실행.
- `git push origin --delete feature/a15-file-upload-download` (실패 시) — 사용자 승인 후 실행.
- `git worktree remove .claude/worktrees/a15-file-upload-download` — 사용자 승인 후 실행.

### 검증 참조
- 최종 backend `./gradlew test` GREEN.
- 최종 frontend `pnpm test --run` + typecheck + lint + build GREEN.
- CI green 확인 후 머지 게이트.

### 문서 반영
- 본 phase가 곧 docs 반영.
