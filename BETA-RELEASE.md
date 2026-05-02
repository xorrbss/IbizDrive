# IbizDrive — Beta Release Checklist (사내 베타)

Last Updated: 2026-05-02
Source: `mvp-qa-security-week-11-12` 트랙 closure + `feature/mvp-prod-profile` 트랙 (application-prod.yml + cron 4종 활성화)

> **본 문서의 목적**: 사내 베타 GO/NO-GO 결정에 필요한 단일 페이지 체크리스트.
> docs/03 §1.3 STRIDE matrix + docs/04 §13 cron + 인프라 게이트를 한 곳으로 모음.

## 베타 출시 전제

- **베타 = 사내 베타 (내부 사용자 전용)**. 외부 일반 출시는 본 체크리스트 범위 밖 (v1.x 별도 트랙).
- 사내 도메인 + 사내 IdP/SSO 가정 (외부 인터넷 노출 없음).
- 외부 모의해킹 / SAST·DAST / SCIM / Legal Hold / quota 시스템은 v1.x.

## 1. 코드 베이스 게이트

| 항목 | 상태 | 검증 |
|---|---|---|
| backend test GREEN | ✓ | `cd backend && ./gradlew test` — 75 classes / 723 tests / 522 PASS / 201 skip(no Docker IT) / 0 fail |
| frontend test GREEN | ✓ | `cd frontend && pnpm test --run` — 563/563 |
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
| MFA | ✗ v1.x deferred (ADR #18) |

## 6. 감사 / 권한

| 항목 | 상태 |
|---|---|
| audit_log append-only (DB-level REVOKE) | ✓ V4 + `AuditLogAppendOnlyTest` |
| audit emit coverage | 41 enum 중 26 emit (63%) — 미사용 15는 모두 deferred 항목 (mvp-qa-security Phase 2) |
| `@PreAuthorize` 미보호 mutation | 0 (mvp-qa-security P2.3 검증) |
| 권한 evaluator (`IbizDrivePermissionEvaluator`) | ✓ A4 + A11/A16 closure |

## 7. v1.x deferred 명시 (사내 베타 가정)

- 확장자 화이트리스트 / MIME magic / AV 스캔 — `Content-Disposition: attachment`로 1차 방어 (docs/03 §5.3)
- presigned URL / S3 / KMS / cross-region replication — LocalFsStorageClient MVP (docs/03 §5)
- MFA / refresh rotation / SCIM — ADR #18
- audit_level / 파티션 / `FILE_VIEWED` emit — ADR #9
- Legal Hold (전체 §6.3 / §10) — docs/00 §4.3 v2.x
- admin frontend (사용자/부서/권한/스토리지/정책/시스템 페이지) — audit logs UI(M12)만 활성, 나머지 v1.x

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

## 10. 참조

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
