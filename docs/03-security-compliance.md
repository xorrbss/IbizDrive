# 03 - 보안 & 컴플라이언스

> 권한 모델, 감사 정책, 저장소 보안, 법적 보존(Legal Hold) 등 보안 관점 설계.
> **현재 상태**: 스켈레톤 (팀 합류 후 본문 작성)

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

> **백엔드 스택 미정**: 아래 흐름은 토큰 종류·저장 위치·DB 스키마는 결정,
> 라이브러리(NestJS Passport vs Spring Security)는 **TBD: A 트랙에서 확정**.

### 2.1 인증 방식 (선택지)

- **1순위 (운영)**: SSO (SAML 2.0 또는 OIDC) — 사내 IdP(예: Azure AD, Google Workspace)와 페더레이션
- **2순위 (백업/관리자)**: 자체 ID/PW + MFA(TOTP). 관리자/감사자는 MFA 필수
- 두 경로 모두 동일 `users` 테이블을 owner로 매핑 (`users.external_id` 컬럼 — TBD: A 트랙)

### 2.2 토큰 / 세션 모델

| 토큰 | 저장 위치 | 만료 | 회전 | 용도 |
|---|---|---|---|---|
| **access token** (JWT) | 메모리 (또는 HttpOnly 쿠키) | 15분 | 짧게 발급, 재발급으로 갱신 | API 호출 인증 |
| **refresh token** | HttpOnly + Secure + SameSite=Lax 쿠키 | 14일 | 사용 시 1회용 회전 (rotation) | access token 재발급 |
| **sessionId** (불투명 ID) | DB `sessions` 테이블 + 쿠키 | refresh 동기 | refresh 회전 시 갱신 | audit 기록·강제 로그아웃 |

- **클레임 최소화**: JWT는 `sub`/`role`/`exp`/`iat`/`sessionId` 만. **권한 평가는 DB**.
- **회전 전략**: refresh 1회용 — 사용 시 invalidate + 새 토큰 발급. 동시 사용 감지 시 전체 세션 무효화 (탈취 의심).

### 2.3 시퀀스 — 로그인 (Login)

```text
사용자(브라우저)         App 서버               IdP/SSO            DB
      │                    │                     │                 │
      │ GET /login         │                     │                 │
      │───────────────────▶│                     │                 │
      │ 302 → IdP          │                     │                 │
      │◀───────────────────│                     │                 │
      │ SSO 인증 (외부)    │                     │                 │
      │═══════════════════════════════════════════▶                │
      │ 302 callback?code  │                     │                 │
      │◀═══════════════════════════════════════════                │
      │ GET /auth/callback │                     │                 │
      │───────────────────▶│                     │                 │
      │                    │ POST /token (code)  │                 │
      │                    │────────────────────▶│                 │
      │                    │ id_token (verified) │                 │
      │                    │◀────────────────────│                 │
      │                    │ users.upsert(email) │                 │
      │                    │─────────────────────────────────────▶ │
      │                    │ sessions.insert     │                 │
      │                    │─────────────────────────────────────▶ │
      │                    │ audit: user.login.success             │
      │                    │─────────────────────────────────────▶ │
      │ 200 + Set-Cookie:  │                     │                 │
      │   refresh, session │                     │                 │
      │   + JSON access    │                     │                 │
      │◀───────────────────│                     │                 │
      │ → /files (탐색기)  │                     │                 │
```

**실패 분기 (login.failed):**
- IdP 검증 실패 → `401`, audit `user.login.failed`(reason=idp_reject), 세션 미생성.
- 자체 ID/PW 5회 실패 → 15분 락. 락 해제 시도도 실패로 카운트.

### 2.4 시퀀스 — API 호출 + Access 만료 (Refresh)

```text
브라우저                App 서버              DB
   │ GET /api/files     │                     │
   │ (Authorization: Bearer access)           │
   │ + Cookie: refresh, sessionId             │
   │───────────────────▶│                     │
   │                    │ verify(access)      │
   │                    │   → exp 만료 (401)  │
   │ 401 + WWW-Auth     │                     │
   │◀───────────────────│                     │
   │ POST /auth/refresh │                     │
   │ (Cookie: refresh)  │                     │
   │───────────────────▶│                     │
   │                    │ sessions.find(sid)  │
   │                    │ + refresh 일치 검증 │
   │                    │────────────────────▶│
   │                    │ refresh 회전:       │
   │                    │  invalidate old     │
   │                    │  insert new token   │
   │                    │────────────────────▶│
   │ 200 + Set-Cookie   │                     │
   │ + new access token │                     │
   │◀───────────────────│                     │
   │ Retry /api/files   │                     │
   │───────────────────▶│ ... (정상 응답)     │
```

**탈취 의심 분기 (replay detection):**
- 회전된(이미 사용된) refresh가 다시 들어옴 → **세션 전체 강제 종료** + audit `user.login.failed`(reason=refresh_replay) + 사용자에게 알림.

### 2.5 시퀀스 — 로그아웃 (Logout)

```text
브라우저              App 서버              DB
   │ POST /auth/logout │                     │
   │───────────────────▶                     │
   │                   │ sessions.delete(sid)│
   │                   │────────────────────▶│
   │                   │ refresh blacklist   │
   │                   │  (남은 ttl 동안)    │
   │                   │────────────────────▶│
   │                   │ audit: user.logout  │
   │                   │────────────────────▶│
   │ 204 + Clear-Cookie│                     │
   │◀──────────────────│                     │
```

- **logout-all** 엔드포인트는 사용자의 모든 sessionId를 일괄 삭제 (다른 기기 로그아웃).

### 2.6 비활성·만료 정책

| 항목 | 값 | 비고 |
|---|---|---|
| Access 만료 | 15분 | 짧게 — 노출 시 영향 최소화 |
| Refresh 만료 | 14일 | 회전 시 만료 시점도 갱신 (롤링) |
| 비활성 자동 로그아웃 | 30분 (SPA idle) | 클라이언트 타이머 + 다음 요청 시 만료 응답 |
| MFA 재인증 | 24시간 (관리자) | 민감 작업(권한 변경/Legal Hold) 시 즉시 재인증 |

### 2.7 서비스 계정 (Service Account)

- 배치/통합용 별도 계정. 사람과 동일 `users` 테이블이지만 `kind='service'`로 구분.
- **세션 토큰 대신 장기 API 키** (저장 시 hash). 키 회전은 관리자 콘솔 — `admin.api_key.rotated` audit.
- 모든 행위는 사람과 동일하게 audit. service account 우대 없음.
- **TBD: A 트랙** — 키 회전 주기 정책(90일 권장).

### 2.8 인증 관련 audit 이벤트 (§4.1과 동기화)

`user.login.success` / `user.login.failed` / `user.logout` /
`user.password.changed` / `user.mfa.enabled` /
(추가 예정) `user.session.revoked` — TBD: A 트랙에서 §4.1에 추가.

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
| `SHARE` | 외부/내부 공유 링크 생성 | POST `/api/files/:id/share` |
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
- `department`: 부서. LTREE 기반 — 하위 부서 자동 포함 옵션 (`includeDescendants: bool`)
- `role`: 시스템 역할 (`MEMBER` / `AUDITOR` / `ADMIN`) — §3.2.5
- `everyone`: 전사

### 3.4 권한 상속 & 평가

- 폴더 → 자식 폴더/파일로 상속 (default)
- 자식에 명시적 권한 정의 시 → override
- 계산 로직: 재귀 CTE로 root까지 순회, **deny 우선** (deny가 한 번이라도 나오면 최종 deny)
- `PermissionService.check(userId, resource, resourceId, permission)`이 단일 진입점.

### 3.5 권한 매트릭스 (엔드포인트 × 권한)

각 endpoint의 권한 요구는 **`docs/02-backend-data-model.md §7.4~§7.13` Guard 컬럼**에 명시 (단일 진실 출처). 본 문서는 권한 enum과 preset 정의만 담당, endpoint 매핑은 02 문서 참조.

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
  | 'folder.audit_level_changed'
  // 권한 / 공유
  | 'permission.granted'
  | 'permission.revoked'
  | 'permission.changed'
  | 'share.created'
  | 'share.revoked'
  | 'share.expired'
  // 인증
  | 'user.login.success'
  | 'user.login.failed'
  | 'user.logout'
  | 'user.password.changed'
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

- [ ] TLS 1.2+ 전체 강제
- [ ] HSTS 헤더
- [ ] presigned URL 사용 시 짧은 만료 (10분 이내)

### 5.2 저장 구간

- [ ] S3 서버 사이드 암호화 (SSE-S3 또는 SSE-KMS)
- [ ] KMS 키 로테이션 주기: 1년
- [ ] 버킷 public access 차단

### 5.3 악성 파일 대응

- [ ] 확장자 화이트리스트 (02 문서 §5.4)
- [ ] MIME magic number 검증
- [ ] 바이러스 스캔 (v1.x)
- [ ] 스캔 실패 시 다운로드 경고

### 5.4 다운로드 보안

- [ ] Content-Disposition: attachment 강제
- [ ] `X-Content-Type-Options: nosniff`
- [ ] HTML/SVG 직접 렌더링 금지 (샌드박스 iframe 또는 별도 도메인)

---

## 6. 데이터 보호 (Data Protection)

### 6.1 개인정보 처리

- [ ] 개인정보보호법 준수 (한국)
- [ ] 처리 목적 / 보존 기간 / 제3자 제공 정책 명시
- [ ] 사용자 탈퇴 시 처리 방침 (파일 소유권 이관 vs 삭제)

### 6.2 백업

- [ ] DB: 일일 스냅샷 + PITR (7일)
- [ ] S3: Cross-region replication + 버전 관리
- [ ] 감사 로그: 별도 버킷 + WORM

### 6.3 법적 보존 (Legal Hold)

- [ ] 관리자가 특정 파일/폴더/사용자에 Legal Hold 지정
- [ ] Legal Hold 상태에서는:
  - 삭제 불가 (휴지통 이동도 차단)
  - 영구 삭제 불가 (휴지통 purge 크론도 스킵)
  - 버전 변경 불가
- [ ] 해제는 관리자 2인 승인 (optional)

---

## 7. 비밀번호 / 키 관리

- [ ] .env 파일로 관리, 절대 커밋 금지
- [ ] 운영: AWS Secrets Manager / HashiCorp Vault
- [ ] 키 로테이션 주기

---

## 8. 규정 준수 체크리스트 (도메인별)

### 8.1 공통

- [ ] 개인정보처리방침 제공
- [ ] 이용약관
- [ ] 쿠키 정책

### 8.2 금융 (해당 시)

- [ ] 전자금융감독규정
- [ ] 데이터 국외 이전 금지

### 8.3 의료 (해당 시)

- [ ] 의료법 제21조 (의료정보 보호)
- [ ] HIPAA (해외 환자)

### 8.4 공공 (해당 시)

- [ ] 국가정보보호 지침
- [ ] CSAP 클라우드 보안 인증

---

## 9. 취약점 대응

- [ ] SAST / DAST 도구 도입
- [ ] 의존성 취약점 스캔 (Snyk, Dependabot)
- [ ] 연 1회 외부 모의해킹
- [ ] 취약점 리포트 채널 (security@...)

---

## 10. 인시던트 대응

- [ ] 인시던트 분류 기준 (Severity 1~4)
- [ ] 에스컬레이션 경로
- [ ] 데이터 유출 시 통지 의무 (72시간 내)
- [ ] 사후 검토 (Post-mortem) 템플릿

---

## 작성 우선순위

1. §3 권한 매트릭스 (프론트 `usePermission`과 백엔드 미들웨어 동시 작성)
2. §4 감사 이벤트 정의 (엔드포인트별 어떤 이벤트를 기록할지)
3. §5 저장소 보안 세부
4. §6 Legal Hold (컴플라이언스 요구 시)
5. 나머지는 운영 직전 완성
