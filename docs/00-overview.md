# 00 - 시스템 개요

> 회사 문서관리 시스템 설계 문서 세트의 입구.
> 각 팀이 자기 영역부터 읽고, 계약(contract) 지점만 공유합니다.

---

## 1. 시스템 개요

### 1.1 목적
사내 구성원이 문서를 안전하게 저장·공유·버전관리하는 웹 기반 파일 시스템.

### 1.2 주요 사용자
- **일반 사용자**: 업로드, 탐색, 공유, 버전 관리
- **관리자**: 사용자/부서/권한 관리, 감사 로그 열람
- **감사자 (선택)**: 읽기 전용 감사 로그 접근

### 1.3 기술 스택
```text
Frontend: Next.js 15 (App Router) + TypeScript + Zustand + TanStack Query v5 + dnd-kit
Backend:  Spring Boot 3.x + Java 21 (LTS) + Gradle (Kotlin DSL)
          Spring Data JPA + Hibernate / Spring Security 6 + Spring Session JDBC
          tus-java-server + AWS SDK v2 (S3 multipart) / SseEmitter (MVC)
          Hibernate Envers 또는 AOP 기반 audit / Flyway
DB:       PostgreSQL 15+
Storage:  S3 호환 (AWS S3 / MinIO / Azure Blob)
Search:   Postgres tsvector (MVP) → OpenSearch (v1.x)
Realtime: SSE (SseEmitter, MVC 기반)
```

---

## 2. 문서 구조

| 문서 | 주요 독자 | 상태 |
|---|---|---|
| **00-overview.md** (본 문서) | 전체 | ✅ |
| **01-frontend-design.md** | 프론트엔드 팀 | ✅ (v3 완성) |
| **02-backend-data-model.md** | 백엔드 팀, DBA | ✅ |
| **03-security-compliance.md** | 보안 팀, 백엔드 팀 | 🔲 스켈레톤 |
| **04-admin-operations.md** | 운영 팀, 관리자 | 🔲 스켈레톤 |

---

## 3. 문서 간 계약(Contract) 지점

문서가 분리되어 있으므로 **팀 간 동기화가 필요한 지점**만 명시:

### 3.1 API 계약
- 모든 엔드포인트 스키마는 **02-backend-data-model.md §7**이 단일 진실 출처
- 프론트는 OpenAPI spec에서 타입 생성 (`openapi-typescript`)
- 변경 시: 02 문서 PR → 프론트 팀 리뷰 필수

### 3.2 권한 모델
- 권한 enum 9종 — `READ / UPLOAD / EDIT / MOVE / DOWNLOAD / DELETE / SHARE / PERMISSION_ADMIN / PURGE` — 은 **03-security-compliance.md §3.1**이 단일 진실 출처
- 프론트 `src/types/permission.ts`(UX 게이트, 보안 아님)와 백엔드 `@PreAuthorize`가 동일 enum 사용 — CLAUDE.md §3 원칙 12 (계약 동기화)에 따라 enum 변경 시 양쪽 동시 수정 필수
- `PURGE`는 시스템 ROLE `ADMIN`만 보유 — 노드 admin preset에도 부여 금지 (영구 삭제 이중 안전장치)

### 3.3 정규화 함수
- `normalizeFileName`, `normalizeForSearch` 구현은 **프론트/백엔드 동일 로직**
- 02 문서 §2.4에 의사 코드 정의
- 테스트 케이스 공유 (양쪽 CI에서 동일 결과 검증)

### 3.4 감사 이벤트 타입
- `AuditEventType` enum은 **03-security-compliance.md §4**에서 정의
- 프론트/백엔드 모두 참조

### 3.5 에러 코드
- 403 (권한 없음) / 409 (충돌) / 413 (용량 초과) / 423 (잠김) 등
- **02-backend-data-model.md §8**에 전체 목록
- 프론트 전역 에러 핸들러와 매칭

---

## 4. 개발 마일스톤

> 프론트엔드 마일스톤(M1~M16)은 `docs/progress.md`에서 추적, 백엔드 마일스톤은 §4.4 참조.

### 4.1 MVP (8~12주)
```text
Week 1-2   : DB 스키마 + 기본 인증 + 업로드/다운로드 API
Week 3-4   : 폴더 트리 + 파일 목록 + FolderTree/Breadcrumb UI
Week 5-6   : 권한 시스템 + 공유 + BulkActionBar
Week 7-8   : 버전 관리 + 휴지통 + ConflictDialog
Week 9-10  : 감사 로그 + 관리자 기본 기능
Week 11-12 : QA + 보안 점검 + 베타
```

### 4.2 v1.x (MVP 후 3~6개월)
```text
- tus 재개 업로드
- SSE 실시간 동기화
- 전문 검색 (OpenSearch)
- 바이러스 스캔
- 외부 링크 공유
- 파일 잠금 (pessimistic)
```

### 4.3 v2.x (장기)
```text
- Co-authoring (Office Online 연동)
- Legal Hold
- 부서 조직도 고급 연동 (SCIM)
- DLP (Data Loss Prevention)
```

### 4.4 백엔드 마일스톤 (A0~A7)

Spring Boot 도입(ADR #11)에 따른 백엔드 트랙. A0~A1은 수동 spec 모드, A2 이후는 구조 안정 후 자율 모드 검토.

| # | 단계 | 산출물 | 의존 | 작업 모드 |
|---|---|---|---|---|
| **A0** | 프로젝트 셋업 + 정규화 spec | `backend/` 스캐폴딩, Gradle(Kotlin DSL), Flyway V1, `docs/normalize-fixtures.json`, `NormalizeUtil` (Java) + Vitest/JUnit 양쪽 통과, CI 게이트 | — | 수동 |
| **A1** | 인증 | Spring Security 6 + Spring Session JDBC + 쿠키 세션(HttpOnly+SameSite=Lax) + CSRF double-submit + `LoginController` + 로그아웃 + `SessionFilter` | A0 | 수동 |
| **A1.5** | 권한 매트릭스 (백엔드 권위) | docs/03 §3 작성 → `@PreAuthorize` 표현식 + `PermissionService` + 단위 테스트. ADR #17 발효 | A1 | 수동→자율 검토 |
| **A2** | 폴더/파일 GET | JPA 엔티티/리포지토리 + 트리·목록·상세 endpoint + 정렬/페이지네이션 + 권한 가드 적용 (read 권한) | A1.5 | 자율 검토 |
| **A3** | Mutation | 생성/이름변경/이동 + `@Transactional` + `SELECT FOR UPDATE` + soft delete (`deleted_at` + UNIQUE WHERE) + 에러 코드 매핑 (docs/02 §8) | A2 | 자율 검토 |
| **A4** | tus 업로드 + 감사 (M12 일부 흡수) | tus-java-server + `S3Store`(AWS SDK v2) + 권한·정규화·충돌 검사 + 완료 시 `audit_log` 기록 + audit 뷰어 endpoint + 프론트 연결 | A3 | 자율 검토 |
| **A5** | SSE 실시간 동기화 | `FilesSseController` + `EventBus` + 무효화 트리거 ↔ TanStack Query | A4 | 자율 검토 |
| **A7** | 휴지통 | `/trash` GET + `restore` / `permanent-delete` endpoint + 프론트 라우트 백엔드 연결 (UI는 기존) | A3 | 자율 검토 |

흡수/통합 내역:
- **A6 → A1.5**: 권한 백엔드 이전이 mutation 진입(A3) 전에 완료되어야 함(ADR #17). 별도 후행 단계 금지.
- **M5.1 (tus 재개) → A4**: tus-java-server 도입으로 재개 로직이 표준화, 별도 마일스톤 불필요.
- **M12 (감사 로그) → A4 후반**: audit_log writer + 뷰어 endpoint를 A4에 통합.

---

## 5. 의사결정 로그 (ADR)

| # | 결정 | 근거 | 문서 |
|---|---|---|---|
| 1 | URL folderId 중심 (slug는 표시용) | 폴더 rename/move 시 링크 안정성 | 01 §2 |
| 2 | RightPanel은 query param | parallel route 과잉, 단일 오버레이면 query param이 표준 | 01 §2.3 |
| 3 | 낙관적 업데이트 = 비파괴적만 | 403 롤백 시 UX 파괴 방지 | 01 §1.3 |
| 4 | unique constraint = partial index | 휴지통 파일은 이름 충돌 허용 | 02 §3.2 |
| 5 | storage_key = UUID | 경로 추측 공격 방지, 한글/특수문자 독립 | 02 §5 |
| 6 | 업로드 완료 시점 트랜잭션 체크 | 동시 업로드 race 방지 | 02 §6.1 |
| 7 | MVP 업로드 = multipart | tus 복잡도 대비 ROI, 파일 크기 분포가 소형 중심 | 01 §9 |
| 8 | MVP 실시간 = 폴링 | SSE 인프라 복잡도 회피 | 01 §15.1 |
| 9 | 뷰 로그 = 민감 폴더만 | 로그 폭증 방지 | 03 §4.2 |
| 10 | 문서 분리 | v3 단일 문서 2000줄 초과, 팀 병렬화 필요 | 00 본 문서 |
| 11 | **백엔드 = Spring Boot 3.x + Java 21** | 엔터프라이즈 보안·트랜잭션 성숙도, JPA + Flyway + Spring Security 통합 안정성. NestJS 후보 대비 정규화 함수 이중구현 비용은 ADR #16 fixtures 공유로 상쇄 | 00 §1.3, 02 §1 |
| 12 | **인증 = 쿠키 세션 (HttpOnly + SameSite=Lax) + Spring Session JDBC** | XSS 토큰 탈취 차단(JWT in localStorage 회피), Next.js SSR/RSC 친화. CSRF는 double-submit 토큰. 다중 인스턴스 세션 공유는 Postgres `SPRING_SESSION` 테이블로 구현(Flyway가 schema 권위, `initialize-schema: never`). MVP 인프라 단순화 우선 — Redis 도입(세션·rate limit·캐시)은 v1.x 별도 ADR로 분리 | 03 §1, §2 |
| 13 | **업로드 = tus-java-server (S3 multipart 위임)** | ADR #7 superseded. 표준 프로토콜 재개·체크섬 검증을 자체 구현 회피, S3 multipart는 라이브러리가 위임 처리. M5.1 useUpload 인터페이스 안정 | 01 §9, 02 §6.1 |
| 14 | **실시간 = SSE (SseEmitter, MVC 유지)** | ADR #8 superseded. 문서 변경 알림은 단방향, WS sticky session 인프라 회피. Webflux 도입 안 함 — MVC 단일 모델 유지로 복잡도 통제 | 01 §15 |
| 15 | **API base URL = build-time `.env.local`** | `NEXT_PUBLIC_API_BASE_URL` 빌드시 주입. dev/staging/prod 분리 필요 시점에 runtime `window.__ENV__` 주입으로 전환(deferred) | 00 §1.3 |
| 16 | **정규화 함수 = 공유 fixtures 검증 (docs/normalize-fixtures.json)** | CLAUDE.md §3 원칙 11(프론트/백엔드 동일 로직) 보증. Vitest + JUnit 양쪽 동일 fixtures 로드, CI 게이트로 드리프트 차단 | 02 §3 |
| 17 | **권한 매트릭스 = 백엔드 권위 (`@PreAuthorize`)** | 프론트 `usePermission`은 UX용, 보안 아님(CLAUDE.md §3 원칙 10). A3 mutation부터 즉시 활성화 — A6 후이전 금지(권한 미적용 mutation merge 방지) | 03 §3 |
| 18 | **MVP 인증 범위 = 자체 ID/PW only** | SSO(SAML/OIDC), MFA(TOTP), remember-me, IP rate limit, 셀프 비밀번호 리셋 모두 MVP 제외. SSO는 IdP 협의·메타데이터 교환 비용 큼 → A6 또는 v1.x. MFA·셀프 reset은 이메일 인프라 필요 → A1.5 후속. rate limit은 G4 lockout이 1차 방어 → v1.x | 03 §2 |
| 19 | **비밀번호 해싱·정책 = BCrypt(strength=12) + 최소 12자 (영·숫 각 1자, 공백 금지)** | Spring Security 기본 `BCryptPasswordEncoder`, BouncyCastle 의존성 회피. Argon2id 마이그레이션은 `DelegatingPasswordEncoder`로 추후 가능. 길이 우선 정책(OWASP), 특수문자 강제는 사용자 저항. zxcvbn/HIBP 사전 공격 방지는 v1.x | 03 §2.7 |
| 20 | **세션 만료·잠금 = idle 30분(sliding) + absolute 8시간(max) + 5회 실패/15분 lockout (Redis 카운터)** | Spring Session `setMaxInactiveIntervalInSeconds(1800)` + 별도 issuedAt 검사로 8시간 강제. lockout은 `AuthenticationFailureBadCredentialsEvent` 리스너 → Redis `INCR loginfail:{email}` + `EXPIRE 900`. 락 해제 시도도 실패로 카운트 | 03 §2.6 |
| 21 | **사용자 등록 = 관리자 초대 only (셀프 가입 금지)** | 사내 시스템 → 셀프 가입 부적절. MVP는 (a) Flyway 시드로 admin 1명 + (b) `POST /api/admin/users` (admin이 email/임시PW 지정)로 user 생성. 첫 로그인 시 PW 변경 강제. 초대 메일은 A1.5(이메일 인프라 도입 시) | 03 §2.8, 02 §7.12 |
| 22 | **`/api/auth/me` 응답 = identity + role + permissionsCacheKey만 (effectivePermissions full resolve 제외)** | `{ user, departments, roles, effectivePermissionsCacheKey }`. 폴더×권한 N×M full resolve는 페이로드 폭증 → 권한은 per-resource 응답에 `currentUserPermissions: ['READ','EDIT']` 동봉(별도 endpoint 호출 X). cacheKey는 권한 변경 시 invalidate trigger | 02 §7.4, 01 §6.3 |
| 23 | **MVP lockout 카운터 backing store = in-memory `ConcurrentHashMap`** | ADR #20의 "Redis 카운터" 결정을 MVP 한정으로 정정 (ADR #12 본문이 Redis 도입을 v1.x로 분리). `LoginAttemptTracker`가 lowercased email → (count, lockedUntil) 매핑. 5회/15분 lockout, 만료는 lazy 평가 (cleanup 스레드 미운영). 단일 인스턴스 가정 — 다중 인스턴스/Redis 도입 시 본 클래스 인터페이스를 추출하여 교체. 프로세스 재시작 시 카운터 휘발은 의도된 동작 (재시작 후 재시도 허용). | 03 §2.6, A1.3 |

### 5.1 Superseded ADRs

#### ADR #7: MVP 업로드 = multipart

- Status: Superseded by ADR #13 (2026-04-25)
- 결정: MVP에서는 단순 multipart 업로드 채택. tus 재개 업로드는 v1.x로 연기.
- 근거: tus 복잡도 대비 ROI, 파일 크기 분포가 소형 중심.
- 참조 문서: 01 §9
- Superseded 사유: 백엔드 스택 결정(ADR #11)과 함께 tus-java-server + S3 multipart 위임이 라이브러리로 안정화되어, 자체 재개 로직 부재의 위험이 ROI 판단을 역전. M5.1 useUpload 인터페이스 설계 시점부터 재개 지원.

#### ADR #8: MVP 실시간 = 폴링

- Status: Superseded by ADR #14 (2026-04-25)
- 결정: MVP 실시간 동기화는 TanStack Query staleTime 단축 기반 폴링. SSE는 v1.x로 연기.
- 근거: SSE 인프라 복잡도 회피.
- 참조 문서: 01 §15.1
- Superseded 사유: SseEmitter(MVC)는 Webflux 도입 없이 표준 Spring MVC에서 사용 가능하여 인프라 추가 부담이 사실상 없음. 폴링 staleTime 추정의 트레이드오프(즉시성 vs 서버 부하)를 회피.

---

## 6. 용어집

| 용어 | 정의 |
|---|---|
| **folderId** | 폴더의 불변 식별자 (UUID). URL canonical key |
| **storage_key** | 저장소 객체 키 (UUID). 원본 파일명과 무관 |
| **normalized_name** | NFC + lowercase + trim 처리된 파일명. 중복 검사용 |
| **current_version_id** | 파일의 "현재 표시 버전" 참조 |
| **effective permission** | 사용자/부서/역할 권한을 합산한 최종 권한 |
| **tombstone** | 휴지통 상태 (deleted_at IS NOT NULL, purge_after 이전) |
| **purge** | 영구 삭제 (저장소 객체 + DB row 모두 제거) |
| **audit_level** | 폴더별 감사 레벨 (standard / strict). view 로그 여부 결정 |

---

## 7. 다음에 작성할 문서

- **03-security-compliance.md** 본문 (권한 매트릭스, 감사 대상, 저장소 보안)
- **04-admin-operations.md** 본문 (관리자 페이지, 쿼터, 백업)
- 각 도메인별 API OpenAPI spec
- DB 마이그레이션 스크립트
