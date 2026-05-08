# 03 - 보안 & 컴플라이언스

> 권한 모델, 감사 정책, 저장소 보안, 법적 보존(Legal Hold) 등 보안 관점 설계.
> **현재 상태**: §1·§2·§3·§4 본문 활성, §5·§6·§7·§8 일부 본문 진행 중.

---

## 1. 위협 모델 (Threat Model)

> 본 절은 v1.0 위협 모델 초안. **백엔드 스택 미정 부분은 "TBD: A 트랙에서 확정"** 으로 표기하며,
> 백엔드 합류 후 A 트랙(NestJS or Spring Boot 결정)에서 구체 구현 채워넣음.

### 1.1 보호 대상 자산 (Assets)

| ID | 자산 | 가치 | 노출 채널 |
|---|---|---|---|
| A1 | **문서 본문** (S3 객체) | 회사 영업기밀 / 개인정보 / 계약서 | API 다운로드, presigned URL, S3 버킷 |
| A2 | **문서 메타데이터** (DB `files`/`folders`) | 디렉터리 구조·작성자·소유자가 곧 조직 활동 노출 | API JSON, 검색 결과 |
| A3 | **권한 데이터** (DB `permissions`) | 권한 우회 시 전 자산 노출 | 관리자 API, 권한 평가 미들웨어 |
| A4 | **감사 로그** (DB `audit_log`) | 사후 추적·법적 증거 | DB, 감사 페이지(/admin/audit/logs), CSV export |
| A5 | **인증 토큰 / 세션** | 탈취 시 어떤 사용자도 사칭 가능 | 브라우저 쿠키, refresh 엔드포인트 |
| A6 | **암호화 키 (KMS)** | 저장소 객체 일괄 복호화 가능 | KMS API |
| A7 | **백업 / 스냅샷** | 운영 데이터와 동등 가치 | 백업 버킷, RDS 스냅샷 |

### 1.2 트러스트 경계 (Trust Boundaries)

```text
[브라우저] ──TLS──▶ [Edge/CDN] ──▶ [API 게이트웨이] ──▶ [App 서버] ──▶ [DB / S3 / KMS]
                                            │
                                            └──▶ [Auth (SSO/IdP)]    [관리자 콘솔]
```

- **외부 ↔ Edge**: 익명. TLS 종단 + WAF.
- **Edge ↔ App**: 인증된 사용자(JWT). 권한 평가는 App 내부.
- **App ↔ DB/S3**: 서비스 IAM 역할. **app 역할은 audit_log INSERT만**, UPDATE/DELETE 권한 없음 (CLAUDE.md §3 원칙 8).
- **Admin ↔ App**: 관리자 페이지는 동일 App이지만 권한이 `admin` preset 필요. 감사 작업도 별도 audit role 분리(§4).

### 1.3 STRIDE 위협 매트릭스

> 카테고리별 자산·위협·완화책. **"TBD"는 A 트랙에서 채움.**

#### Spoofing (위장)

| 위협 | 자산 | 완화책 | 상태 |
|---|---|---|---|
| 세션 토큰 탈취·재사용 | A5 | HTTP-only + Secure + SameSite=Lax 쿠키 / refresh rotation / IP·UA 변동 시 재인증 | 설계 |
| 비밀번호 추측 / credential stuffing | A5 | 로그인 실패 5회/15분 락아웃 + captcha · MFA 강제 (§2.1) | 설계 |
| 관리자 사칭 (역할 위조) | A3, A4 | 권한 평가는 항상 서버측 DB 조회, JWT 클레임 단독 신뢰 금지 | 설계 |
| presigned URL 도용 | A1 | 만료 ≤ 10분, IP/UA bind는 v1.x. 다운로드 자체를 audit (`file.downloaded`) | 설계 |

#### Tampering (변조)

| 위협 | 자산 | 완화책 | 상태 |
|---|---|---|---|
| 파일 본문 변조 | A1 | S3 versioning + ETag 검증 + 업로드 완료 트랜잭션(02 §6.1) | 설계 |
| DB 메타 직접 변조 | A2, A3 | DB 접근 IAM 최소권한 + 변경은 마이그레이션만 / app 역할은 trigger로 무결성 강제 | 설계 |
| 감사 로그 변조·삭제 | A4 | **DB 레벨 REVOKE UPDATE, DELETE** (02 §2.8, CLAUDE.md §3 원칙 8). admin role과 app role 분리. WORM 버킷 아카이빙(v1.x) | 설계 |
| Path traversal (`../`) | A1, A2 | storage_key는 UUID(03 §1 원칙 9). 사용자 제공 경로 직접 사용 금지. 정규화 후 화이트리스트 검증 | 설계 |
| 악성 파일 업로드 (XSS/RCE 페이로드) | A1 | 확장자 화이트리스트 + MIME magic 검증 (§5.3) + 다운로드 시 `Content-Disposition: attachment` + 별도 도메인 미리보기 | 설계 |

#### Repudiation (부인)

| 위협 | 자산 | 완화책 | 상태 |
|---|---|---|---|
| 사용자가 자신의 행위 부인 | A4 | 모든 변경 이벤트(`file.*`, `permission.*`, `share.*`) audit_log 기록 + actor·sessionId·IP 보존 | 설계 |
| 관리자 행위 부인 | A4 | `admin.*` 이벤트 별도 분류, audit role만 조회 가능 | 설계 |
| 토큰 탈취 후 위장 → "내가 안 했다" | A4, A5 | 세션별 sessionId를 audit에 함께 기록 → 사후 IP/UA로 감식 가능 | 설계 |

#### Information Disclosure (정보 노출)

| 위협 | 자산 | 완화책 | 상태 |
|---|---|---|---|
| 권한 없는 폴더 트리 enumeration | A2 | tree API는 effective_permissions 필터링 후 반환 (docs/01 §14) | 설계 |
| 검색을 통한 메타 누설 | A2 | 검색 결과도 권한 필터 후 반환. 검색어 자체는 audit 안 함(폭증) | 설계 |
| 직접 S3 객체 URL 추측 | A1 | UUID 키 + 버킷 public access 차단 + presigned only | 설계 |
| 다운로드 로그 누설 | A4 | audit 조회는 admin/auditor 권한 한정 | 설계 |
| 백업본을 통한 노출 | A7 | 백업 버킷 별도 IAM, KMS 분리 키, cross-region replication 시 액세스 로그 필수 | 설계 |
| 에러 메시지 leak (SQL/스택트레이스) | A2, A3 | 프로덕션은 generic 에러 메시지, 상세는 서버 로그에만. 에러 코드는 docs/02 §8 계약 | 설계 |

#### Denial of Service (서비스 거부)

| 위협 | 자산 | 완화책 | 상태 |
|---|---|---|---|
| 대용량 업로드로 스토리지 고갈 | A1 | 업로드 사이즈 한도 + 사용자/부서별 quota (docs/04 §13). 청크 업로드 abort cron | 설계 |
| 검색 N+1 / 깊은 트리 lazy 로딩 | A2 | tree API lazy 로딩(>10k 폴더), 검색 결과 페이지네이션 강제 | 설계 |
| 인증 엔드포인트 brute force | A5 | 락아웃 + IP rate limit + CAPTCHA. **TBD: A 트랙에서 RateLimiter 구현체 확정** | 부분 설계 |
| audit_log 폭증 | A4 | `audit_level` (folder별 standard/strict — §4.2) + 파티션 + 콜드 스토리지 | 설계 |

#### Elevation of Privilege (권한 상승)

| 위협 | 자산 | 완화책 | 상태 |
|---|---|---|---|
| 프론트 권한 우회 | 전체 | 프론트 권한은 UX용. **모든 파괴적 액션은 백엔드 재검증** (CLAUDE.md §3 원칙 10) | 설계 |
| 권한 상속 버그 (override 미적용) | A3 | 재귀 CTE + deny 우선 로직 + 단위 테스트 골든셋 (§3.3) | 설계 |
| 비활성 사용자 토큰 재사용 | A5 | 토큰 만료 + 사용자 비활성 시 즉시 revoke (logout-all 엔드포인트) | 설계 |
| MFA 미적용 관리자 계정 | A3 | admin/auditor role은 MFA 필수, 미설정 시 로그인 차단 | 설계 |
| 부서 LTREE 경계 우회 | A3 | LTREE prefix 기반 권한은 `<@` 연산자만 사용, 문자열 startsWith 금지 | 설계 |

### 1.4 잔여 위험 (Out of Scope, v1.x로 이월)

- **클라이언트 측 E2E 암호화**: v1.0은 KMS 기반 서버측 암호화. 사용자측 키는 미지원.
- **이상 행동 탐지 (UEBA)**: 비정상 다운로드 패턴 탐지는 v1.x.
- **소버린 클라우드**: 데이터 국외 이전 정책은 §8.2/§8.4 도메인별로 향후 결정.

---

## 2. 인증 (Authentication)

> **결정 근거**: ADR #11 (Spring Boot 3.x), ADR #12 (쿠키 세션 + Spring Session JDBC + CSRF double-submit),
> ADR #18~#22 (MVP 인증 범위·해싱·만료·사용자 등록·`/me` 응답).
> 이전 v3 초안의 JWT access + refresh rotation 모델은 ADR #12로 폐기됨.

### 2.1 인증 방식 (MVP)

- **MVP A1**: **자체 ID/PW only** (ADR #18). 이메일 + BCrypt 해시 비밀번호.
- **A1.5 (후속)**: 셀프 비밀번호 변경/리셋 (이메일 토큰).
- **v1.x**: SSO (SAML 2.0 또는 OIDC, 사내 IdP 페더레이션), MFA(TOTP).
- 사용자 출처는 단일 `users` 테이블. 향후 SSO 도입 시 `users.external_id` 컬럼 추가 예정 (마이그레이션).
- 사용자 등록은 **관리자 초대 only** (셀프 가입 금지, ADR #21). §2.8 참조.

### 2.2 세션 모델 (ADR #12)

| 항목 | 값 |
|---|---|
| 토큰 | **단일 불투명 sessionId** (JWT 없음, refresh 없음) |
| 발급 | Spring Security 로그인 성공 시 `HttpServletRequest.changeSessionId()` |
| 저장소 | **Postgres `SPRING_SESSION` 테이블** (Spring Session `JdbcIndexedSessionRepository`, Flyway V1이 schema 권위 — `initialize-schema: never`) |
| 쿠키 | `SESSION=<id>; HttpOnly; Secure; SameSite=Lax; Path=/` |
| CSRF | `XSRF-TOKEN` 쿠키 (HttpOnly **아님**, JS 읽기 가능) ↔ `X-CSRF-Token` 헤더 double-submit |
| 만료 | idle 30분 (sliding) + absolute 8시간 (issuedAt 검증) — ADR #20 |
| 강제 로그아웃 | `SessionRegistry.expireNow(sessionId)` — `SPRING_SESSION` row 즉시 DELETE |

- **JWT/refresh 없음**: 세션 검증은 `SPRING_SESSION` 테이블 lookup. 권한 평가는 DB(`PermissionService.check`).
- **세션 attribute 최소화**: `userId`, `roles`, `issuedAt`, `permissionsCacheKey`만. effectivePermissions는 DB·캐시 별도.
- **다중 인스턴스**: Spring Session JDBC 백엔드로 sticky session 불필요 (모든 인스턴스가 동일 Postgres 참조).
- **로그인 시 세션 ID 회전**: session fixation 방지 (`changeSessionId()` 호출).

### 2.3 시퀀스 — 로그인 (자체 ID/PW)

```text
브라우저                App 서버         Redis(loginfail*)    DB / SPRING_SESSION
   │ GET /api/auth/csrf  │                    │                  │
   │────────────────────▶│ generate XSRF      │                  │
   │ 200 { csrfToken }   │                    │                  │
   │ Set-Cookie:         │                    │                  │
   │   XSRF-TOKEN=...    │                    │                  │
   │◀────────────────────│                    │                  │
   │                     │                    │                  │
   │ POST /api/auth/login│                    │                  │
   │ X-CSRF-Token: ...   │                    │                  │
   │ { email, password } │                    │                  │
   │────────────────────▶│ email→lowercase    │                  │
   │                     │ check loginfail:E  │                  │
   │                     │───────────────────▶│                  │
   │                     │ if count >= 5      │                  │
   │                     │   → 423 LOCKED     │                  │
   │                     │ users.find(email)  │                  │
   │                     │─────────────────────────────────────▶ │
   │                     │ BCrypt.verify(pw)  │                  │
   │                     │ on fail:           │                  │
   │                     │   INCR loginfail:E │                  │
   │                     │   EXPIRE 900       │                  │
   │                     │───────────────────▶│                  │
   │                     │   audit: login.failed (DB)            │
   │                     │   ─────────────────────────────────▶ │
   │                     │ on success:        │                  │
   │                     │   DEL loginfail:E  │                  │
   │                     │───────────────────▶│                  │
   │                     │   changeSessionId()│                  │
   │                     │   session.set(...) (Spring Session)   │
   │                     │   ─────────────────────────────────▶ │ INSERT/UPDATE SPRING_SESSION
   │                     │   audit: login.success (DB)           │
   │                     │   ─────────────────────────────────▶ │
   │ 200 { user, ... }   │                    │                  │
   │ Set-Cookie:         │                    │                  │
   │   SESSION=newId     │                    │                  │
   │◀────────────────────│                    │                  │
```

> *Redis 컬럼은 §2.6 lockout 카운터 전용. MVP 인프라에는 Redis 미포함 — Redis 도입은 v1.x 별도 ADR로 분리(ADR #12 본문 참조). A1 구현 시 lockout 카운터의 1차 backing store를 결정해야 함 (옵션: 단일 인스턴스 한정 in-memory `ConcurrentHashMap` + idempotency 가능한 DB 카운터, 또는 Redis 선조정 ADR). 본 시퀀스는 정책상 목표 동작이며, 실제 backing은 A1 spec에서 확정.

**실패 응답 분기:**
- 자격 증명 불일치 → `401 UNAUTHORIZED { reason: 'INVALID_CREDENTIALS' }` + `INCR loginfail:{email}` + audit `user.login.failed(reason=invalid_credentials)`
- 5회 실패 후 → `423 ACCOUNT_LOCKED { retryAfterSec: <ttl> }` + audit `user.login.failed(reason=locked)`. 락 상태에서의 시도도 카운트 (TTL 갱신). 첫 락 진입 시 `user.locked` audit 추가 발급
- CSRF 토큰 누락/불일치 → `403 CSRF_MISMATCH`

> **에러 메시지 정책**: "이메일 없음" vs "비밀번호 불일치"를 구분하지 않음 (계정 enumeration 방지). 모두 `INVALID_CREDENTIALS`.

### 2.4 시퀀스 — 세션 만료 처리 (Refresh 없음)

```text
브라우저              App 서버              SPRING_SESSION (Postgres)
   │ GET /api/files    │                     │
   │ Cookie: SESSION=X │                     │
   │ X-CSRF-Token: ... │                     │
   │──────────────────▶│ session.find(X)     │
   │                   │────────────────────▶│  SELECT FROM SPRING_SESSION
   │                   │   ← null (만료/삭제)│
   │                   │ OR session.found    │
   │                   │   issuedAt + 8h     │
   │                   │   < now → expire    │
   │ 401 SESSION_EXPIRED                     │
   │ Set-Cookie:       │                     │
   │   SESSION=; Max-Age=0                   │
   │◀──────────────────│                     │
   │ → 로그인 화면     │                     │
```

- **Refresh 없음**: 세션 만료 = 재로그인. JWT/refresh 회전 모델 폐기 (ADR #12).
- 프론트는 401 응답 시 `useAuth` 훅이 `/login`으로 리다이렉트 (현재 path를 `?next=` 쿼리로 보존).
- idle 30분 sliding은 Spring Session이 자동 갱신. absolute 8시간은 `SessionAttributeFilter`에서 `issuedAt` 검증.

### 2.5 시퀀스 — 로그아웃

```text
브라우저              App 서버              SPRING_SESSION (Postgres)
   │ POST /api/auth/logout                  │
   │ X-CSRF-Token: ... │                     │
   │──────────────────▶│ session.invalidate()│
   │                   │────────────────────▶│ DELETE FROM SPRING_SESSION WHERE PRIMARY_ID=X
   │                   │ audit: user.logout  │
   │ 204               │                     │
   │ Set-Cookie:       │                     │
   │   SESSION=; Max-Age=0                   │
   │◀──────────────────│                     │
```

- **logout-all** (다른 기기 로그아웃)은 v1.x. MVP는 단일 세션만 지원.
- 강제 종료(관리자) — `POST /api/admin/users/:id/sessions/revoke` → A4 admin endpoint.

### 2.6 만료·잠금 정책 (ADR #20)

| 항목 | 값 | 구현 |
|---|---|---|
| **Idle timeout (sliding)** | 30분 | Spring Session `setMaxInactiveIntervalInSeconds(1800)` |
| **Absolute max lifetime** | 8시간 | session attribute `issuedAt` + 필터에서 `now - issuedAt > 8h` 검사, 초과 시 `invalidate()` |
| **계정 잠금 임계값** | 5회 연속 실패 | Redis `INCR loginfail:{email}` |
| **잠금 지속 시간** | 15분 | Redis `EXPIRE 900` (TTL). 락 상태 시도도 카운트되어 TTL 갱신 |
| **카운터 리셋** | 로그인 성공 시 | Redis `DEL loginfail:{email}` |

- 클라이언트 idle 타이머는 도입하지 않음 (서버 401 응답으로 충분).
- 잠금 해제는 (a) TTL 만료 자동 + (b) 관리자 수동 해제 (`POST /api/admin/users/:id/unlock`, A4).
- MFA 재인증(민감 작업) 정책은 v1.x MFA 도입과 함께 재정의.

### 2.7 비밀번호 정책·해싱 (ADR #19)

> **Closure (auth-password-policy, 2026-05-04)** — ADR #41 §2.8(self-signup MVP) 임시 완화 min=8 → ADR #19 본문 회복. 본 §2.7 5규칙이 단일 진실의 출처. 구현: backend `auth.validation.PasswordPolicyValidator` + `@ValidPassword` (3 DTO 적용), `AuthExceptionHandler.ruleOf`가 ValidPassword violation을 rule code로 변환. Frontend `lib/password.ts` 미러 (`validatePassword` + `getPasswordRuleMessage`) — 핵심 원칙 11(FE/BE 동일 로직). 3 페이지(`/signup`·`/reset-password`·`/account/password`)가 사전검증 후 한국어 rule 메시지 노출. `TempPasswordGenerator`는 영문/숫자 강제 주입 + Fisher-Yates shuffle로 정책 회귀 가드(200회 sample 테스트).

**해싱:**
- `BCryptPasswordEncoder(strength=12)` — Spring Security 기본.
- DB 저장 컬럼: `users.password_hash VARCHAR(100)` — `DelegatingPasswordEncoder` 프리픽스(`{bcrypt}` ~9자 + 60자) 및 향후 `{argon2id}` 마이그레이션 여유.
- `DelegatingPasswordEncoder` 사용: 향후 Argon2id 마이그레이션 가능 (`{bcrypt}...` / `{argon2}...` 프리픽스).

**정책 (등록·변경 시 검증):**

| 규칙 | 값 | 에러 코드 |
|---|---|---|
| 최소 길이 | 12자 | `VALIDATION_ERROR { rule: 'min_length' }` |
| 영문자 1자 이상 | 필수 | `VALIDATION_ERROR { rule: 'missing_alpha' }` |
| 숫자 1자 이상 | 필수 | `VALIDATION_ERROR { rule: 'missing_digit' }` |
| 공백 문자 금지 | 필수 | `VALIDATION_ERROR { rule: 'whitespace' }` |
| 최대 길이 | 128자 | `VALIDATION_ERROR { rule: 'max_length' }` |

- **사전 공격 방지(zxcvbn/HIBP)**: 도입 안 함 (외부 의존·UX 부담) → v1.x.
- **이메일 정규화**: `email = email.trim().toLowerCase()` (별도 로직, `NormalizeUtil.normalize()` 적용 대상 아님 — 파일/폴더명 전용).

**비밀번호 분실/재설정/변경 (a1.5, ADR #42 + #43, 2026-05-02 closure)**

3개 endpoint:

| 메서드 | 경로 | 인증 | CSRF | 동작 |
|---|---|---|---|---|
| `POST` | `/api/auth/password/forgot` | 비로그인 | 면제 | 가입자 → 토큰 생성 + 메일 발송 / 미가입자 → no-op. 두 경우 모두 200 동일 응답 (anti-enumeration). **email + IP 분당 1회 rate-limit (ADR #44)** — 초과 시 429 + `Retry-After` |
| `POST` | `/api/auth/password/reset` | 비로그인 (token 인증) | 면제 | 토큰 hash 매칭 + TTL/used 검사 → PW 갱신 + **모든 세션 invalidate** |
| `POST` | `/api/auth/password/change` | 인증 필수 | 필수 | 현재 PW BCrypt 검증 → 새 PW 갱신 + **현재 세션 보존**, 다른 세션만 invalidate |

토큰 정책 (`password_reset_tokens` 테이블, V8):

- **저장** = SHA-256 hex (64자), 평문은 메일 본문에만.
- **TTL** = 30분 (`expires_at = created_at + 30m`).
- **1회 사용** = `used_at` set 후 동일 토큰은 INVALID_TOKEN.
- **다중 토큰 허용** — 같은 사용자 forgot 여러 번 호출 시 모든 active 토큰 유효 (race/UX 부담 회피).
- **사유 비공개** — 만료/사용됨/미존재 모두 400 INVALID_TOKEN 단일 코드 (token enumeration 방지).
- **rate-limit (ADR #44)** — forgot endpoint은 email + IP 분당 1회. 초과 시 429 + `Retry-After`. in-memory single-instance (login 트래커와 동일 패턴, ADR #23 mirror). reset/change는 이미 토큰/세션 가드로 보호되므로 미적용.

세션 무효화 정책 차이:

- **reset**: PW 변경이 기존 모든 세션을 무효화 — `FindByIndexNameSessionRepository.findByPrincipalName(email)` → `deleteById` loop, `keepSessionId=null`.
- **change**: 현재 세션은 보존하여 사용자가 강제 로그아웃되지 않음. 다른 기기/세션은 모두 invalidate, `keepSessionId=session.getId()`.

이메일 인프라 (ADR #42, ADR #45):

- `EmailService` 인터페이스 + profile 분기 — `ConsoleEmailService(@Profile("!prod"))`는 stdout 로깅 (dev/test SMTP 의존성 제거), `SmtpEmailService(@Profile("prod"))`는 `JavaMailSender` 위임.
- **`@Async("emailExecutor")` fire-and-forget (ADR #45)** — `EmailService.send()` 호출 시점에 caller 스레드는 즉시 반환되며 실제 SMTP 발송은 `emailExecutor`(corePool=2 / queue=100, prefix `email-async-`) 풀에서 백그라운드 수행. 발송 실패는 `SmtpEmailService` 내부 ERROR 로그로 흡수 — caller(`PasswordResetService.requestReset`)에 어떤 형태로도 도달하지 않음. `EmailDeliveryException` throw 제거(클래스 자체는 보존).
- HTML 템플릿/i18n은 v1.x 분리. 외부 큐(SQS/Kafka)는 in-process executor로 충분(MVP 트래픽 가정).
- **Anti-enumeration timing leak (ADR #42 한계 → ADR #45에서 완화)** — 동기 발송 시 가입자만 SMTP 라운드트립이 발생해 caller latency가 다르던 timing side channel을 ADR #45 비동기화로 제거. 가입자/미가입자 caller latency가 동일(DB lookup + token hash + audit emit 한도). 통합 테스트(`EmailAsyncIntegrationTest`)가 stub의 200ms sleep과 무관하게 caller < 50ms 임계를 강제해 회귀 차단.

**강제 비밀번호 변경 UX (auth-must-change-pw 트랙, ADR #21 잔여 closure, 2026-05-03)**

`User.must_change_password=true` 사용자가 explorer를 사용하기 전에 반드시 `/account/password`를 거치도록 프론트/백엔드 양쪽에서 enforce.

- **백엔드 — 플래그 클리어**: `PasswordResetService.change()`와 `reset()`이 PW hash 갱신 직후 `User.clearMustChangePassword()`를 호출해 단일 트랜잭션 내에서 `must_change_password=false`로 영속화. 이 클리어가 없으면 프론트 enforce가 무한 redirect 루프를 유발.
- **프론트 — LoginPage redirect**: `me.user.mustChangePassword=true`인 사용자가 로그인하거나 로그인된 상태로 `/login`을 열면 `next` query를 무시하고 `/account/password?force=1`로 `router.replace`.
- **프론트 — AuthGuard guard**: `(explorer)` 라우트 그룹의 `AuthGuard`가 매 navigation마다 `usePathname()`으로 위치를 확인. 로그인 사용자라도 `mustChangePassword=true && pathname !== '/account/password'`이면 `/account/password?force=1`로 bounce. URL 직접 입력 우회 차단. `/account/password` 자체에서는 통과 (무한 루프 회피).
- **프론트 — `/account/password` force UI**: `?force=1`이면 amber 배너 (`role=alert`, "관리자가 비밀번호 변경을 요청했습니다 …") 표시 + "돌아가기" 버튼 hide + 변경 성공 시 `router.replace('/files')`.
- **프론트 — `usePasswordChange` invalidation**: `onSuccess`에서 `qk.authMe()` invalidate → AuthGuard가 fresh state(false)로 재평가 → bounce 없이 `/files` 통과.
- **audit 변동 없음**: `USER_PASSWORD_CHANGED`/`USER_PASSWORD_RESET`이 이미 emit. 플래그 변화에 별도 이벤트 추가 안 함.

운영자 초대(`POST /api/admin/users` invite-by-email)는 m-admin-entry-rewrite 트랙(2026-05-03)에서 활성화 완료 — §2.8 참조. 초대 사용자는 `must_change_password=true`로 생성되며 첫 로그인 시 본 절의 force UI로 진입한다.

### 2.8 사용자 등록 (ADR #21 → ADR #41 supersede, 2026-05-02 / m-admin-entry-rewrite closure 2026-05-03)

> **Status**: ADR #21(관리자 초대 only)은 ADR #41 auth-pages 트랙에서 supersede. MVP는 self-signup + first-user-ADMIN 부트스트랩 + **운영자 초대(`POST /api/admin/users`) 활성화 완료(2026-05-03, m-admin-entry-rewrite 트랙, ADR #21 잔여 closure)**. 두 경로(self-signup / admin invite) 모두 운영 가능.

- **MVP = 셀프 가입**. `POST /api/auth/signup` 활성화 (request: `{email, password, displayName}`, response: `LoginResponse` shape + auto-session).
- **first-user-ADMIN 부트스트랩**: `userRepository.count() == 0`이면 새 사용자 ROLE=ADMIN, 그 외 MEMBER. 빈 DB 첫 호출만 ADMIN 부여(초기 admin 시드 의존성 제거). 동시 두 요청 race는 MVP single-instance + tx 직렬화로 사실상 차단(엄밀 보장은 advisory lock — v1.x).
- **CSRF**: `/api/auth/signup`은 CSRF token 미요구(`csrf().ignoringRequestMatchers` + `permitAll()`). 첫 호출 token preflight 비용/UX 마찰 회피. 로그인/로그아웃은 §2.2 그대로 double-submit.
- **자동 세션**: signup 성공 = `AuthService.establishSession`(login 공통 helper, `changeSessionId()` 호출) → `LoginResponse` 반환. 가입 후 즉시 `/files`.
- **Validation (Bean Validation)**:
  - `email`: `@NotBlank @Email @Size(max=254)` — trim+lowercase 후 저장 (`users.email CITEXT` UNIQUE 의존).
  - `password`: `@NotBlank @ValidPassword` — §2.7 ADR #19 본문 회복 (auth-password-policy 트랙, 2026-05-04 closure). 5규칙(`whitespace`/`max_length`/`min_length`/`missing_alpha`/`missing_digit`) 우선순위로 거부, rule code는 fieldErrors[].rule로 노출.
  - `displayName`: `@NotBlank @Size(min=1, max=100)` — trim 후 저장.
- **에러 envelope (auth flat)**:
  - `409 CONFLICT/DUPLICATE_EMAIL` — 이미 가입된 이메일.
  - `400 VALIDATION_ERROR` — Bean Validation 실패 (standard envelope).
- **Audit emission**: `USER_REGISTERED("user.registered")` (§4.1 추가). `UserRegisteredEvent` record + `AuthAuditListener.onRegistered`가 `@EventListener` REQUIRES_NEW로 audit_log 기록 — 로그인 `AuthenticationSuccessEvent` 패턴 일관.
- **운영자 user 초대 (`POST /api/admin/users`) — 활성화 완료 (m-admin-entry-rewrite, 2026-05-03)**:
  - **Endpoint**: `POST /api/admin/users` — `@PreAuthorize("hasRole('ADMIN')")`. Request `{email, displayName, role}` (Bean Validation: `@Email @NotBlank` email, `@NotBlank @Size(max=100)` displayName, `@NotNull` role). 자세한 wire 계약은 docs/02 §7.12.
  - **임시 PW 생성**: `TempPasswordGenerator` (16자, `SecureRandom`, alphabet `[A-Za-z0-9]` + 소량 특수문자) → `BCryptPasswordEncoder(strength=12)` hash로 `users.password_hash` 저장 + `must_change_password=true`. raw 임시 PW는 메모리에만 일시 보유 후 EmailService로 전달.
  - **임시 PW 비노출 정책 (4채널 전수 차단)**:
    1. **응답 DTO**: `AdminInviteUserResponse`는 `{id, email, displayName, role, mustChangePassword}`만. tempPassword/password/passwordHash 필드 부재 (회귀 가드 — 컨트롤러 테스트 `jsonPath("$.tempPassword").doesNotExist()`).
    2. **로그**: 서비스/컨트롤러/이벤트 리스너에서 raw 임시 PW를 INFO/DEBUG 로그에 기록하지 않는다 (메일 본문 전달 시점에만 노출).
    3. **Audit**: `audit_log.payload`에 임시 PW 평문/해시 모두 기록하지 않는다 (`admin.user.created` 이벤트는 actor/target/email만).
    4. **Exception 메시지**: `DuplicateEmailException` 등 어떤 예외에도 임시 PW를 포함하지 않는다.
  - **이메일 발송**: `EmailService.sendInviteEmail(email, rawTempPassword)` — `ConsoleEmailService(@Profile("!prod"))` stdout, `SmtpEmailService(@Profile("prod"))` SMTP 위임. Prod SMTP 도입은 v1.x 인프라 트랙.
  - **Audit emission**: `ADMIN_USER_CREATED("admin.user.created")` (§2.10 + §4.1). `AdminUserCreatedEvent` record + `AdminAuditListener.onCreated`가 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `Propagation.REQUIRES_NEW`로 audit_log 기록 — 같은 트랜잭션 commit 보장 후 별도 tx에서 emit (login `AuthAuditListener` 패턴 일관).
  - **첫 로그인 흐름**: 초대된 사용자가 임시 PW로 로그인 → `me.user.mustChangePassword=true` → §2.7 force UX가 `/account/password?force=1`로 redirect → PW 변경 성공 시 `clearMustChangePassword()` + 모든 다른 세션 invalidate → `/files` 진입.
  - **에러 envelope (auth flat)**:
    - `409 CONFLICT/DUPLICATE_EMAIL` — 이미 가입된 이메일.
    - `400 VALIDATION_ERROR` — Bean Validation 실패 (standard envelope).
    - `401` — 미인증 (Spring Security entry point).
    - `403` — ADMIN role 아님 (`@PreAuthorize` 차단, 본문 없음).
  - **프론트 진입점 (m-admin-entry-rewrite)**: `/admin/users` 페이지 (`AuthGuard` + `AdminGuard` 중첩) — 폼 제출 시 `useAdminInviteUser` mutation 호출. 성공 시 "초대 메일을 발송했습니다" 안내 (PW 자체는 어떤 형태로도 노출하지 않음). 409 → 인라인 "이미 등록된 이메일입니다." 메시지.

`users` 테이블 인증 관련 컬럼 (docs/02 §2.1과 동기):
- `email VARCHAR UNIQUE NOT NULL` (lowercase 정규화)
- `password_hash VARCHAR(72) NOT NULL`
- `must_change_password BOOLEAN NOT NULL DEFAULT false`
- `kind ENUM('human', 'service') NOT NULL DEFAULT 'human'`

### 2.9 서비스 계정 (Service Account)

- 배치/통합용 별도 계정. `users.kind='service'`로 구분.
- **세션 쿠키 대신 장기 API 키** (저장 시 hash). 키 회전은 관리자 콘솔 — `admin.api_key.rotated` audit.
- 모든 행위는 사람과 동일하게 audit. service account 우대 없음.
- **MVP 범위 외**: A4 admin 트랙에서 별도 spec. MVP A1은 human 계정만.

### 2.10 인증 관련 audit 이벤트 (§4.1과 동기화)

| 이벤트 | trigger | reason 필드 값 |
|---|---|---|
| `user.registered` | 셀프 가입 성공 (ADR #41) | — |
| `user.login.success` | 로그인 성공 | — |
| `user.login.failed` | 로그인 실패 | `invalid_credentials` / `locked` / `csrf_mismatch` |
| `user.logout` | 명시적 로그아웃 | — |
| `user.session.expired` | idle/absolute timeout | `idle` / `absolute` |
| `user.password.changed` | 비밀번호 변경 (A1.5 P5, ADR #43) | — |
| `user.password.forgot_requested` | 비밀번호 분실 토큰 발급 + 메일 발송 (A1.5 P3, ADR #43) — 가입자에 한해 발생 (anti-enumeration) | — |
| `user.password.reset` | 토큰 + 새 PW로 재설정 성공 (A1.5 P4, ADR #43) | — |
| `user.locked` | 5회 실패 후 락 진입 | — |
| `user.unlocked` | 관리자 수동 해제 (A4) | `admin_action` |
| `admin.user.created` | 운영자 초대로 신규 user 생성 (m-admin-entry-rewrite, 2026-05-03) — actor=ADMIN, target=신규 user, AFTER_COMMIT REQUIRES_NEW emit | — |

> §4.1 `AuditEventType` enum에 위 이벤트 동기화 필요 (현재 `user.password.changed` / `user.mfa.enabled`만 존재). MFA는 v1.x로 연기되어 `user.mfa.enabled`는 enum 유지(미사용)하거나 v1.x 도입 시점에 추가 가능 — TBD: A1 구현 진입 시 §4.1 정리.

---

## 3. 권한 모델 (단일 진실 출처)

### 3.1 권한 enum (Permission)

> ADR #17: 백엔드 권위. 프론트 `src/types/permission.ts`는 1:1 미러 (UX용 게이트만, 보안 아님).

| 권한 | 의미 | 체크 지점 (대표 endpoint) |
|---|---|---|
| `READ` | 메타/내용 조회 | GET `/api/folders/:id`, GET `/api/files/:id`, tree, search 결과 필터 |
| `UPLOAD` | 폴더에 새 파일 추가 | POST `/api/files/upload`, finalize |
| `EDIT` | 메타/이름/버전 변경 | PATCH `/api/folders/:id`, PATCH `/api/files/:id`, POST versions |
| `MOVE` | 자기 자신을 이동 | POST `/api/folders/:id/move`, POST `/api/files/:id/move` |
| `DOWNLOAD` | 다운로드 | GET `/api/files/:id/download` |
| `DELETE` | 휴지통으로 이동 (soft delete) + 복원 | DELETE `/api/folders/:id`, POST `/api/folders/:id/restore`, 동일한 file/trash endpoint |
| `SHARE` | 내부 공유 (subject = user/department/role/everyone, ADR #34) | POST `/api/files/:id/share`, POST `/api/folders/:id/share` (A12) |
| `PERMISSION_ADMIN` | 권한 부여/회수 | POST/DELETE `/api/:resource/:id/permissions` |
| **`PURGE`** | **영구 삭제 (소프트 → 완전 제거)** | **DELETE `/api/trash/:id`, DELETE `/api/trash`** |
| **`MANAGE_LEGAL_HOLD`** *(v2.x — ADR #46)* | **Legal Hold 지정/해제** | **POST/DELETE `/api/admin/legal-holds/...`** *(v2.x deferred)* |

> `PURGE`는 회복 불가 액션이므로 별도 권한으로 분리. **시스템 ROLE `ADMIN`만 보유** (§3.2.5 참조). `DELETE` 권한이 있어도 `PURGE`는 자동 부여되지 않음.
>
> `MANAGE_LEGAL_HOLD`는 **시스템 ROLE `ADMIN`만 보유** (PURGE 일관). active hold **조회**는 `AUDITOR`도 가능(read-only). dual-approval 게이트(§6.3) 활성 환경에서는 release 시 secondary admin 별도 승인 필요.

### 3.2 Preset × 권한 매트릭스

| Preset | READ | UPLOAD | EDIT | MOVE | DOWNLOAD | DELETE | SHARE | PERMISSION_ADMIN | PURGE |
|---|---|---|---|---|---|---|---|---|---|
| **read** | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **upload** | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **edit** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (자기 것) | ❌ | ❌ | ❌ |
| **share** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (자기 것) | ✅ | ❌ | ❌ |
| **admin** *(노드 admin)* | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (전체) | ✅ | ✅ | ❌ |
| **system_admin** *(role)* | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (전체) | ✅ | ✅ | ✅ |

> `PURGE`는 **노드 admin preset에도 부여하지 않음** — 시스템 전역 ROLE `ADMIN`만 가능. 이중 안전장치: 노드 단위 권한 위임이 영구 삭제로 번지지 않도록.

#### 3.2.5 시스템 ROLE

| ROLE | 보유 권한 |
|---|---|
| `MEMBER` | 노드별 preset만 적용 (별도 가산 없음) |
| `AUDITOR` | 모든 노드에 `READ` (감사 페이지 한정 — `/api/admin/*` 일부) + audit_log 조회 |
| `ADMIN` | 전 노드 admin preset + **`PURGE`** + 사용자 관리 (`PATCH /api/admin/users/:id`) + **`MANAGE_LEGAL_HOLD`** *(v2.x — ADR #46)* |

### 3.3 Subject 유형

- `user`: 개인 (`users.id`)
- `department`: 부서 (`departments.id`). A16(ADR #37)에서 도메인 도입 — V7 마이그레이션 + `users.department_id` FK. **MVP 매칭은 flat** (LTREE 후손 자동 포함 미사용, v1.x deferred).
- `role`: 시스템 역할 (`MEMBER` / `AUDITOR` / `ADMIN`) — §3.2.5
- `everyone`: 전사

### 3.4 권한 상속 & 평가

- 폴더 → 자식 폴더/파일로 상속 (default)
- 자식에 명시적 권한 정의 시 → override
- 계산 로직: 재귀 CTE로 root까지 순회, **deny 우선** (deny가 한 번이라도 나오면 최종 deny) — *(v1 deferred — preset 단일 컬럼만 도입, 명시 deny semantics는 v1.x 이월. ADR #28 참조. v1 evaluator는 explicit grant lookup = "grant 우선"으로 동작.)*
- `PermissionService.check(userId, resource, resourceId, permission)`이 단일 진입점.
- **Subject 매칭 (PermissionRepository.findEffective, A16 ADR #37 dept 분기 추가)**: 단일 SQL이 아래 OR 분기로 effective grant 집합을 반환:
  - `subject_type='user'   AND subject_id=:userId`
  - `subject_type='everyone' AND subject_id IS NULL`
  - `subject_type='role'   AND subject_id::text = :role` (role-grant)
  - `subject_type='department' AND subject_id = (SELECT department_id FROM users WHERE id=:userId AND is_active)` *(A16 추가)*
  - `users.department_id` NULL → dept 매칭 unmatched(false). 비활성(`is_active=FALSE`) → 동일.
  - dept 후손 자동 포함은 v1.x deferred (LTREE descendant join).

### 3.5 권한 매트릭스 (엔드포인트 × 권한)

각 endpoint의 권한 요구는 **`docs/02-backend-data-model.md §7.4~§7.14` Guard 컬럼**에 명시 (단일 진실 출처). 본 문서는 권한 enum과 preset 정의만 담당, endpoint 매핑은 02 문서 참조.

> **사용자 검색 (`/api/users/search`, ADR #35)**: `isAuthenticated()` 공개 — 사용자 명단 자체가 trust boundary 내부 정보(ADR #18 admin-invitation only 등록). share subject picker가 ADMIN 외 EDIT 보유자에게도 노출되어야 하므로 ROLE 가드 부적절.
>
> **부서 검색 (`/api/departments/search`, ADR #37)**: 동일 정책 — `isAuthenticated()` 공개. 부서 명단도 trust boundary 내부 정보. A14와 동형.

`@PreAuthorize` 표현식 패턴:

```java
// 단일 권한
@PreAuthorize("hasPermission(#id, 'folder', 'READ')")
public FolderDto getFolder(@PathVariable String id) { ... }

// 복합 권한 (이동: 양쪽 모두)
@PreAuthorize(
  "hasPermission(#id, 'file', 'MOVE') and "
  + "hasPermission(#req.targetFolderId, 'folder', 'EDIT')"
)
public FileDto moveFile(@PathVariable String id, @RequestBody MoveRequest req) { ... }

// PURGE는 ROLE 검사 (노드 권한 무시)
@PreAuthorize("hasRole('ADMIN')")
public void purge(@PathVariable String id) { ... }
```

### 3.6 403 응답 기준

- `PermissionService.check`가 false 반환 → `403 PERMISSION_DENIED` (`docs/02 §8`)
- 응답 body: `{ error: { code: 'PERMISSION_DENIED', message, details: { required: ['EDIT'], have: ['READ'] } } }`
- 프론트 핸들러 → 토스트 + `effectivePermissions` 재조회 (캐시된 권한이 stale일 수 있음)

---

## 4. 감사 정책 (Audit Policy)

### 4.1 감사 이벤트 타입

> **클라이언트 mirror**: `frontend/src/types/audit.ts`의 `AuditEventType`이 아래 enum의 1:1 미러.
> 새 이벤트 추가 시 양쪽 동시 갱신 (CLAUDE.md §4 계약 파일).

```ts
type AuditEventType =
  // 파일
  | 'file.viewed'        // strict audit_level 폴더만
  | 'file.downloaded'
  | 'file.uploaded'
  | 'file.renamed'
  | 'file.moved'
  | 'file.deleted'
  | 'file.restored'
  | 'file.purged'
  // 버전
  | 'version.created'
  | 'version.restored'
  | 'version.downloaded'
  // 폴더
  | 'folder.created'
  | 'folder.renamed'
  | 'folder.moved'
  | 'folder.deleted'
  | 'folder.restored'
  | 'folder.purged'        // A8 manual purge (DELETE /api/trash/folder/:id, ADR #32)
  | 'folder.audit_level_changed'
  // 권한 / 공유
  | 'permission.granted'
  | 'permission.revoked'
  | 'permission.expired'   // 활성화 (`permissions-expired-cron`, 2026-05-01) — actor_id=NULL, metadata.trigger='system.expiration', docs/02 §7.10.1
  | 'permission.changed'
  | 'share.created'        // A10 활성화 (`a10-shares`)
  | 'share.revoked'        // A10 활성화 (`a10-shares`)
  | 'share.expired'        // 활성화 (`share-expired-cron`, 2026-05-01) — actor_id=NULL, metadata.trigger='system.expiration', docs/02 §7.9.1
  // 인증
  | 'user.registered'      // ADR #41 활성화 (auth-pages, 2026-05-02) — 셀프 가입 성공
  | 'user.login.success'
  | 'user.login.failed'
  | 'user.logout'
  | 'user.password.changed'           // A1.5 P5 활성화 (`a1.5-email-infra`, 2026-05-02, ADR #43)
  | 'user.password.forgot_requested'  // A1.5 P3 활성화 (`a1.5-email-infra`, 2026-05-02, ADR #43) — 가입자만 emit (anti-enumeration)
  | 'user.password.reset'             // A1.5 P4 활성화 (`a1.5-email-infra`, 2026-05-02, ADR #43)
  | 'user.mfa.enabled'
  // 관리자
  | 'admin.user.created'
  | 'admin.user.updated'
  | 'admin.user.deactivated'
  | 'admin.role.changed'
  | 'admin.quota.changed'
  | 'admin.legal_hold.placed'         // v2.x — ADR #46 (placeholder enum 활성화)
  | 'admin.legal_hold.released'       // v2.x — ADR #46 (placeholder enum 활성화)
  | 'admin.legal_hold.expired'        // v2.x — ADR #46 신규 (expiration cron, actor_id=NULL)
  | 'admin.legal_hold.violation_blocked' // v2.x — ADR #46 신규 (차단된 mutation 시도, actor=시도자)
  | 'admin.department.created'      // Wave 2 T4 활성화 (admin-department-crud, 2026-05-06) — POST /api/admin/departments
  | 'admin.department.updated'      // Wave 2 T4 활성화 — PATCH (rename + reactivate 흡수, before/after JSON)
  | 'admin.department.deactivated'  // Wave 2 T4 활성화 — PATCH isActive=false (제재 분기)
  | 'admin.cron.toggled'            // admin-cron-policy-toggle 활성화 (2026-05-08) — PUT /api/admin/system/cron/{key}, metadata={key, fromEnabled, toEnabled}
  // 시스템
  | 'system.backup.completed'
  | 'system.purge.executed'
  | 'storage.orphan.cleaned'  // 활성화 (`storage-orphan-cleanup`, 2026-05-02, ADR #38) — actor_id=NULL, target_type=system, metadata={runId,scanned,candidates,deleted,failed,truncated,durationMs}, docs/02 §5.6
  // 감사 로그 자체
  | 'audit.exported'   // docs/04 §7.2 — CSV/JSON 내보내기 자체도 감사 기록
```

### 4.2 감사 레벨 정책

```text
folders.audit_level:
  'standard': upload/download/move/delete/permission 이벤트만 기록
  'strict':   위 + view/preview 이벤트도 기록

→ 관리자가 민감 폴더에 audit_level='strict' 지정
→ 일반 폴더는 view 로그 생략 (폭증 방지)
```

### 4.3 감사 로그 보존

- [ ] 기본 보존 기간: 3년 (도메인에 따라 조정)
- [ ] Legal Hold 대상: 무기한
- [ ] 파티션별 아카이빙 (cold storage 이전)

### 4.4 감사 로그 불변성 강제

- [ ] DB 레벨 REVOKE UPDATE, DELETE (02 문서 §2.8)
- [ ] application role과 admin role 분리
- [ ] 파티션 분리 + WORM storage (v1.x)

---

## 5. 저장소 보안

### 5.1 전송 구간

> **MVP 결정**: 전송 구간 보안은 운영 인프라(리버스 프록시 / ALB / Cloudfront)에서 종단. Spring Boot는 HTTPS 직접 처리 안 함. 베타 출시 절차는 `BETA-RELEASE.md`.

- [ ] TLS 1.2+ 전체 강제 — *운영 (인프라 책임 — `BETA-RELEASE.md` 게이트)*
- [ ] HSTS 헤더 — *운영 (리버스 프록시 또는 CDN 설정)*
- [ ] presigned URL 사용 시 짧은 만료 (10분 이내) — *v1.x deferred (S3 미도입, MVP는 직접 GET `/api/files/{id}/download`)*

### 5.2 저장 구간

> **MVP 결정**: LocalFsStorageClient만 구현 (`ibizdrive.storage.type=local`). S3/KMS는 v1.x.

- [ ] S3 서버 사이드 암호화 (SSE-S3 또는 SSE-KMS) — *v1.x deferred (S3 미도입)*
- [ ] KMS 키 로테이션 주기: 1년 — *v1.x deferred (KMS 미도입)*
- [ ] 버킷 public access 차단 — *v1.x deferred (S3 미도입)*

### 5.3 악성 파일 대응

> **MVP 결정 (mvp-qa-security Phase 3)**: 사내 베타에서는 §5.4 `Content-Disposition: attachment`(구현됨, FileDownloadController:76-114) + `X-Content-Type-Options: nosniff`(SecurityConfig 명시화)로 1차 방어.
> 직접 렌더링 차단 → XSS 페이로드는 다운로드 후 사용자 명시 실행이 필요. 확장자 화이트리스트 / MIME magic / AV 스캔은 외부 출시 시점 v1.x.

- [ ] 확장자 화이트리스트 (02 문서 §5.4) — *v1.x deferred (외부 출시 시 도입)*
- [ ] MIME magic number 검증 — *v1.x deferred (외부 출시 시 도입)*
- [ ] 바이러스 스캔 (v1.x) — *v1.x deferred*
- [ ] 스캔 실패 시 다운로드 경고 — *v1.x deferred (스캔 도입 후)*

### 5.4 다운로드 보안

- [x] Content-Disposition: attachment 강제 — `FileDownloadController:76-114` RFC 5987 인코딩 포함 (A15 closure)
- [x] `X-Content-Type-Options: nosniff` — `SecurityConfig.headers().contentTypeOptions()` 명시 (mvp-qa-security Phase 3)
- [ ] HTML/SVG 직접 렌더링 금지 (샌드박스 iframe 또는 별도 도메인) — *v1.x deferred (별도 미리보기 도메인은 인프라 트랙)*

---

## 6. 데이터 보호 (Data Protection)

### 6.1 개인정보 처리

> **MVP 결정**: 사내 베타 — 사내 정보보호 정책으로 대체. 외부 출시 시 별도 정책/약관 트랙.

- [ ] 개인정보보호법 준수 (한국) — *운영 (사내 베타 → 사내 정책 / 외부 출시 시 본문화)*
- [ ] 처리 목적 / 보존 기간 / 제3자 제공 정책 명시 — *운영 (정책/약관 책임)*
- [ ] 사용자 탈퇴 시 처리 방침 (파일 소유권 이관 vs 삭제) — *v1.x deferred (현재 비활성화만 지원, 탈퇴 UI 미구현)*

### 6.2 백업

> **MVP 결정**: managed Postgres / RDS 백업으로 대체. 절차는 `BETA-RELEASE.md` 운영 게이트.

- [ ] DB: 일일 스냅샷 + PITR (7일) — *운영 (managed Postgres / RDS 책임)*
- [ ] S3: Cross-region replication + 버전 관리 — *v1.x deferred (S3 미도입)*
- [ ] 감사 로그: 별도 버킷 + WORM — *v1.x deferred (S3 미도입). MVP는 DB-level append-only(`V4__audit_log_revoke.sql`)로 1차 무결성 보장*

### 6.3 법적 보존 (Legal Hold)

> **Status: v2.x deferred** (docs/00 §4.3, ADR #46). 본 절은 **설계 명세 — 코드 0줄**. v2.x 진입 시 `dev/active/legal-hold-design/` plan 그대로 실행.

#### 6.3.1 정책 개요

Legal Hold = 법적 분쟁/감사/컴플라이언스 사유로 특정 자료의 변경·삭제를 차단하는 보존 락. 일반 권한/휴지통 정책 위에 얹는 **상위 게이트**:

- 일반 권한: "누가 무엇을 할 수 있는가"
- Legal Hold: "권한과 무관하게 무엇을 막는가"

설계 원칙:

1. **읽기/다운로드/조회는 허용** — 보존 목적상 access trail 자체는 정상 유지 (`READ`/`DOWNLOAD`/`VIEW` 모두 통과)
2. **변경/삭제는 거부 + 423 LOCKED** — UI/API 양쪽 가드, 백엔드가 진실의 출처
3. **메타 풍부 + cache flag** — ADR #46 하이브리드 모델 (사유/만료/승인자 + hot path 1줄 검사)
4. **Audit append-only** — place/release/expire/violation_blocked 4종 신규 (placeholder 2종 활성화 + 신규 2종)

#### 6.3.2 데이터 모델 (요약)

→ docs/02 §2.10 `legal_holds` 테이블 + `files.legal_hold`/`folders.legal_hold` cache flag (ADR #46).

cascade 의미:

| target_type | cascade 범위 |
|---|---|
| `file` | 해당 file row만 |
| `folder` | 해당 folder + 모든 후손 folder + 모든 후손 file (시점 스냅샷, 신규 업로드 file은 ancestor hold 검사 후 자동 set) |
| `user` | 해당 user 소유의 모든 file/folder (owner_id 매칭) |

#### 6.3.3 차단 액션 매트릭스

`legal_hold = TRUE`인 file/folder가 대상이면 아래 mutation은 모두 **423 `LEGAL_HOLD_VIOLATION`**:

| 액션 | endpoint | 차단 |
|---|---|---|
| 휴지통 이동 (soft delete) | `DELETE /api/files/:id`, `DELETE /api/folders/:id` | ✅ |
| 복원 | `POST /api/{...}/restore` | ✅ (휴지통 잔존 row의 hold도 차단) |
| 영구 삭제 (manual purge) | `DELETE /api/trash/:id` | ✅ |
| 영구 삭제 (cron) | `HardPurgeService` | ✅ — `WHERE legal_hold IS NOT TRUE` 1줄 (ADR #31 forward-ref) |
| 신규 버전 업로드 | `POST /api/files` (NEW_VERSION 분기), `POST /api/files/:id/versions` | ✅ |
| 버전 복원 | `POST /api/files/:id/versions/:vid/restore` | ✅ |
| 이름 변경 | `PATCH /api/folders/:id`, `PATCH /api/files/:id` | ✅ |
| 이동 | `POST /api/{...}/move` | ✅ (양쪽 — hold 대상 자체 + target folder가 hold면 양쪽 모두) |
| 공유 생성/해제 | `POST /api/{file\|folder}/:id/share`, `DELETE /api/shares/:shareId` | ✅ — 공유는 권한 변경 표면이므로 차단 |
| 권한 부여/회수 | `POST/DELETE /api/:resource/:id/permissions` | ✅ |
| **신규 파일 업로드 (folder 대상)** | `POST /api/files` to held folder | ✅ — 신규 child도 즉시 `legal_hold=TRUE`로 INSERT는 의미상 hold가 더 강해지는 것이므로 거부 (보수적 정책, KISS) |
| 다운로드 | `GET /api/files/:id/download` | ❌ (허용) |
| 조회 / preview / 메타 | `GET /api/files/:id`, tree, search | ❌ (허용) |

> **권한 변경 차단 사유**: hold 중 권한이 바뀌면 hold target에 대한 access surface가 변동 → 보존 목적과 충돌. release 후 변경.
>
> **이동 양쪽 차단**: source가 held이면 거부 + target folder가 held이면 거부 (held folder에 새 자료가 들어가는 것도 차단 — 보존 범위 명확화).

#### 6.3.4 API 계약 (v2.x — wire format)

> **활성화 = v2.x**. 본 절은 endpoint 설계 reserve.

```text
POST   /api/admin/legal-holds                 # place
DELETE /api/admin/legal-holds/:holdId         # release (single-admin) 또는 dual-approval 1단계
POST   /api/admin/legal-holds/:holdId/approve # dual-approval 2단계 (게이트 활성 시)
GET    /api/admin/legal-holds                 # 활성 hold 목록 (페이지·필터)
GET    /api/admin/legal-holds/:holdId         # 단건 상세
GET    /api/admin/legal-holds/by-target?type=file&id=<UUID>  # target에 걸린 active hold 조회
```

**place** (`MANAGE_LEGAL_HOLD` 권한 — ROLE=ADMIN):

```json
POST /api/admin/legal-holds
{
  "targetType": "file" | "folder" | "user",
  "targetId": "<UUID>",
  "reason": "법적 분쟁 — 사건번호 2026-XX-XXX",
  "expiresAt": "2027-01-01T00:00:00Z"  // optional, NULL=무기한
}

→ 201 Created
{
  "holdId": "<UUID>",
  "targetType": "...",
  "targetId": "...",
  "reason": "...",
  "placedBy": { "id": "<UUID>", "displayName": "..." },
  "placedAt": "...",
  "expiresAt": "..." | null,
  "cascadeAffected": { "files": 12, "folders": 3 }  // cascade 결과 — UI 토스트용
}

에러:
- 400 VALIDATION_ERROR (reason 누락, targetType invalid, expiresAt 과거)
- 404 (target 미존재)
- 409 LEGAL_HOLD_RECENTLY_RELEASED (30일 재지정 락, ADR #46)
- 409 (동일 target에 이미 active hold — 이중 hold 거부, KISS)
```

**release** (`MANAGE_LEGAL_HOLD` 권한):

```json
DELETE /api/admin/legal-holds/:holdId
{
  "releaseReason": "분쟁 종결 (2026-12-15 합의)"
}

→ 200 OK (단일 admin 모드)
{ "holdId": "...", "releasedAt": "...", "releasedBy": {...}, "cacheFlagCleared": { "files": 12, "folders": 3 } }

→ 202 Accepted (dual-approval 게이트 활성, primary 승인만 완료 — secondary 대기)
{ "holdId": "...", "dualApprovalStatus": "pending", "secondaryApprover": null }

에러:
- 404 (holdId 미존재 또는 이미 released)
- 403 PERMISSION_DENIED (secondary가 primary와 동일 인물 — 자기 승인 거부)
```

**dual-approval 2단계** (`MANAGE_LEGAL_HOLD` 권한):

```json
POST /api/admin/legal-holds/:holdId/approve
{
  "decision": "approve" | "reject",
  "comment": "..."  // optional
}

→ 200 OK
{ "holdId": "...", "dualApprovalStatus": "released" | "pending", ... }

거부:
- approve = primary와 다른 admin이어야 함, status=pending인 hold만 처리
- reject = pending → null로 되돌리고 hold 유지
```

#### 6.3.5 Audit 이벤트 매핑

| 이벤트 | actor_id | target_type | target_id | metadata |
|---|---|---|---|---|
| `admin.legal_hold.placed` | placedBy | `legal_hold` (신규 audit target_type) | holdId | `{targetType, targetId, reason, expiresAt, cascadeAffected}` |
| `admin.legal_hold.released` | releasedBy | `legal_hold` | holdId | `{releaseReason, dualApproval: bool, secondaryApproverId?, durationDays}` |
| `admin.legal_hold.expired` | NULL (system) | `legal_hold` | holdId | `{trigger:"system.expiration", originalExpiresAt}` — share-expired/permission-expired cron 동형 |
| `admin.legal_hold.violation_blocked` | 시도자 | (mutation 대상의 type) | (대상 id) | `{action, holdId, reason, attemptedEndpoint}` — sample/throttle 정책 권장 (대량 봇 폭주 시 audit 폭증 방지) |

> **violation_blocked sampling 정책** (선택, v2.x 진입 시 결정): 동일 (actor, target, action) 묶음에 대해 60s 윈도우 내 첫 시도만 audit 기록. 메모리 LRU(LoginAttemptTracker 패턴 mirror, ADR #23/44 일관) 또는 audit 미발행 후 WARN 로그만(KISS)으로 대체 가능. 본 결정은 dev-docs에 backlog로 명시.

#### 6.3.6 권한

→ §3.1 `MANAGE_LEGAL_HOLD` enum + §3.2.5 ROLE 매트릭스. 추가 권한 enum 없음.

#### 6.3.7 30일 재지정 락

release 후 30일 이내 동일 (target_type, target_id)에 대한 place 거부 (실수 방지 — release 직후 다시 hold가 정책 의도이면 admin이 의도적으로 cooldown 조정 필요):

- 검사 SQL: `legal_holds`에서 `target_type=:t AND target_id=:id AND released_at >= NOW() - INTERVAL '30 days'` 존재 시 거부
- 에러: 409 `LEGAL_HOLD_RECENTLY_RELEASED` (docs/02 §8)
- 응답 details: `{previousReleasedAt, replaceableAt}` — UI 토스트에서 "재지정 가능 일자" 표시
- 회피: `app.legal-hold.replace-cooldown-days` properties로 환경별 조정 (dev/staging=0 가능, prod=30 default)

#### 6.3.8 Hold 만료 cron (선택)

`expires_at <= NOW()`인 active hold 자동 release (share-expired/permission-expired cron 동형):

- properties: `app.legal-hold.expiration.{enabled, cron, batch-size, zone}`
- default `enabled=false` — 운영자 명시적 활성화 (A7/share/permission cron 패턴 일관)
- cron 주기: 매 5분 (default), 운영 환경별 조정
- 처리: 트랜잭션 내 `UPDATE legal_holds SET released_at=NOW(), released_by=NULL, release_reason='system.expiration'` + cache flag clear + `admin.legal_hold.expired` audit 1건/hold
- 다중 인스턴스 안전성: row-level pessimistic lock (`SELECT ... FOR UPDATE`)으로 직렬화 — share-expired-cron 패턴 mirror

#### 6.3.9 dual-approval 게이트

properties: `app.legal-hold.dual-approval.enabled` (default=false).

- false (default): release 1회 호출로 즉시 종료. `dual_approval_status = NULL`로 INSERT (게이트 미사용)
- true: release 1단계(primary) → status=`pending` + secondary 후보 알림(이메일 — `EmailService`/ADR #45 재사용) → 2단계(secondary, primary 와 다른 ADMIN) → status=`released` + cache flag clear

**Self-approval 방지**: secondary_approver_id ≠ placed_by **AND** ≠ release request actor.

**Reject 처리**: `decision: "reject"` 시 status=NULL 복귀(release 자체 취소), hold는 active 유지. 별도 audit `admin.legal_hold.release_rejected`는 v2.x 추가 enum 후보 (현재 spec에서 reject는 audit 미발행으로 시작 — KISS, backlog).

#### 6.3.10 v2.x 진입 시 작업 분해

→ `dev/active/legal-hold-design/legal-hold-design-tasks.md` 참조. 핵심 단계:

1. V_ 마이그레이션: `legal_holds` + cache flag 2개 + 인덱스 4개
2. `Permission.MANAGE_LEGAL_HOLD` enum 추가, `IbizDrivePermissionEvaluator` 매핑
3. `LegalHoldGuard` AOP 또는 service-level 가드 (mutation entry 9곳: trash move/restore/purge, version create/restore, rename, move, share create/revoke, permission grant/revoke)
4. `HardPurgeService.findExpiredCandidates` SQL에 `AND legal_hold IS NOT TRUE` 1줄 추가 (ADR #31 close)
5. `AuditEventType` placeholder 2종 emission 활성화 + 신규 2종 추가
6. `LegalHoldController`/`LegalHoldService` + dual-approval 분기 + expiration cron
7. Frontend: `/admin/legal-holds` 페이지 (목록/상세/place/release/approve), file/folder detail에 "Legal Hold" 배지 + 차단 토스트, RightPanel에 active hold 메타 카드
8. CLAUDE.md §3 핵심 원칙 6 (DB 제약이 진실의 출처) 정합 — cache flag 동기화 invariant를 트랜잭션으로 강제, mutation 가드 service-level (DB CHECK 미사용 — folders.legal_hold가 boolean이라 CHECK로 cascade 의미 표현 불가, 코드 가드 + 트랜잭션이 진실)

---

## 7. 비밀번호 / 키 관리

> **MVP 결정**: 사내 베타는 `.env` + 사내 시크릿 저장소(없으면 사내 위키 비공개 페이지)로 충분. 외부 운영 시 Secrets Manager 도입.

- [x] `.env` 파일로 관리, 절대 커밋 금지 — `.gitignore`에 `.env*` 패턴 포함, `application.yml`은 dev-only password (`dev_password_only_change_in_real_envs` 명시)
- [ ] 운영: AWS Secrets Manager / HashiCorp Vault — *운영 (외부 출시 시점 인프라 도입)*
- [ ] 키 로테이션 주기 — *v1.x deferred (KMS/Secrets Manager 도입 후)*

---

## 8. 규정 준수 체크리스트 (도메인별)

> **MVP 결정**: 사내 베타에서는 §8.1을 사내 정책 문서로 대체. §8.2-§8.4는 도메인 진입 결정 시점에 별도 트랙(컴플라이언스 인증).

### 8.1 공통

- [ ] 개인정보처리방침 제공 — *운영 (사내 베타 → 사내 정책)*
- [ ] 이용약관 — *운영 (외부 출시 시점)*
- [ ] 쿠키 정책 — *운영 (사내 도메인은 SameSite=Lax + HttpOnly로 자동 보호)*

### 8.2 금융 (해당 시)

- [ ] 전자금융감독규정 — *v1.x deferred (도메인 진입 시 별도 트랙)*
- [ ] 데이터 국외 이전 금지 — *v1.x deferred*

### 8.3 의료 (해당 시)

- [ ] 의료법 제21조 (의료정보 보호) — *v1.x deferred*
- [ ] HIPAA (해외 환자) — *v1.x deferred*

### 8.4 공공 (해당 시)

- [ ] 국가정보보호 지침 — *v1.x deferred*
- [ ] CSAP 클라우드 보안 인증 — *v1.x deferred*

---

## 9. 취약점 대응

> **MVP 결정**: GitHub Dependabot 활성화로 의존성 스캔 1차 커버. SAST/DAST/외부 모의해킹은 외부 출시 시점.

- [ ] SAST / DAST 도구 도입 — *v1.x deferred (CI 도구 비용 ↑, 외부 출시 시 도입)*
- [ ] 의존성 취약점 스캔 (Snyk, Dependabot) — *MVP — `BETA-RELEASE.md` 게이트로 GitHub Dependabot 활성화*
- [ ] 연 1회 외부 모의해킹 — *운영 (외부 출시 시점)*
- [ ] 취약점 리포트 채널 (security@...) — *운영 (사내 베타 → 슬랙/이메일 채널로 충분)*

---

## 10. 인시던트 대응

> **MVP 결정**: 사내 베타에서는 사내 인시던트 절차로 대체. 외부 출시 시 본문화 + 별도 운영 트랙.

- [ ] 인시던트 분류 기준 (Severity 1~4) — *운영 (사내 베타 → 사내 절차)*
- [ ] 에스컬레이션 경로 — *운영*
- [ ] 데이터 유출 시 통지 의무 (72시간 내) — *운영 (외부 출시 시점 정책)*
- [ ] 사후 검토 (Post-mortem) 템플릿 — *운영 (사내 절차)*

---

## 작성 우선순위 (MVP closure 시점 갱신 — 2026-05-02)

1. ~~§3 권한 매트릭스~~ — A3 closure (`dev/completed/a3-permission-matrix/`)
2. ~~§4 감사 이벤트 정의~~ — A2 closure (`dev/completed/a2-audit-log/`)
3. §5 저장소 보안 세부 — *5.3/5.4 (mvp-qa-security Phase 3) closure / 5.1·5.2 = 운영·v1.x*
4. §6 Legal Hold — *v2.x deferred (docs/00 §4.3)*
5. 나머지는 외부 출시 트리거링 시점에 본 트랙 산출(`mvp-qa-security` findings) 기반으로 부활
