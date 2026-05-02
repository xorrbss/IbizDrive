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
| `POST` | `/api/auth/password/forgot` | 비로그인 | 면제 | 가입자 → 토큰 생성 + 메일 발송 / 미가입자 → no-op. 두 경우 모두 200 동일 응답 (anti-enumeration) |
| `POST` | `/api/auth/password/reset` | 비로그인 (token 인증) | 면제 | 토큰 hash 매칭 + TTL/used 검사 → PW 갱신 + **모든 세션 invalidate** |
| `POST` | `/api/auth/password/change` | 인증 필수 | 필수 | 현재 PW BCrypt 검증 → 새 PW 갱신 + **현재 세션 보존**, 다른 세션만 invalidate |

토큰 정책 (`password_reset_tokens` 테이블, V8):

- **저장** = SHA-256 hex (64자), 평문은 메일 본문에만.
- **TTL** = 30분 (`expires_at = created_at + 30m`).
- **1회 사용** = `used_at` set 후 동일 토큰은 INVALID_TOKEN.
- **다중 토큰 허용** — 같은 사용자 forgot 여러 번 호출 시 모든 active 토큰 유효 (race/UX 부담 회피).
- **사유 비공개** — 만료/사용됨/미존재 모두 400 INVALID_TOKEN 단일 코드 (token enumeration 방지).
- **rate-limit 미도입** — v1.x 별도 트랙.

세션 무효화 정책 차이:

- **reset**: PW 변경이 기존 모든 세션을 무효화 — `FindByIndexNameSessionRepository.findByPrincipalName(email)` → `deleteById` loop, `keepSessionId=null`.
- **change**: 현재 세션은 보존하여 사용자가 강제 로그아웃되지 않음. 다른 기기/세션은 모두 invalidate, `keepSessionId=session.getId()`.

이메일 인프라 (ADR #42):

- `EmailService` 인터페이스 + profile 분기 — `ConsoleEmailService(@Profile("!prod"))`는 stdout 로깅 (dev/test SMTP 의존성 제거), `SmtpEmailService(@Profile("prod"))`는 `JavaMailSender` 위임.
- 발송 실패는 `EmailDeliveryException`으로 표면화하되 forgot은 swallow + ERROR 로그 (anti-enumeration 200 유지).
- HTML 템플릿/i18n/비동기 큐 모두 v1.x 분리.
- **Anti-enumeration timing leak** = 알려진 한계 (가입자만 SMTP 라운드트립). v1.x rate-limit + 비동기 큐 트랙에서 재검토.

**관리자 사용자 초대 (ADR #21 admin closure, 2026-05-03)** — `POST /api/admin/users` 활성화. 본 §2.7 BCrypt(strength=12) + `DelegatingPasswordEncoder` 정책을 그대로 적용해 임시 PW를 BCrypt 해시 저장. 임시 PW 정책은 §2.8 invariant 참조.

### 2.8 사용자 등록 (ADR #21 admin closure 2026-05-03 — ADR #41 supersede 2026-05-02)

> **Status**: ADR #21(관리자 초대 only)은 ADR #41 auth-pages 트랙에서 supersede. MVP는 self-signup + first-user-ADMIN 부트스트랩. **운영자 초대(`POST /api/admin/users`)도 활성화 완료(2026-05-03, `admin-invite-email` 트랙)** — admin이 신규 사용자를 시스템에서 직접 초대할 수 있다.

- **MVP = 셀프 가입**. `POST /api/auth/signup` 활성화 (request: `{email, password, displayName}`, response: `LoginResponse` shape + auto-session).
- **first-user-ADMIN 부트스트랩**: `userRepository.count() == 0`이면 새 사용자 ROLE=ADMIN, 그 외 MEMBER. 빈 DB 첫 호출만 ADMIN 부여(초기 admin 시드 의존성 제거). 동시 두 요청 race는 MVP single-instance + tx 직렬화로 사실상 차단(엄밀 보장은 advisory lock — v1.x).
- **CSRF**: `/api/auth/signup`은 CSRF token 미요구(`csrf().ignoringRequestMatchers` + `permitAll()`). 첫 호출 token preflight 비용/UX 마찰 회피. 로그인/로그아웃은 §2.2 그대로 double-submit.
- **자동 세션**: signup 성공 = `AuthService.establishSession`(login 공통 helper, `changeSessionId()` 호출) → `LoginResponse` 반환. 가입 후 즉시 `/files`.
- **Validation (Bean Validation)**:
  - `email`: `@NotBlank @Email @Size(max=254)` — trim+lowercase 후 저장 (`users.email CITEXT` UNIQUE 의존).
  - `password`: `@NotBlank @Size(min=8, max=128)` — §2.7 ADR #19(min 12) 정정 (가입 진입 마찰 최소화, v1.x 정책 강화 트랙).
  - `displayName`: `@NotBlank @Size(min=1, max=100)` — trim 후 저장.
- **에러 envelope (auth flat)**:
  - `409 CONFLICT/DUPLICATE_EMAIL` — 이미 가입된 이메일.
  - `400 VALIDATION_ERROR` — Bean Validation 실패 (standard envelope).
- **Audit emission**: `USER_REGISTERED("user.registered")` (§4.1 추가). `UserRegisteredEvent` record + `AuthAuditListener.onRegistered`가 `@EventListener` REQUIRES_NEW로 audit_log 기록 — 로그인 `AuthenticationSuccessEvent` 패턴 일관.
- **운영자 user 초대 (`POST /api/admin/users`) — 활성화 완료(2026-05-03, `admin-invite-email`)**:
  - **요청**: `{email, displayName, role}`. role은 `MEMBER`/`AUDITOR`/`ADMIN` 중 하나(@NotNull). email/displayName은 §위 가입 정책과 동일한 Bean Validation.
  - **임시 PW 생성**: 백엔드 `TempPasswordGenerator.generate()` — 16자 `SecureRandom`, alphabet `A-Za-z0-9!@#$%&` (메일 transit 깨짐/사용자 입력 에러 최소화 위해 특수문자 일부만). DB는 `BCrypt(strength=12)` 해시만 저장.
  - **응답 invariant**: 응답 DTO는 `{id, email, displayName, role, mustChangePassword=true}` 5필드. **임시 PW(평문/해시 모두)는 응답·로그·예외 메시지·git history에 절대 비포함** — `EmailService.send()` 본문에만 등장. `AdminUserControllerTest`가 jsonPath로 `tempPassword`/`password`/`passwordHash` 키 부재 강제.
  - **첫 로그인 강제 변경**: `mustChangePassword=true`로 생성 → `auth-must-change-pw` UX(`/account/password` redirect chain)가 임시 PW 강제 변경. 토큰 기반 invite link 미도입(KISS) — 강제 변경 chain이 보안 보강.
  - **이메일 발송**: `EmailService.send(email, subject, body)` — body에 평문 임시 PW + 로그인 안내. dev/test = `ConsoleEmailService` stdout, prod = `SmtpEmailService` (ADR #42 인프라 재사용).
  - **CSRF**: 표준 double-submit (signup/forgot/reset과 달리 인증된 admin 호출이므로 토큰 보유 가정).
  - **권한**: `@PreAuthorize("hasRole('ADMIN')")` (Spring Security RoleVoter). 비-ADMIN은 403 PERMISSION_DENIED.
  - **에러**: `409 CONFLICT/DUPLICATE_EMAIL` (auth flat envelope, signup 매핑 재사용) / `400 VALIDATION_ERROR` (standard envelope) / `401 UNAUTHORIZED` (미인증) / `403 PERMISSION_DENIED` (비-ADMIN, nested error.code envelope).
  - **Audit emission**: `ADMIN_USER_CREATED("admin.user.created")` — `AdminUserCreatedEvent` 발행 → `AdminAuditListener` REQUIRES_NEW로 audit_log 기록 (§2.10 / §4.1). actorId=호출 admin user id, target_type=`user`, target_id=신규 user id.
  - **본 트랙 범위 외 (v1.x)**: 사용자 목록·역할 변경·비활성화·재초대 페이지. invite endpoint + 단일 invite form만 활성화.

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
| `admin.user.created` | 관리자 사용자 초대 (`POST /api/admin/users`, ADR #21 admin closure 2026-05-03) | — |

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

> `PURGE`는 회복 불가 액션이므로 별도 권한으로 분리. **시스템 ROLE `ADMIN`만 보유** (§3.2.5 참조). `DELETE` 권한이 있어도 `PURGE`는 자동 부여되지 않음.

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
| `ADMIN` | 전 노드 admin preset + **`PURGE`** + 사용자 관리 (`PATCH /api/admin/users/:id`) + Legal Hold |

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
  | 'admin.legal_hold.placed'
  | 'admin.legal_hold.released'
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

> **v1.x deferred** (docs/00 §4.3 v2.x 명시). 전체 §6.3은 외부 출시 + 컴플라이언스 도메인 도입 시점에 부활.

- [ ] 관리자가 특정 파일/폴더/사용자에 Legal Hold 지정 — *v2.x deferred*
- [ ] Legal Hold 상태에서는: — *v2.x deferred*
  - 삭제 불가 (휴지통 이동도 차단)
  - 영구 삭제 불가 (휴지통 purge 크론도 스킵)
  - 버전 변경 불가
- [ ] 해제는 관리자 2인 승인 (optional) — *v2.x deferred*

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
