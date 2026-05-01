---
Last Updated: 2026-05-01
---

# A15 — Storage 모듈 + 파일 업로드/다운로드 endpoint (MVP enabling track)

## 요약

MVP 핵심 기능 중 backend에 부재한 **파일 업로드 + 다운로드 파이프라인**을 도입한다. 현재 frontend M5 업로드 UI는 `FakeXHR`로 동작 중 (실저장 안 됨), backend `FileMutationService`에는 `create()` 부재, `S3StorageClient` 추상화 0개. 본 트랙은 (a) storage abstraction (LocalFs MVP), (b) `POST /api/files` (multipart, 단일 POST), (c) `GET /api/files/:id/download` (stream + Content-Disposition), (d) frontend `api.uploadFile` FakeXHR → 실 XHR 교체로 MVP gap을 닫는다.

## 현재 상태 분석

### 이미 갖춰진 것
- **DB schema (V5)**: `files` + `file_versions` + DEFERRABLE FK (`current_version_id`) — 업로드 commit 트랜잭션 호환 (`docs/02 §6.1`).
- **`file_versions.storage_key UUID UNIQUE`** + `idx_versions_scan_pending` — 객체 키 정책 (ADR #5) 인프라.
- **Audit enum**: `FILE_UPLOADED("file.uploaded")`, `FILE_DOWNLOADED("file.downloaded")` — listener만 등재 필요.
- **Permission**: `UPLOAD`/`DOWNLOAD` enum, `@PreAuthorize hasPermission(...)` 인프라 (A4).
- **Conflict 가드 패턴**: `FileMutationService.rename` 이중 가드 (사전 native + UNIQUE violation → `FileNameConflictException`) — upload create에 그대로 재사용.
- **Frontend M5**: `api.uploadFile(...)` 인터페이스 = `FakeXHR` 반환 (`progress`/`load`/`error`/`abort`/`upload-conflict`/`http-error` 이벤트). UploadStore + ConflictDialog + Queue Dock.
- **Frontend types**: `FileItem` 응답 처리, `qk.files()` invalidate.
- **A5 closure**: `FileVersion` entity + `FileVersionRepository` + `existsByStorageKey(UUID)`.

### 부재한 것 (본 트랙 scope)
- **`StorageClient` interface** + `LocalFsStorageClient` impl (MVP) — `app.storage.{type, root}` properties. `S3StorageClient` skeleton은 본 트랙 out (v1.x).
- **`FileUploadService`** — 트랜잭션 + storage write + files INSERT + file_versions INSERT + `current_version_id` UPDATE + audit.
- **`POST /api/files`** controller (multipart, `MultipartFile` + `folderId` + `resolution?` + `newName?`) → 201 `{ file: FileDto, version: FileVersionDto }`.
- **`GET /api/files/:id/download`** controller — `READ` 가드 (정정: docs는 `DOWNLOAD`이지만 enum에 `DOWNLOAD`가 있으나 frontend 게이트는 `READ`로 단순화 가정 검토) + storage read stream + `Content-Disposition: attachment; filename*=UTF-8''…` (RFC 5987) + `Content-Type` + `Content-Length` + ETag(checksum) + audit emit.
- **Audit listener** — `@AfterReturning` AOP 또는 service 명시 emit (기존 패턴 그대로).
- **Frontend `api.uploadFile`** — FakeXHR 분기 제거, 실 `XMLHttpRequest` 교체. progress/error 이벤트 인터페이스 보존.

## 목표 상태

### 범위 안 (MVP)
1. `POST /api/files` (multipart) 단일 POST. resolution=`new_version`(기존 파일에 새 버전) | `rename`(자동 이름 변경) | unset(409 UPLOAD_CONFLICT).
2. `GET /api/files/:id/download` stream + headers + audit.
3. `LocalFsStorageClient` (`app.storage.type=local`, `app.storage.root=./uploads`) — dev/test/MVP single-instance용. 객체 키 = `{YYYY}/{MM}/{storage_key UUID}` 디렉터리 구조 (ADR #5 정합).
4. `StorageClient` interface 정의 — `write(key, InputStream, sizeBytes, contentType): void`, `read(key): InputStream`, `delete(key): void`, `exists(key): boolean`. S3 구현은 v1.x ADR로 분리.
5. Frontend `api.uploadFile` 실 XHR — M5 인터페이스 (progress/load/error/abort/upload-conflict/http-error 이벤트) 1:1 보존. `FakeXHR` 모듈 자체는 삭제 (M5 transport 메모리 정책 - mock transport 선호와 충돌하나 backend 도입 시점 = 교체 시점).
6. **ADR #13 (tus) → 부분 supersede**: MVP는 단일-POST multipart 채택, tus는 v1.x로 재이월. 이유: M5 frontend가 단일-POST progress 인터페이스, tus chunked 프로토콜 미사용. KISS+YAGNI.

### 범위 밖 (deferred)
- `POST /api/files/:id/versions` (별도 endpoint, ADR #29 — A6+ 이월 유지). 본 트랙은 `POST /api/files` 의 `resolution=new_version` 분기만 활성화. `expectedCurrentVersionId` 등 optimistic concurrency는 본 트랙 out.
- S3StorageClient 실 구현. interface만 도입, AWS SDK v2 의존성 추가는 v1.x.
- tus 재개 프로토콜 (ADR #13 재이월).
- Quota enforcement (`storage_used` 컬럼은 V5에 없음, ADR # 별도 트랙).
- 바이러스 스캔 (`scan_status`는 V5에 정의되어 있으나 worker 부재, v1.x).
- Checksum 검증 (`checksum_sha256`은 V5에 정의됨 — MVP는 서버 계산 후 저장만, 클라이언트 검증 X).
- MIME magic number 검증 (extension allowlist만 MVP).
- Preview endpoint (`/preview` — 본 트랙 out).

### Phase 지도

| Phase | 목표 | 산출물 | 의존 |
|---|---|---|---|
| **A15.0** | bootstrap | dev-docs 3파일 + ADR #13 재정정 초안 (코드 미변경 commit) | — |
| **A15.1** | StorageClient + LocalFs | `StorageClient` interface + `LocalFsStorageClient` + `application.yml` `app.storage.*` properties + RED 4 케이스 (write/read/delete/exists) | A15.0 |
| **A15.2** | FileUploadService (RED) | `FileUploadService.upload(...)` 메서드 시그니처 + 단위 테스트 RED (folder permission UPLOAD / conflict / new_version / rename / quota는 deferred / audit emit) | A15.1 |
| **A15.3** | FileUploadService (GREEN) | impl: 폴더 lock → conflict 검사 → storage write → files/file_versions INSERT → current_version_id UPDATE → audit emit. extension allowlist 검증. | A15.2 |
| **A15.4** | POST `/api/files` controller | `FileUploadController` + `@PreAuthorize hasPermission(#req.folderId, 'folder', 'UPLOAD')` + multipart parsing + 201 응답 + 에러 매핑 (409/403/413) | A15.3 |
| **A15.5** | GET `/api/files/:id/download` | `FileDownloadController` + `@PreAuthorize hasPermission(#id, 'file', 'READ')` (정정: DOWNLOAD enum 재검토 — docs/03 §3 매트릭스 확인) + storage read + headers + audit `FILE_DOWNLOADED` emit | A15.3 |
| **A15.6** | Frontend api.uploadFile 실 XHR | `frontend/src/lib/api.ts` uploadFile 분기 교체 (FakeXHR 제거 또는 dev mode toggle 보존?) + ConflictDialog 재검증 + UploadStore 통합 테스트 | A15.4, A15.5 |
| **A15.7** | E2E + closure | E2E 시나리오 (업로드 → list → download → 충돌→ resolve), docs sync (00 ADR + 02 §6.1/§7.6/§7.7 + progress.md), PR + dev-docs archive | A15.6 |

## Acceptance Criteria

- [ ] `POST /api/files` 단일 POST multipart. happy path: 201 + FileDto + FileVersionDto. 동일 폴더 동일 이름 + resolution unset → 409 `UPLOAD_CONFLICT`. resolution=`new_version` → file_versions row +1, files.current_version_id 갱신, files row 동일 id 유지. resolution=`rename` → 새 files row + `name (1).ext` 형태 자동 명명.
- [ ] `GET /api/files/:id/download` happy path: 200 + body bytes + `Content-Disposition: attachment; filename*=UTF-8''<percent-encoded>` + `Content-Type` + `Content-Length` + audit row `FILE_DOWNLOADED`.
- [ ] 권한: 폴더 UPLOAD 미보유 → 업로드 403. 파일 READ 미보유 → 다운로드 403. ROLE ADMIN은 통과.
- [ ] storage write 성공 + DB commit 실패 → orphan 객체 (MVP는 별도 cleanup job 부재 — TODO 명시). DB commit 성공 + storage write 실패 → 트랜잭션 rollback (storage write 실패가 RuntimeException이면 자동, IOException은 명시 catch + rethrow as RuntimeException).
- [ ] Frontend `api.uploadFile` 인터페이스 (XHR-like 객체 반환, progress/load/error/abort 이벤트) 1:1 유지. ConflictDialog 분기 (409 UPLOAD_CONFLICT → upload-conflict 이벤트) 유지. UploadStore 단위 테스트 변경 없음.
- [ ] 새 backend tests 모두 GREEN, 기존 tests 회귀 0. Frontend lint/typecheck/test/build 모두 GREEN.
- [ ] docs/00 ADR #13 재정정 (multipart MVP, tus v1.x), docs/02 §7.7 도입 (단일 POST 본문 spec) + §7.6 download 본문 정합, docs/progress.md A15 closure entry.

## 검증 게이트

- **Phase 별 GREEN gate**: 각 phase 끝에서 backend `./gradlew test` GREEN + frontend `pnpm test --run`/typecheck/lint GREEN.
- **트랜잭션 invariant**: A15.3 GREEN 시 `@Transactional`+`SELECT FOR UPDATE folders` 검증 단위 테스트 필수 (병렬 업로드 race → UNIQUE 위반 → `FileNameConflictException` 양쪽 다 발생).
- **Storage cleanup invariant**: storage write 후 DB rollback 시점 → orphan 객체 발생 (MVP 한정 알려진 한계, A15 closure 시 TODO + 별도 트랙 reserve).
- **Frontend 인터페이스 invariant**: M5 UploadStore/ConflictDialog 테스트 코드 0줄 변경.

## 리스크와 완화 전략

1. **ADR #13 (tus) drift** — risk: tus 라이브러리 추가 + chunked 프로토콜 frontend 동시 작업 = 본 트랙 + M5 frontend 재작성. → 완화: ADR #13 재정정 (multipart MVP), tus는 v1.x 별도 ADR. M5 frontend FakeXHR 인터페이스 보존.
2. **storage orphan** — risk: storage write 후 DB commit 실패 시 객체 잔존. → 완화: MVP는 알려진 한계로 명시 (`docs/02 §6.2` 본문 두 개 옵션 중 단순 옵션 = "두 단계 commit"의 단순 형태 = "storage write → DB INSERT 같은 트랜잭션 안에서 실행, 실패 시 storage 객체 manual cleanup 후속 작업 큐"는 본 트랙 out). closure 시 TODO 추가.
3. **권한 매트릭스 drift** — docs/02 §7.6 download Guard `hasPermission(#id, 'file', 'DOWNLOAD')` vs `Permission` enum `READ`/`DOWNLOAD` 분리. → 완화: docs/03 §3 권한 매트릭스 검증 후 결정. MVP는 `READ` 통합 가능성 검토.
4. **resolution=rename 자동 명명 충돌** — `name (1).ext`이 또 충돌하면? → 완화: 단순 카운터 1~N 시도 (cap=100) 또는 timestamp suffix. MVP는 `name (1).ext` 한 번만 시도, 또 충돌 시 409 `UPLOAD_CONFLICT_RENAME_FAILED`로 fallback (frontend는 `rename` 선택 후 다른 이름 입력 유도).
5. **MultipartFile 메모리 한계** — Spring 기본 32MB threshold. 2GB 파일 → temp file. → 완화: `spring.servlet.multipart.{max-file-size, max-request-size}` properties로 명시 (예: `2GB`/`2GB`), `file-size-threshold` 그대로 유지(자동 디스크 spool).
6. **Frontend FakeXHR 제거 부수효과** — `frontend/src/lib/fakeXhr.ts`를 import하는 다른 모듈? → 완화: A15.6 진입 전 grep, 영향 범위 명시.
7. **Audit emission 트랜잭션 분리** — ADR #24 `REQUIRES_NEW`. upload service는 emit을 listener로 위임(기존 패턴) — service에서 직접 호출 금지. → 완화: `FileUploadedEvent` 신규 record + `FileAuditListener` 추가 (folder/permission 패턴 답습).
