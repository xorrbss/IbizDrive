# IbizDrive — Beta Release Checklist (사내 베타)

Last Updated: 2026-05-07
Source: `mvp-qa-security-week-11-12` 트랙 closure + `feature/mvp-prod-profile` 트랙 (application-prod.yml + cron 4종 활성화) + `m-rp-rightpanel-completion` 트랙 closure + `auth-pages` 트랙 closure (셀프 가입 + first-user-ADMIN, ADR #41) + `auth-must-change-pw` 트랙 closure (ADR #21 §2.7) + `auth-forgot-rate-limit` 트랙 closure (ADR #44) + `m-admin-entry-rewrite` 트랙 closure (admin shell + `POST /api/admin/users`, ADR #21) + `auth-password-policy` 트랙 closure (ADR #19 본문 회복) + `email-async` 트랙 closure (`@Async EmailService`, ADR #45) + `admin-user-search-update` (Wave 1 T1 — `ADMIN_USER_UPDATED` emit, #59) + `audit-export-endpoint` (Wave 1 T2 — `GET /api/admin/audit/export` + `AUDIT_EXPORTED` emit) + `admin-department-crud` (Wave 2 T4 — `/admin/departments` + 3 emit) + `admin-permission-matrix` (Wave 2 T5 — `GET /api/admin/permissions` + `/admin/permissions` read-only viewer) + `wave2-t6-folder-items-wire` (Wave 2 T6 — `GET /api/folders/{id}/items` + `GET /api/files/{id}` + frontend mock 일괄 제거) + `admin-dashboard` 트랙 closure (`GET /api/admin/dashboard/summary` + KPI 그리드, 2026-05-07) + `wave2-t9-admin-global-trash` (Wave 2 T9 — `GET /api/admin/trash` + `/admin/trash/all` viewer, 2026-05-07)

> **본 문서의 목적**: 사내 베타 GO/NO-GO 결정에 필요한 단일 페이지 체크리스트.
> docs/03 §1.3 STRIDE matrix + docs/04 §13 cron + 인프라 게이트를 한 곳으로 모음.

## 베타 출시 전제

- **베타 = 사내 베타 (내부 사용자 전용)**. 외부 일반 출시는 본 체크리스트 범위 밖 (v1.x 별도 트랙).
- 사내 도메인 + 사내 IdP/SSO 가정 (외부 인터넷 노출 없음).
- 외부 모의해킹 / SAST·DAST / SCIM / Legal Hold / quota 시스템은 v1.x.

## 1. 코드 베이스 게이트

| 항목 | 상태 | 검증 |
|---|---|---|
| backend test GREEN | ✓ | `cd backend && ./gradlew test` — BUILD SUCCESSFUL (auth-pages/forgot-rate-limit/must-change-pw/admin-invite/password-policy/email-async 누적 TDD 케이스 포함) |
| frontend test GREEN | ✓ | `cd frontend && pnpm test --run` — 817 passed / 11 skipped (Wave 2 T6 closure 시점, 105 files) |
| frontend typecheck/lint/build | ✓ | `pnpm typecheck && pnpm lint && pnpm build` 모두 exit 0 |
| 코드 위반 (CLAUDE.md §3 11개 원칙) | ✓ FAIL 0 | `findings/principle-conformance.md` |
| STRIDE 매트릭스 evidence | ✓ 28/28 매핑 | `findings/stride-gap-analysis.md` |

## 2. 인프라 게이트 (운영자 책임)

### 2.1 전송 / 도메인

- [ ] HTTPS 종단 (리버스 프록시 또는 ALB) — TLS 1.2+
- [ ] HSTS 헤더 (리버스 프록시 또는 CDN)
- [ ] 사내 도메인 + DNS 등록
- [ ] CORS `ibizdrive.cors.allowed-origins`를 사내 도메인으로 설정 (현재 default `http://localhost:3000`)

### 2.2 쿠키 / 세션

- [x] `application-prod.yml`에서 `server.servlet.session.cookie.secure=true` (mvp-prod-profile closure) — `SPRING_PROFILES_ACTIVE=prod` 활성 시 자동
- [x] `application-prod.yml` 분리 (mvp-prod-profile closure) — `ProdProfileConfigTest`로 회귀 차단
- [x] `http-only=true`, `same-site=lax` (이미 설정)

### 2.3 시크릿

- [ ] `spring.datasource.password` 환경 변수 또는 사내 시크릿 저장소 주입 — 현재 dev default(`dev_password_only_change_in_real_envs`) 운영 절대 금지
- [ ] BCrypt strength=12 유지 (`SecurityConfig.passwordEncoder()`, ADR #19)
- [ ] `.env*` 커밋 금지 (`.gitignore` 확인)

### 2.4 DB / 스토리지

- [ ] managed Postgres (RDS 또는 동등) + 자동 일일 스냅샷 + PITR
- [ ] DB role 분리 (V4 `app_user` / `audit_admin` / `db_superuser`) 적용 — `app_user`만 application 사용
- [ ] storage 디스크 (`ibizdrive.storage.local.root=./uploads`) 백업 정책 (rsync 또는 snapshot)
- [ ] Flyway 마이그레이션 V1~V7 적용 확인

## 3. 운영 cron 활성화

`application-prod.yml`이 모두 `enabled=true`로 override (mvp-prod-profile closure). `SPRING_PROFILES_ACTIVE=prod` 활성 시 자동.
`ProdProfileConfigTest`가 키 누락/오타를 회귀 차단.

| Cron | 활성화 옵션 | prod profile |
|---|---|---|
| `purge.expired` | `app.purge.enabled=true` | ✓ — 휴지통 30일 후 hard delete |
| `share.expire` | `app.share.expiration.enabled=true` | ✓ — 만료된 공유 자동 expire + audit |
| `permission.expire` | `app.permission.expiration.enabled=true` | ✓ — 만료된 권한 cleanup + audit |
| `storage.orphan.cleanup` | `app.storage.orphan-cleanup.enabled=true` | ✓ — 일일 storage 객체 회수 (ADR #38) |

> 단일 인스턴스 가정 — 멀티 인스턴스 시 `@SchedulerLock` 도입 필요(ADR backlog).
> default profile(dev/test/CI)에서는 모두 `false` 유지 — 무영향.

## 4. 보안 헤더 / CSRF

| 항목 | 상태 |
|---|---|
| `X-Content-Type-Options: nosniff` | ✓ `SecurityConfig.headers().contentTypeOptions()` 명시 (mvp-qa-security Phase 3) |
| `X-Frame-Options: DENY` | ✓ `SecurityConfig.headers().frameOptions(deny)` |
| `Cache-Control: no-cache, no-store, max-age=0` | ✓ `SecurityConfig.headers().cacheControl()` |
| CSRF double-submit | ✓ `CookieCsrfTokenRepository.withHttpOnlyFalse()` + plain handler (ADR #12) |
| `Content-Disposition: attachment` (파일 다운로드) | ✓ `FileDownloadController:76-114` RFC 5987 |
| `server.error.include-stacktrace=never` | ✓ `application.yml` (mvp-qa-security Phase 3) |

## 5. 인증 / 세션

| 항목 | 상태 |
|---|---|
| 쿠키 세션 (Spring Session JDBC) | ✓ ADR #12 |
| idle 30분 sliding (`spring.session.timeout=PT30M`) | ✓ ADR #20 |
| absolute 8h 한도 (`SessionValidityFilter`) | ✓ A1.6 |
| 로그인 lockout 5회/15분 (`LoginAttemptTracker`) | ✓ ADR #20 |
| forgot rate-limit email + IP 분당 1회 (`ForgotPasswordRateLimiter`) | ✓ ADR #44 |
| 셀프 가입 (`POST /api/auth/signup`) + first-user-ADMIN | ✓ ADR #41 (auth-pages) — BETA 첫 가입자 부재 차단 해제 |
| `/login` · `/signup` 페이지 + `(explorer)` 401 가드 | ✓ ADR #41 — useMe → `/login?next=...` replace |
| 비밀번호 정책 (12자 + 영·숫 + 공백 금지, max 128) | ✓ ADR #19 본문 회복 (auth-password-policy) — `@ValidPassword` + frontend `lib/password.ts` mirror, signup/reset/change 3 endpoint 통합 |
| 강제 비밀번호 변경 UX (`mustChangePassword` flag) | ✓ ADR #21 §2.7 (auth-must-change-pw) — backend `clearMustChangePassword()` + frontend `AuthGuard`/`/account/password?force=1` redirect |
| 운영자 초대 endpoint (`POST /api/admin/users`) | ✓ ADR #21 closure (m-admin-entry-rewrite) — `@PreAuthorize("hasRole('ADMIN')")` + `TempPasswordGenerator` + `ADMIN_USER_CREATED` audit |
| 이메일 비동기 발송 (`@Async("emailExecutor")`) | ✓ ADR #45 (email-async) — anti-enumeration timing leak 완화 (caller latency < 50ms 통합 테스트 가드) |
| MFA | ✗ v1.x deferred (ADR #18) |

## 6. 감사 / 권한

| 항목 | 상태 |
|---|---|
| audit_log append-only (DB-level REVOKE) | ✓ V4 + `AuditLogAppendOnlyTest` |
| audit emit coverage | 47 enum 중 40 emit (~85%) — `USER_REGISTERED` (auth-pages) + `USER_PASSWORD_FORGOT_REQUESTED` / `USER_PASSWORD_RESET` / `USER_PASSWORD_CHANGED` (a1.5-email-infra) + `ADMIN_USER_CREATED` (m-admin-entry-rewrite) + `ADMIN_USER_DEACTIVATED` / `ADMIN_ROLE_CHANGED` (admin-user-mgmt) + `ADMIN_USER_UPDATED` (admin-user-search-update Wave 1 T1) + `AUDIT_EXPORTED` (audit-export-endpoint Wave 1 T2) + `ADMIN_DEPARTMENT_CREATED` / `ADMIN_DEPARTMENT_UPDATED` / `ADMIN_DEPARTMENT_DEACTIVATED` (admin-department-crud Wave 2 T4) 신규 emit. **미emit 7개는 §7 deferred 매핑 (`audit-emit-gap-mapping` closure, 2026-05-05; T2 갱신 2026-05-06) — 누락(버그) 0건** |
| `@PreAuthorize` 미보호 mutation | 0 (mvp-qa-security P2.3 검증) |
| 권한 evaluator (`IbizDrivePermissionEvaluator`) | ✓ A4 + A11/A16 closure |
| audit query — file 단위 활동 조회 | ✓ M-RP.4 (`?targetType=file&targetId=`) — RP-2 정책: 파일 READ 보유 시 actor 제한 우회 (ADR #40) |

## 7. v1.x deferred 명시 (사내 베타 가정)

- 확장자 화이트리스트 / MIME magic / AV 스캔 — `Content-Disposition: attachment`로 1차 방어 (docs/03 §5.3)
- presigned URL / S3 / KMS / cross-region replication — LocalFsStorageClient MVP (docs/03 §5)
- MFA / refresh rotation / SCIM — ADR #18 (`USER_MFA_ENABLED` emit deferred)
- audit_level / 파티션 / `FILE_VIEWED` emit / `FOLDER_AUDIT_LEVEL_CHANGED` emit — ADR #9 (docs/04 §6 line 269)
- Legal Hold (전체 §6.3 / §10) — docs/00 §4.3 v2.x (`ADMIN_LEGAL_HOLD_PLACED` / `ADMIN_LEGAL_HOLD_RELEASED` emit deferred)
- admin frontend (정책 페이지) — admin shell + `/admin/users` 초대/목록/role 변경/비활성/검색/재활성/displayName 편집 (m-admin-entry-rewrite + admin-user-mgmt + admin-user-search-update Wave 1 T1) + `/admin/departments` 부서 CRUD(생성/검색/rename/(de)activate, admin-department-crud Wave 2 T4) + `/admin/permissions` 권한 매트릭스 read-only viewer(subject/resource/preset/q 필터 + 만료 배지, admin-permission-matrix Wave 2 T5) + `/admin/system` 운영 cron 4종 read-only 노출 (Wave 1 T3 — `GET /api/admin/system/cron`, 변경은 application.yml + 재기동 → mutation은 v1.x. 읽기 가드는 Wave 1.5 `auditor-cron-readonly`에서 ADMIN+AUDITOR로 확장. UI 진입은 Wave 1.5 `auditor-admin-ui-access`(2026-05-07)에서 layout 가드를 ADMIN+AUDITOR로 완화 — `/admin/audit/logs`와 `/admin/system`은 AUDITOR 진입 가능, mutation 페이지는 페이지 단 default 가드로 ADMIN-only 유지) + `/admin/storage` 시스템 합계 + 정리 기록 overview(admin-storage-overview, 2026-05-07) + `/admin/trash/all` 전역 휴지통 viewer(q/type/ownerId 필터 + cursor pagination + 단건 복원/영구삭제, wave2-t9-admin-global-trash Wave 2 T9, 2026-05-07 — `GET /api/admin/trash`, mutation은 기존 endpoint 재사용. 휴지통 보존 정책 UI(`/admin/trash/policy`) / bulk / 2인 승인 / `deletedBy` 컬럼은 v1.x) + audit logs UI(M12) 활성. quota(`ADMIN_QUOTA_CHANGED` emit) + 권한 mutation(grant/revoke direct CRUD)은 v1.x
- DB backup cron — managed Postgres / RDS 자동 백업으로 대체, docs/04 §13 "별도 cron 미구현" (`SYSTEM_BACKUP_COMPLETED` emit deferred)
- audit log JSON export endpoint — docs/04 §7.2 v1.x (CSV server-side export는 Wave 1 T2에서 ship — `GET /api/admin/audit/export`, `AUDIT_EXPORTED` emit 활성)

## 8. 모니터링 (사내 베타 최소)

- [ ] application logs 수집 (`com.ibizdrive` INFO 이상) — *운영자 책임 (외부 log shipper: fluent-bit / Promtail / Datadog agent)*
- [ ] audit_log SELECT 권한 (AUDITOR/ADMIN role)을 사내 보안 담당자에게 부여
- [ ] 슬랙 webhook (장애 알림) — *운영자 책임 (외부 log shipper의 alert rule, in-process logback appender 비채택, v1.x 관측성 트랙)*
- [ ] DB connection pool 사용률 — *v1.x (Actuator 도입 시점)*

## 9. 베타 출시 GO 결정

다음 모두 `[x]` 시 **GO**:

- [x] §1 코드 베이스 게이트 (모든 행 ✓)
- [ ] §2 인프라 게이트 (운영자 sign-off)
- [x] §3 cron 4종 prod profile에서 자동 활성 (mvp-prod-profile closure) — 운영 1회 dry-run은 staging 가동 시점에 확인
- [x] §4 보안 헤더 (코드 적용)
- [x] §5 인증 / 세션 (코드 적용)
- [x] §6 감사 / 권한 (코드 적용)
- [ ] §8 모니터링 최소 (외부 log shipper + audit 조회 권한 부여) — 운영자 셋업

§2/§3/§8은 staging/prod 인프라 셋업 시점에 채워야 함. **현재 master 시점 = 코드 게이트만 PASS, 인프라 게이트는 운영팀 책임으로 미정 = NO-GO 상태이지만 코드 측 readiness는 완료**.

## 10. RightPanel 4탭 완성 (M-RP 트랙)

| 탭 | 상태 | 비고 |
|---|---|---|
| Detail | ✓ pre-MVP | 파일 메타 표시 |
| Versions | ✓ M-RP.1~2 | read-only list + 버전별 다운로드/복원 (ADR #39 옵션 A — current_version_id 재지정, denormalized 메타 동기화) |
| Permissions | ✓ M-RP.3 | 9 chip held/unheld 시각 구분 |
| Activity | ✓ M-RP.4 | 파일 단위 audit timeline (ADR #40 RP-2 정책) |

> 모든 탭은 비활성 시 fetch 차단(`enabled: tab === 'X'`) — 불필요 네트워크 0.

## 11. 버전 관리 (M-RP.2)

| 항목 | 상태 |
|---|---|
| `GET /api/files/{fileId}/versions` | ✓ A5 (read) |
| `GET /api/files/{fileId}/versions/{versionId}/download` | ✓ M-RP.2 — RFC 5987 + READ 가드 + `VERSION_DOWNLOADED` audit |
| `POST /api/files/{fileId}/versions/{versionId}/restore` | ✓ M-RP.2 — `@Transactional` + SELECT FOR UPDATE + EDIT 가드 + `VERSION_RESTORED` audit |
| 복원 의미론 | 옵션 A (current_version_id 재지정, 새 version 생성 안 함) — ADR #39 |
| denormalized 메타 동기화 | `files.size_bytes` / `files.mime_type` — `FileUploadService:214-217` invariant 보존 |
| 멱등 | 같은 versionId 재호출 시 audit 추가 0 |

## 12. 참조

- 트랙 산출: `dev/active/mvp-qa-security-week-11-12/findings/`
  - `baseline-report.md` — 1차 베이스라인
  - `adr-index.md` — ADR #1~#38 + 2 superseded
  - `empty-checkbox-inventory.md` — docs/03·04 빈 체크박스 분류
  - `stride-gap-analysis.md` — 28행 STRIDE evidence
  - `principle-conformance.md` — CLAUDE.md §3 11개 원칙
  - `triage-decisions.md` — Phase 3 sign-off + remediation
- ADR 상세: `docs/00-overview.md` §5
- STRIDE 본문: `docs/03-security-compliance.md` §1.3
- 운영 cron 본문: `docs/04-admin-operations.md` §13
