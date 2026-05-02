# STRIDE Gap Analysis — Phase 2

Last Updated: 2026-05-02
Source: `docs/03-security-compliance.md §1.3` STRIDE 매트릭스 vs master `90274c7` 코드

> 각 행 status를 (a) **구현됨** / (b) **부분** / (c) **v1.x deferred** / (d) **운영 책임** 중 하나로 분류.
> docs/03 본문은 모두 `상태=설계`로 표기되어 있는 상태 — 본 보고서가 갱신 evidence.

## 결과 요약

| 분류 | 개수 |
|---|---|
| **구현됨** | 18 |
| **부분** | 3 |
| **v1.x deferred** | 5 |
| **운영 책임** | 2 |
| **MVP-blocker (Phase 3 fix 후보)** | 3 |

총 28행 (6 카테고리). 베타 출시 가능 + 3 권장 fix.

## Spoofing (4행)

| 위협 | 분류 | Evidence | 비고 |
|---|---|---|---|
| 세션 토큰 탈취 | 구현됨 | A1 closure: HttpOnly + SameSite=Lax + Secure (운영) cookie. `SecurityConfig` + Spring Session JDBC. ADR #12. | refresh rotation은 v1.x (refresh 메커니즘 자체가 ADR #18로 `/api/auth/me` only) |
| 비밀번호 추측 / credential stuffing | 구현됨 | `LoginAttemptTracker` 5회/15분 lockout (ADR #20 + #23). CAPTCHA/MFA는 ADR #18 v1.x | 단일 인스턴스 in-memory — 멀티 인스턴스화 시 별도 ADR |
| 관리자 사칭 (역할 위조) | 구현됨 | 권한 평가는 `IbizDrivePermissionEvaluator` + DB 조회 (ADR #26). JWT 미사용 (쿠키 세션, ADR #12) | `@PreAuthorize` 모든 mutation 엔드포인트 적용 (원칙 10 PASS) |
| presigned URL 도용 | v1.x deferred | LocalFsStorageClient MVP — presigned 0. ADR #36 단일-POST multipart, 다운로드는 직접 GET `/api/files/{id}/download` | S3 도입 시점에 presigned 정책 결정 |

## Tampering (5행)

| 위협 | 분류 | Evidence | 비고 |
|---|---|---|---|
| 파일 본문 변조 | 부분 | `FileVersion.storage_key UUID NOT NULL UNIQUE` (V5). 신규 업로드 시 새 storage_key 생성 (FileUploadService:130/197) — 변조는 새 버전 생성으로 추적 가능 | ETag 검증 + S3 versioning은 v1.x (S3 미도입) |
| DB 메타 직접 변조 | 운영 책임 | DB IAM 최소권한은 운영 (`app_user` / `audit_admin` / `db_superuser` role 분리는 V4__audit_log_revoke.sql에 정의) | 마이그레이션 외 변경 금지 정책은 운영 절차 |
| 감사 로그 변조·삭제 | 구현됨 | `V4__audit_log_revoke.sql:25-26` REVOKE UPDATE, DELETE. `AuditLogAppendOnlyTest`가 SQLState 42501 검증 (원칙 8 PASS) | WORM 버킷 아카이빙은 v1.x (S3 미도입) |
| Path traversal | 구현됨 | `storage_key=UUID` (원칙 9 PASS). 사용자 제공 경로 직접 사용 0. `LocalFsStorageClient`는 `{root}/{YYYY}/{MM}/{UUID}` 결정적 경로 | UUID regex 검증은 orphan-cleanup walk (ADR #38)에 적용 |
| 악성 파일 업로드 (XSS/RCE) | **부분 / MVP-blocker 후보** | Content-Disposition: attachment + RFC 5987 (FileDownloadController:76-114) 적용 — XSS 1차 방어 ✓. **확장자 화이트리스트 미구현**. **MIME magic 미구현** (v1.x) | 본 트랙 Phase 3 결정: 사내 베타 + attachment 헤더로 1차 방어 → 확장자 화이트리스트 v1.x deferred로 마커. 또는 MVP single-line cap 추가 |

## Repudiation (3행)

| 위협 | 분류 | Evidence | 비고 |
|---|---|---|---|
| 사용자 행위 부인 | 구현됨 | audit_log 26/41 enum 활성 (file/folder/version/permission/share/auth 전 카테고리). `AuditService.record` actor_id + IP + UA + sessionId 모두 기록 (ADR #24) | ADMIN_*/SYSTEM_BACKUP_COMPLETED/USER_PASSWORD_CHANGED 등 15개 enum 미사용 — 모두 deferred 항목 |
| 관리자 행위 부인 | v1.x deferred | `ADMIN_*` 7 enum 정의됨 / emit 0. admin 페이지 frontend 미구현 (audit logs UI만 존재 — M12) | 관리자 페이지 트랙 도입 시점에 emit 활성 |
| 토큰 탈취 후 위장 | 구현됨 | sessionId(=Spring Session row id)는 audit_log에 기록. IP/UA도 기록 → 사후 감식 가능 | ADR #24 정합 |

## Information Disclosure (6행)

| 위협 | 분류 | Evidence | 비고 |
|---|---|---|---|
| 권한 없는 폴더 트리 enumeration | 구현됨 | tree API에 `@PreAuthorize` 적용. `IbizDrivePermissionEvaluator`가 read 권한 필터 (ADR #26 / A4) | folder list/tree 모두 권한 평가 |
| 검색 메타 누설 | 구현됨 | A9 search service에 권한 후처리 (ADR #33). MEMBER → grant 보유분만, ADMIN → 전부 | 검색어 자체 audit는 v1.x (`SEARCH_QUERIED` enum 미정의) |
| S3 객체 URL 추측 | 구현됨 | UUID storage_key (원칙 9). 직접 다운로드는 `@PreAuthorize("hasPermission(#id, 'file', 'READ')")` (FileDownloadController) | 버킷 public access 차단은 v1.x (S3 미도입) |
| 다운로드 로그 누설 | 구현됨 | audit 조회는 `/api/admin/audit` AUDITOR/ADMIN role only (docs/02 §7.12) | M12 frontend도 권한 게이트 |
| 백업본 노출 | 운영 책임 | 백업 정책 자체 v1.x | RDS managed snapshot + KMS는 운영 인프라 책임 |
| 에러 메시지 leak (SQL/스택) | **부분** | Spring Boot default error handling — `application.yml`에서 `server.error.include-stacktrace`/`include-message` 명시 검증 필요 | Phase 3 후보: production profile에서 generic 에러 강제 |

## Denial of Service (4행)

| 위협 | 분류 | Evidence | 비고 |
|---|---|---|---|
| 대용량 업로드 스토리지 고갈 | 구현됨 | `spring.servlet.multipart.max-file-size=100MB` + `max-request-size=110MB` (application.yml:40-41). 사용자 quota는 v1.x (quota 컬럼 0) | ADR #36 cap 일치 |
| 검색 N+1 / 깊은 트리 lazy 로딩 | 부분 | A9 search는 페이지네이션 cursor 기반 (limit+1, ADR #33). 폴더 tree는 lazy 로딩 미구현 (전체 반환 가정) | 10k+ 폴더 운영 시점에 lazy 도입 (ADR backlog) |
| 인증 brute force | 구현됨 | LoginAttemptTracker 5회/15분 lockout (ADR #20). IP rate limit은 v1.x (ADR #18) | docs/03 §1.3 "TBD: A 트랙에서 RateLimiter 확정"은 lockout으로 1차 방어 — TBD 마커 close 가능 |
| audit_log 폭증 | v1.x deferred | `audit_level` 컬럼 0 (ADR #9 deferred), 파티션 미적용. SEARCH_QUERIED/FILE_VIEWED 미emit으로 폭증 위험은 낮음 | 운영 시점에 audit row 증가율 측정 후 결정 |

## Elevation of Privilege (5행)

| 위협 | 분류 | Evidence | 비고 |
|---|---|---|---|
| 프론트 권한 우회 | 구현됨 | 모든 mutation에 `@PreAuthorize` (원칙 10 PASS). 프론트 `usePermission`은 UX용 | A4 closure 검증 |
| 권한 상속 버그 | 구현됨 | resource-level evaluator (A4.3). recursive ancestor lookup `permissions` row + role + everyone + dept (A16). 단위 테스트 다수 (`PermissionRepositoryTest`, `IbizDrivePermissionEvaluatorTest`) | deny 우선 로직은 v1.x (ADR #28 — preset 단일, deny semantics deferred) |
| 비활성 사용자 토큰 재사용 | 구현됨 | `SessionValidityFilter` (A1.6) absolute 만료 검사. `User.active=false` 시 인증 차단 | logout-all 엔드포인트는 v1.x |
| MFA 미적용 관리자 | v1.x deferred | ADR #18 — MFA 자체가 v1.x | 사내 베타 가정 |
| 부서 LTREE 경계 우회 | 구현됨 | A16 (V7) 도입. `findEffective` SQL은 dept 매칭이 `subject_id = (SELECT department_id FROM users WHERE id=:userId AND active)`로 정확 매칭 (LTREE prefix 후손 매칭 v1.x) | startsWith 사용 0 |

## MVP-blocker 후보 (Phase 3 트리아지 대상)

다음 3건은 본 트랙 Phase 3에서 사용자 sign-off 시 결정:

### 1. Tampering — 악성 파일 업로드 (확장자 화이트리스트)
- 현재: Content-Disposition: attachment 적용 = XSS 1차 방어 OK
- 결정 옵션:
  - (A) **추천**: v1.x deferred 명시 마커 (사내 베타 + attachment 헤더 + 사용자 보안 가이드). docs/03 §5.3 inline `> v1.x deferred — 사내 베타 + attachment 헤더로 1차 방어. 외부 출시 시 화이트리스트 도입`
  - (B) MVP single-config-line 추가: `app.upload.allowed-extensions` 설정 + FileUploadService 검증 (10~30 LOC)
- 근거: 사내 베타 가정에서 추가 비용 < 효용. attachment 헤더가 직접 렌더링 차단 → XSS 페이로드는 다운로드 후 사용자 명시 실행이 필요. 사내 사용자 신뢰 수준 가정 시 MVP에서는 deferred가 합리적

### 2. Information Disclosure — 에러 메시지 leak (production stacktrace)
- 현재: `application.yml`에 `server.error.include-stacktrace` 명시 검증 필요
- 결정 옵션:
  - (A) **추천**: `application.yml`에 명시 추가 — `server.error.include-stacktrace: never`, `include-message: on-param`, `include-binding-errors: on-param`. 1줄 변경 = MVP-fix
  - (B) Spring profile 기반 (`application-prod.yml`) — 운영 정책 + 분리. 나중 추가 가능
- 근거: production profile 미운영 단계라도 `application.yml` 단일 파일에 명시 = blast radius 최소

### 3. Spring Security 헤더 명시화
- 현재: `.headers()` 호출 부재 → Spring Security 6 default 의존 (nosniff/X-Frame-Options=DENY/Cache-Control 자동)
- 결정 옵션:
  - (A) **추천**: `SecurityConfig`에 명시적 `.headers(h -> h.contentTypeOptions(...).frameOptions(...))` 추가 — Spring Security 7+ 호환성 보호. 5~10 LOC 추가
  - (B) defer — 현재 default가 안전, Spring 7+ migration 시 별도 트랙
- 근거: KISS — 명시화는 미래 호환성을 위한 보험. MVP 베타 출시 자체에는 영향 0이지만 작은 변경이라 권장

## v1.x deferred 5건 명시

다음 5행은 본 트랙에서 deferred로 마커 (docs/03 §1.3 본문에 inline 추가 예정):

1. presigned URL 도용 (S3 미도입)
2. 백업본 노출 (백업 인프라 v1.x)
3. 관리자 행위 부인 (admin 페이지 v1.x)
4. audit_log 폭증 (audit_level/파티션 v1.x — ADR #9)
5. MFA 미적용 관리자 (ADR #18)

## Phase 4에서 docs/03 §1.3 본문 갱신 패치

각 행의 `상태=설계` → `상태=구현됨` / `상태=v1.x deferred (사유)` / `상태=운영 책임` 으로 갱신.
