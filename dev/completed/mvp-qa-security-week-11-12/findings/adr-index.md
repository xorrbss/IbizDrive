# ADR Index — IbizDrive MVP

Last Updated: 2026-05-02
Source: `docs/00-overview.md` §5 + §5.1

## Active ADRs (#1 ~ #38)

| # | 결정 (요지) | 영향 docs § | 영향 코드 모듈 | 관련 closure 트랙 |
|---|---|---|---|---|
| 1 | URL folderId 중심 (slug 표시용) | 01 §2 | `frontend/app/(explorer)/files/[...parts]`, `lib/folderPath.ts` | M1 routing |
| 2 | RightPanel = query param | 01 §2.3 | `frontend/components/files/RightPanel*` | M1 routing |
| 3 | 낙관적 업데이트 = 비파괴적만 | 01 §1.3 | `frontend/hooks/use*Mutation*` | (전 트랙 공통) |
| 4 | UNIQUE = partial index `WHERE deleted_at IS NULL` | 02 §3.2 | `V1__init.sql` (folders/files) | A0 schema |
| 5 | storage_key = UUID | 02 §5 | `backend/file/FileEntity`, `frontend/types` | A4 / A15 |
| 6 | 업로드 완료 = 트랜잭션 충돌 검사 | 02 §6.1 | `backend/file/FileMutationService` | A4 / A15 |
| 7 | ~~MVP 업로드 = multipart~~ → ADR #13 supersede → ADR #36 재정정 (multipart 복귀) | 01 §9 | `backend/file/FileUploadController` | A15 |
| 8 | ~~MVP 실시간 = 폴링~~ → ADR #14 supersede (SSE) | 01 §15.1 | (deferred — SSE 인프라 milestone) | — |
| 9 | 뷰 로그 = 민감 폴더만 | 03 §4.2 | (audit_level 미구현 — folder별 컬럼 부재) | — (deferred 검토 후보) |
| 10 | 문서 분리 (00/01/02/03/04) | 00 본 문서 | docs/ | (전 트랙 공통) |
| 11 | 백엔드 = Spring Boot 3.x + Java 21 | 00 §1.3, 02 §1 | `backend/build.gradle.kts` | A0 setup |
| 12 | 인증 = 쿠키 세션 + Spring Session JDBC | 03 §1, §2 | `backend/auth/`, `V2__spring_session.sql` | A1 |
| 13 | ~~업로드 = tus-java-server~~ → ADR #36 supersede (multipart 복귀) | 01 §9, 02 §6.1 | `backend/file/` | A15 |
| 14 | 실시간 = SSE (SseEmitter, MVC) | 01 §15 | (인프라 deferred — `EventBus`/`SseEmitter` 0개) | — |
| 15 | API base URL = build-time `.env.local` | 00 §1.3 | `frontend/lib/api.ts` | (전 트랙 공통) |
| 16 | 정규화 = 공유 fixtures (`docs/normalize-fixtures.json`) | 02 §3 | `backend/common/normalize/`, `frontend/lib/normalize.ts` | A0 |
| 17 | 권한 = 백엔드 권위 (`@PreAuthorize`) | 03 §3 | `backend/permission/`, controller `@PreAuthorize` | A1.5 |
| 18 | MVP 인증 = 자체 ID/PW only (SSO/MFA deferred) | 03 §2 | `backend/auth/AuthController` | A1 |
| 19 | BCrypt(strength=12) + 12자 이상 | 03 §2.7 | `backend/auth/PasswordEncoderConfig` | A1 |
| 20 | 세션 = idle 30m + absolute 8h + 5회/15m lockout | 03 §2.6 | `backend/auth/LoginAttemptTracker` | A1 |
| 21 | 등록 = 관리자 초대 only | 03 §2.8, 02 §7.12 | `backend/user/UserController` (admin endpoint) | A1 |
| 22 | `/api/auth/me` = identity + role + cacheKey only | 02 §7.4, 01 §6.3 | `backend/auth/MeController` | A1 |
| 23 | lockout 카운터 = in-memory `ConcurrentHashMap` | 03 §2.6 | `backend/auth/LoginAttemptTracker` | A1.3 |
| 24 | Audit emission = AOP `@Audited` + Auth event listener | 03 §4, A2.1, A2.4 | `backend/audit/`, `@Audited` 어노테이션 | A2 |
| 25 | Audit append-only = DB role 분리 + REVOKE UPDATE/DELETE | 02 §2.8, 03 §4.4 | `V4__audit_role_split.sql` | A2 |
| 26 | PermissionEvaluator MVP = role-only (resource-level A4) | 03 §3.1~§3.6, 02 §7.10 | `backend/permission/IbizDrivePermissionEvaluator` | A3 / A4.4 |
| 27 | A4 = PR 2개 분할 (data + controllers) | (process) | — | A4-data + A4-controllers |
| 28 | permissions v1 = `preset` 단일 컬럼 (deny v1.x) | 03 §3.4, 02 §2.6 | `V5__permissions.sql`, `backend/permission/Preset` | A4.0~A4.3 |
| 29 | `file_versions` A5 이월 (V5에는 schema만) | 02 §2.5/§7.6, A4.1 | `V5__file_versions.sql`, A5 entity 도입 | A5 |
| 30 | A4.7 root create/move = ROLE ADMIN, 그 외 부모 EDIT | 03 §3, 02 §7.5 | `backend/folder/FolderController` `@PreAuthorize` | A4.7 |
| 31 | A7 hard purge = DB-only (S3 deferred → orphan-cleanup ADR #38) | 02 §2.5/§7.11.1, 04 §13 | `backend/purge/HardPurgeService` | A7 |
| 32 | A8 manual trash purge = single + per-row audit (bulk 미구현) | 02 §7.11/§7.13.1 | `backend/trash/TrashPurgeService` | A8 |
| 33 | A9 search = LIKE on `normalized_name` (tsvector deferred) | 02 §7.8 | `backend/search/SearchService` | A9 |
| 34 | A10/A12/A13 shares = `shares` row + permissions row 메타 | 02 §2.7/§7.9 | `backend/share/`, `V6__shares.sql` | A10 / A12 / A13 / share-expired-cron / permissions-expired-cron |
| 35 | A14 user search = user-only, `isAuthenticated()` | 02 §7.14 | `backend/user/UserSearchController` | A14 |
| 36 | A15 storage = `StorageClient` + LocalFs MVP + multipart 업로드 | 02 §6.1/§7.6/§7.7 | `backend/storage/`, `backend/file/FileUploadController/FileDownloadController` | A15 |
| 37 | A16 department subject picker = V7 도메인 + 3-way picker | 02 §2.x/§7.9/§7.15, 03 §3.3~§3.5, 01 §14.4 | `backend/department/`, `frontend/components/shares/DepartmentSearchCombobox` | A16 |
| 38 | Storage orphan cleanup = daily cron + summary audit | 02 §5/§6, 03 §4.1, 04 §13 | `backend/storage/StorageOrphanCleanupService/Job` | storage-orphan-cleanup |

## Superseded ADRs

| # | 원 결정 | Superseded by | 사유 |
|---|---|---|---|
| 7 | MVP 업로드 = multipart | #13 (2026-04-25) | tus-java-server + S3 multipart 위임 안정화 |
| 8 | MVP 실시간 = 폴링 | #14 (2026-04-25) | SseEmitter MVC로 인프라 추가 부담 사실상 0 |

추가 의문: ADR #13(tus)이 ADR #36(multipart 복귀)에 의해 다시 supersede됨. ADR #36 본문에 명시는 됐으나 docs/00 §5.1 표에는 미반영 — Phase 2 finding 후보.

## 분석 요약

### Active ADRs (38)

- 코드 evidence 매핑 가능 (closure 트랙 존재): 1, 2, 3, 4, 5, 6, 7→13→36(트랙 A15), 11, 12, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38 — **33개**
- 인프라 deferred (코드 0): **8 (폴링/SSE deferred)**, **9 (뷰 로그 audit_level)**, **14 (SSE)**
- 문서 결정 (코드 무관): **10 (문서 분리)**

### v1.x deferred 명시 ADR

- #18 — SSO / MFA / remember-me / IP rate limit / 셀프 PW reset 모두 v1.x
- #29 — `file_versions` POST/restore/scanner v1.x → A5 closure로 일부 활성화 / 잔여 A6+
- #31 — S3 객체 hard delete → ADR #38 storage-orphan-cleanup으로 흡수 완료
- #38 — S3StorageClient `listOlderThan` impl 자체는 v1.x

### Phase 2 finding 후보 (인덱스 작성 시점)

1. ADR #13의 ADR #36 supersede가 docs/00 §5.1 표에 미반영 — 표 갱신 필요
2. ADR #9 (뷰 로그 = 민감 폴더만) — `audit_level` 컬럼 0, MVP 미구현 → docs/03 §4.2 본문 deferred 마커 필요
3. ADR #14 SSE — `SseEmitter` 사용처 0, 실시간 구현 0 → 본 트랙에서 v1.x 명시 deferred 결정 필요
