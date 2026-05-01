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
| 24 | **Audit emission = AOP `@Audited` + Spring Security ApplicationEvent listener 하이브리드** | (a) 비즈니스 이벤트(file.*, folder.*, permission.*)는 메서드 레벨 `@Audited(event=, target=)` 어노테이션 + `@AfterReturning` AOP — 성공한 액션만 기록, 메서드 그래프에서 grep으로 추적 가능. (b) 인증 이벤트(`user.login.*`, `user.logout`)는 `@EventListener`가 `AuthenticationSuccessEvent`/`AbstractAuthenticationFailureEvent`/`LogoutSuccessEvent` 구독. AuthService는 표준 `AuthenticationManager`를 사용하지 않는 custom flow이므로 `ApplicationEventPublisher.publishEvent(...)`를 명시 호출 — **publish 호출은 비즈니스 로직이 아닌 cross-cutting 신호로 침투 허용** (`@Audited` 어노테이션과 의미 동등, grep 가능성 보존). 비즈니스 로직(검증·예외·세션·last_login_at 갱신) 0줄 변경 원칙은 유지. Service가 직접 `AuditService.record()` 호출하는 것은 거부(누락 위험), DB trigger 거부(actor_id/IP 미가용). audit insert는 `REQUIRES_NEW` 트랜잭션으로 비즈니스 트랜잭션과 분리 (실패 격리). | 03 §4, A2.1, A2.4 |
| 25 | **Audit append-only 강제 = DB role 분리 (`app_user` INSERT/SELECT only) + REVOKE UPDATE/DELETE** | Postgres role을 (a) `app_user`(앱 런타임), (b) `audit_admin`(SELECT only, read API), (c) `db_superuser`(마이그레이션·파티션 관리) 3종으로 분리. V4 마이그레이션이 `REVOKE UPDATE, DELETE ON audit_log FROM app_user` 적용 → 앱 코드가 의도/실수로 UPDATE/DELETE 시도 시 SQLState `42501`로 차단. 트리거 차단 추가 안 함 — superuser는 트리거도 우회 가능, REVOKE 권한 모델이 진짜 boundary (KISS). RED 테스트가 `42501`로 증명. | 02 §2.8, 03 §4.4, A2.0 |
| 26 | **PermissionEvaluator MVP = user-level (Role 기반) 평가만. resource-level은 A4 이월** | A3 진입 시점에 file/folder 도메인 부재 → resource-level 권한 테이블(`permissions`) 도입 불가. A3는 `IbizDrivePermissionEvaluator implements PermissionEvaluator`를 도입하되 내부 평가는 `Role`만 사용 — `ADMIN`은 모든 permission true (단, `PURGE`도 `hasRole('ADMIN')` 별도 가드로 이중 검사), `AUDITOR`는 `READ`만 true, `MEMBER`는 deny by default. SpEL 호출 시그니처(`hasPermission(#id, 'folder', 'READ')`)는 docs/02 §7.10 그대로 채택 → A4에서 `permissions` 테이블 + 재귀 CTE 도입 시 evaluator 내부만 교체, controller `@PreAuthorize` 호출처는 보존. `permission.granted/revoked` audit emission도 grant/revoke endpoint(POST/DELETE `/api/:resource/:id/permissions`)가 file/folder 의존이라 A4 이월 — A3에서는 ROLE 변경(`PermissionService.changeRole`)에 한해 `permission.changed`만 emit. `effectivePermissionsCacheKey`는 ADR #22 형식 유지하되 정적값(`userId:role:v0`)을 SHA-256 hex prefix 16자(입력=`userId\|role\|sortedJoin(rolePermissions)`)로 교체 — opaque token으로 frontend invalidate trigger 사용. **A4.4 closure (2026-04-29)**: `permission.granted/revoked` 실 emission 활성화 — `PermissionService.grantPermission/revokePermission` + `PermissionGrantedEvent/RevokedEvent` + `PermissionAuditListener` 확장. `POST /api/:resource/:id/permissions` (201) / `DELETE /api/permissions/:permissionId` (204) endpoint 도입. resource-level 평가 자체는 A4.3 (a4-evaluator 세션)에서 별도 처리. | 03 §3.1~§3.6, 02 §7.10, A3.0~A3.5, A4.4 |
| 27 | **A4 = PR 2개 분할 (A4-data + A4-controllers)** | A4 단일 마일스톤은 추정 13~19 commits → 단일 PR 리뷰 부담 ↑. A4-data PR(A4.0 docs정합+ADR + A4.1 V5 마이그레이션 + A4.2 entity/repo/normalize)는 schema + repository unit test로 독립 검증 가능. A4-controllers PR(A4.3 evaluator 교체 + A4.4 permission endpoint+emit + A4.5 폴더/파일 CRUD + A4.6 E2E + A4.7 closure)는 머지된 A4-data 위에서 분기 → 회귀 추적 용이. A2/A3 단일 PR 패턴과 차이는 A4 규모 반영한 의식적 결정. 의존 단방향: A4-controllers는 A4-data master 머지 후 새 worktree에서 분기. | A4 plan/context/tasks (`dev/active/a4-folder-file-domain/`), A2/A3 commit log |
| 28 | **permissions v1 = `preset` 단일 컬럼 (deny semantics v1.x 이월)** | KISS + YAGNI (CLAUDE.md ULTIMATE INVARIANTS 1, 2). 명시 deny 사용 케이스가 MVP 부재 — evaluator는 explicit grant lookup만(allow 발견 시 true, 어디서도 grant 없으면 false = "grant 우선"). `docs/03 §3.4` 본문의 "deny 우선" 표기는 v1.x deferred 마커로 명시 (`### 3.4` 1줄 마커). v1.x 도입 경로: V6+ 마이그레이션으로 `permissions.deny BOOLEAN` 컬럼 또는 별도 `denies` 테이블 추가 — 현 schema(`subject_type`/`resource_type`/`preset`)는 이 확장을 막지 않음(컬럼 추가만으로 호환). evaluator 로직도 단순(deny 우선순위 규칙·tie-breaker 불필요) → A4 MVP에서 평가 비용·테스트 표면적 ↓. | 03 §3.4, 02 §2.6, A4.0~A4.3 |
| 29 | **`file_versions` entity/repository/CRUD endpoint A5 이월 — V5 schema에는 테이블만 도입** | YAGNI (CLAUDE.md ULTIMATE INVARIANTS 2). 버전 관리 UI/API(이력 탭, 복원, 비교)는 별도 기능 — A4 MVP CRUD(create/rename/move/delete/restore)와 결합 없음. V5 schema는 `file_versions` 테이블 + DEFERRABLE FK `files.current_version_id` 도입(backup V5와 동일) → A5에서는 entity/repository/endpoint만 추가하면 됨. **보장사항**: (a) `files.id`는 mutation/restore 시 stable, (b) `storage_key` 교체 시 `file_versions` 행 추가 가능 구조 유지, (c) `current_version_id` NULL 허용 그대로 유지(MVP는 최신 버전 단일 가정, A5에서 NOT NULL 제약 추가 검토). codex backup `FileVersion.java`는 A5 부트스트랩 시점에 reference로 재검토 — A4에서 cherry-pick 금지. **A5 closure (2026-04-29, squash-merge `5155e00`)**: deferred → **closed**. FileVersion entity + VersionScanStatusConverter + FileVersionRepository(`findByFileIdOrderByVersionNumberDesc` + `existsByStorageKey`) + GET `/api/files/{fileId}/versions` (`@PreAuthorize` READ + `isCurrent` 계산 + soft-delete 404) 모두 도입. 보장사항 (a)(b)(c) 모두 충족 — `files.id` stable, storage_key 교체 가능, `current_version_id` NULL 허용 유지. **잔여 이월(A6+)**: POST `/versions` (업로드 commit), 버전 restore, scanner worker, `scan_result` JSONB 매핑. PR #13 single, CI green. | 02 §2.5/§7.6, A4.1, A5 closed |
| 30 | **A4.7 endpoint 권한 정책 — root create/move(`parentId`/`targetParentId == null`) = ROLE `ADMIN` only, 그 외 = 부모 EDIT 위임** | docs/02 §7.5 본문 Guard는 `hasPermission(#req.parentId, 'folder', 'EDIT')` 단일 표기 — `parentId == null`(root) 케이스의 평가 결과가 evaluator 내부에 의존(false 반환 → 일반 사용자 차단)하나, 이를 **명시적 정책으로 고정**한다. SpEL 삼항(`#req.parentId == null ? hasRole('ADMIN') : hasPermission(...)`)으로 root 분기를 controller 레벨에서 표현 — root 폴더 생성·이동은 시스템 전역 동작이므로 노드 admin preset(부분 권한 위임)이 아닌 ROLE ADMIN으로 제한(원칙 10 — 파괴적 액션은 백엔드 재검증). PURGE 분리(ADR #26 표 §3.2.5)와 동일 정신: 노드 단위 권한 위임이 root 트리 구조 변경으로 번지지 않게. resource-level evaluator(A4.3)도 `parentId=null` 케이스에 별도 grant lookup이 없으므로 controller SpEL이 진실의 출처. | 03 §3, 02 §7.5, A4.7 (`a4-folder-endpoint`) |
| 31 | **A7 hard purge = DB-only. S3 객체 삭제는 storage 모듈 도입 시점으로 deferred.** | A7 진입 시점에 backend storage 모듈 0개 (S3 client/추상화 미존재). `purge.expired` 배치 잡(docs/04 §13)은 `purge_after <= NOW()`인 folders/files를 **DB에서만** hard delete + `file_versions` cascade — `storageKey`는 audit `after_state.orphanStorageKeys` (cap=1000)에 기록만 하고 실 S3 객체는 orphan으로 잔존. **편법 아닌 명시적 deferred** (CLAUDE.md §3 원칙 9 — 문제 은폐 금지): storage 모듈 milestone에서 (a) `S3StorageClient` 추상화 + (b) 기존 `orphan.detect` 잡을 storage_key 기반 cross-check로 확장 → A7 deferred orphan과 일반 orphan 동일 경로로 정리. **이 결정 변경 시**: storage 모듈 신설 시점에 ADR close + `HardPurgeService` 재작성으로 in-loop S3 delete 활성화. **Audit 정책**: `SYSTEM_PURGE_EXECUTED` summary-only 1건/run (A6 root-only audit 패턴 일관). **`FILE_PURGED`/`FOLDER_PURGED` per-row enum은 A8 reserve** (`/api/trash/:id` manual purge 트랙). **MAX_PURGE_PER_RUN=10000** (`app.purge.max-per-run` properties). **Schedule cron `0 0 0 * * * Asia/Seoul`** (`app.purge.cron`/`zone` properties). **No `@SchedulerLock`** — MVP single-instance 가정 (멀티화 시 별도 ADR). **Legal Hold 스킵** — `legal_hold` 컬럼 미존재, 컬럼 도입 시 `WHERE legal_hold IS NOT TRUE` 조건 추가. | 02 §2.5/§7.11.1, 04 §13, A7 (`a7-hard-purge`) |
| 32 | **A8 manual trash purge — URL `DELETE /api/trash/:type/:id` (single only) + per-row `FILE_PURGED`/`FOLDER_PURGED` audit. Bulk `DELETE /api/trash` 미구현(별도 트랙). SSE event 실 emission은 인프라 milestone deferred(audit-only).** | **URL에 `:type` 명시** — file/folder UUID는 별도 테이블, single `:id` 라우팅은 양 테이블 lookup(편법성 dispatch) 필요. `GET /api/trash` 응답이 이미 `type` 필드를 내려주므로 client가 자연스럽게 보유. controller dispatch 단순화 + `@PreAuthorize("hasRole('ADMIN')")` 단일 가드. **Per-row audit 활성화** — ADR #31 reserve 해제. `FILE_PURGED("file.purged")`/`FOLDER_PURGED("folder.purged")` enum은 정의만 됐고 사용처 0 → A8.2 `TrashPurgeService`가 1 call = 1 audit 발행. `actor_role="ADMIN"`, `before_state.storageKeys` + `after_state.purgedAt`. folder 단건 purge는 후손 cascade(A7 leaf-first 패턴 재사용)이지만 audit은 root 1건(A6 root-only 패턴 일관). **Bulk DELETE `/api/trash` 미구현** — `purge.expired` daily cron(A7) + manual single(A8) 조합이면 운영 충분. 운영 요구 발생 시 별도 ADR/트랙. docs/02 §7.11 표 4행은 보존(편법성 표기 아님 — 향후 활성화 가능 형태). **SSE emission deferred** — `EventBus`/`SseEmitter` 인프라 0개. ADR #14 결정만 유효, 구현은 SSE 인프라 milestone. A8은 audit-only(A6/A7 일관). `TrashPurgeService` 종단부 `// TODO: SSE emit (인프라 milestone)` hook 주석 — 도입 시 1줄 활성화. **GET /api/trash 권한 = `isAuthenticated()` + 결과 후처리** — DB-level 권한 join은 MVP overkill. trash row가 30일 grace × 평균 트래시율(<수만건) 가정 하에 페이지 한도 < 권한 보유 row 수면 충분. 큰 dataset 발생 시 별도 ADR. **Restore endpoint drift 정정** — docs/02 §7.11 line 1114 `POST /api/trash/:id/restore` 표기는 A6 구현(`POST /api/files/:id/restore` + `POST /api/folders/:id/restore`)과 drift. 코드가 진실의 출처(원칙 6) → docs 정합. **S3 객체 hard delete**는 ADR #31 정책 그대로 — manual purge에서도 DB-only, `orphanStorageKeys`만 audit `after_state`에 기록. | 02 §7.11/§7.13.1, 03 §3.2.5, 01 §13, A8 (`a8-trash-manage`) |
| 33 | **A9 search endpoint = `LOWER(normalized_name) LIKE %q%` MVP. tsvector(full-text) / `pg_trgm`(trigram) deferred. `type=file\|folder\|all` 외 filter는 spec만 보존(미구현, 향후 추가 시 controller param + service overload만으로 hook).** | **알고리즘 = LIKE on `normalized_name`** — `files.normalized_name` (V1 마이그레이션부터 존재) + `folders.normalized_name`을 `LOWER(normalized_name) LIKE LOWER(:pattern) ESCAPE '\\'` 양 컬럼 검색. NormalizeUtil.normalizeForSearch가 이미 NFC + lowercase + 공백 collapse를 거쳐 row와 query 양쪽 입력을 통일 — case-folding 일관. **MVP 항목 수 가정 < 10k** (단일 테이블 row scan 비용 허용). 큰 dataset 도입 시 (a) `tsvector` 컬럼 + GIN index + `to_tsquery` 또는 (b) `pg_trgm` extension + `gin_trgm_ops` index로 교체 — 둘 다 V_ 마이그레이션 + JPA native query 재작성 필요 → 별도 트랙/ADR. **편법 아닌 명시적 deferred** (CLAUDE.md §3 원칙 9): controller `@RequestParam`/service signature는 `q`/`type`/`cursor`/`limit` 4개로 고정 — 알고리즘 교체는 repository internal만 변경. **Filter scope MVP** = `type` only (file/folder/all, default=all). docs/02 §7.8 의사코드 `owner`/`modifiedFrom`/`modifiedTo`는 spec 수준 보존이지만 controller 파라미터 미수용 — frontend `useSearch` filters 객체가 현재 비어 있고, 추가 필터 도입 시 controller param + service overload만으로 hook 가능 (LIKE WHERE 절 확장). **권한 후처리 = READ** — A8 trash 패턴 1:1 변형. `PermissionService.effectivePermissions(role)` ROLE 경로 short-circuit + resource-level grant fallback. ADMIN → 전부, MEMBER → grant 보유분만. limit+1 패턴으로 hasMore 판정 + 권한 후처리 후 결과가 < limit이어도 nextCursor 발급 (검색 응답성 우선, A8 일관). **Cursor `{updatedAt}|{type}|{id}` base64** — A8 `TrashCursor` 변형 (`{deletedAt}|{id}` → `{updatedAt}|{type}|{id}`). type 분리 이유: 동일 timestamp + 동일 ms 시 file/folder 양쪽 결과 merge sort에서 type tiebreak 필요. **minLength 2자 = normalize 후 기준** (docs/02 §7.8 line 1057). 1자/공백만 → 400 `INVALID_SEARCH_QUERY`. **LIKE pattern escape** — `%`, `_`, `\` → `\` prefix. SQL `ESCAPE '\\'` clause로 명시. **검색 audit emission deferred** — `SEARCH_QUERIED` enum 정의 0. 보안 트랙(개인정보 노출 점검 등)에서 별도 검토. | 02 §7.8/§3, 01 §10, A9 (`a9-search-endpoint`) |
| 34 | **A10 shares endpoint = `shares` 별도 row + `permissions` row 위 메타. POST `/api/files/:id/share`(A10) + POST `/api/folders/:id/share`(A12 활성화, V6 schema 그대로 — file/folder XOR per row). DELETE `/api/shares/:shareId` = `revoked_at` set + permission row delete + audit `share.revoked` 단일 발행(`permission.revoked`는 emit 안 함, 이중 발행 회피). request wire format = preset V5 CHECK 4값(`read`/`upload`/`edit`/`admin`) + subject 4종 lower-case. `Preset.SHARE` enum-only(V5 CHECK 미지원, controller 진입 거부 — backlog). `expires_at` 컬럼 V6 추가. with-me MVP = `subject_type='user'` 매칭만(department/role/everyone backlog). `SHARE_CREATED`/`SHARE_REVOKED` audit 첫 활성화. folder share endpoint A12에서 활성화. `SHARE_EXPIRED` cron 활성화(2026-05-01 closure). SSE emission은 별도 트랙 deferred(audit-only).** | **shares 테이블 = SRP 분리** — ADR #28 본문 "preset 4값(share 제외, share 는 별도 shares 테이블)"의 의미 정합: `permissions.preset`은 V5 CHECK가 정의한 4값(`read`/`upload`/`edit`/`admin`)만 persistable. `Preset.java`는 5 enum 값(`SHARE` 포함)이지만 `Preset.SHARE.wire()='share'`는 V5 CHECK 미지원 → A10 controller 진입 즉시 거부(400 BAD_REQUEST). **drift 해소는 별도 트랙**: V_ 마이그레이션으로 CHECK 확장 + Preset.SHARE persistence 활성화 — A10 scope 외. `shares` 테이블 = 공유 메타(message/expiresAt/revoke 추적) + `permission_id` FK로 4-preset permission row와 1:1 연결. 권한 row에 message/revoked_at 컬럼 추가 대안은 거부(권한과 공유 메타 책임 혼합). **request wire format 정정** — docs/02 §7.9 본문 `'VIEW'\|'EDIT'\|'FULL'`/`'USER'\|'DEPT'`는 코드와 case·집합 모두 drift → A10.0에서 lower-case + V5 4 preset + PermissionService 4 subject로 정합. **revoke 시 permission row delete + share `revoked_at` set** — 공유받은 사람이 file 접근 즉시 회수(권한 row 잔존 시 보안 누설). audit 이중 발행 회피 — `share.revoked` metadata에 `permissionId` 보존하여 추적 가능. **`expires_at` 컬럼** — frontend permission expiresAt UX 패리티. `permissions.expires_at`(V5)과 의미 분리: share만 expire되어도 permission row는 살아있을 수 있음 — `SHARE_EXPIRED` cron(별도 트랙)이 도과 row를 `revoked_at`으로 set + permission row delete. **with-me MVP scope** — `subject_type='user'` 매칭만 결과 포함. department(LTREE 후손 join)/role/everyone subject는 frontend 미사용 + 쿼리 비용 ↑ → 별도 ADR. **folder share endpoint A12 활성화** — `POST /api/folders/:id/share` 도입(`a12-folder-shares-endpoint`). V6 schema 변경 없음 — `ShareCreatedEvent`/`ShareRevokedEvent` 에 `folderId` 필드 추가(file/folder XOR invariant), `ShareCommandService.createFolderShares` 추가(file 변형과 동형, `FolderRepository`로 active 검증), `ShareAuditListener`가 `nodeKey` 분기로 audit_log JSON에 `file_id|folder_id` 출현 — 기존 audit row 호환 유지. by-me/with-me/DELETE는 repository SQL 분기 없이 자연 노출. **SHARE_EXPIRED cron closure (2026-05-01, `share-expired-cron` 트랙)**: `app.share.expiration.*` properties + `ShareExpirationJob @Scheduled(cron)` (운영 기본 5분 주기, default disabled) + `ShareRepository.findExpiredActiveIds(now, Pageable)` 후보 스캔 + `ShareCommandService.expireShare(shareId)` 신규 메서드(=`revokeShare` 형태, `revoked_by=NULL` 시스템 트리거 + `permissions` row delete) + `ShareExpiredEvent` 신규 record + `ShareAuditListener.onShareExpired`가 `AuditEventType.SHARE_EXPIRED` 매핑(`actor_id=NULL`, IP/UA 부재, `metadata.trigger='system.expiration'`). 다중 인스턴스 안전성: V6 row-level pessimistic lock이 동시 expireShare 호출을 직렬화 — 두 번째 인스턴스는 `ResourceNotFoundException` swallow. per-row 실패는 ERROR 로그만 + 다음 row 진행(배치 전체 차단 없음). **permissions-expired-cron closure (2026-05-01, `permissions-expired-cron` 트랙)**: `app.permission.expiration.*` properties + `PermissionExpirationJob @Scheduled(cron)` (운영 기본 5분 주기, default disabled) + `PermissionRepository.lockById(UUID)` + `PermissionRepository.findExpiredActiveIds(now, Pageable)` 후보 스캔 + `PermissionService.expirePermission(permissionId)` 신규 메서드(=`revokePermission` 형태, `actor_id=NULL` 시스템 트리거 + `permissions` row delete) + `PermissionExpiredEvent` 신규 record + `PermissionAuditListener.onPermissionExpired`가 `AuditEventType.PERMISSION_EXPIRED` 매핑(`actor_id=NULL`, IP/UA 부재, `metadata.trigger='system.expiration'`). 다중 인스턴스 안전성: row-level pessimistic lock으로 동시 expirePermission 직렬화 — 두 번째 인스턴스/사용자 직접 revoke와 race 시 `ResourceNotFoundException` swallow. per-row 실패는 ERROR 로그만 + 다음 row 진행. **SHARE_EXPIRED와 차이**: permissions에는 `revoked_at` 컬럼 부재(soft-delete 불가) → DELETE only. `findEffective`가 이미 `expires_at > NOW()` 필터링하므로 cron의 가치는 (a) DB cleanup, (b) `permission.expired` audit row. **SSE emission도 ADR #14 인프라 milestone까지 그대로 deferred.** **A13 closure (2026-05-01, `a13-shares-permissions-join` 트랙)**: F5.1에서 `ShareDto`에 `subjectType`/`subjectId`/`preset` 3 필드를 일시적으로 제거(wire drift)했던 정책을 정정 — backend record에 3 필드 복구 + permissions row를 join해 응답에 surface. POST 응답은 트랜잭션 내 `PermissionRow` 그대로 매핑(추가 SELECT 없음), by-me/with-me는 페이지 결정 후 `permissionRepository.findAllById(ids)` 1회 IN 절 batch (N+1 회피, MAX_LIMIT=100 → 페이지 당 최대 100 IN). V6 FK CASCADE가 active share row의 permission 존재를 보증 — 누락 시 `IllegalStateException`. Frontend `SharesTable`에 `권한` 컬럼 복원(3→4열), `ShareDialog` 기존-share 행에 `subjectLabel · presetLabel` 노출(subject UUID는 머릿8자만, everyone은 "모든 사용자"). DB schema 0 변경. | 02 §2.7/§7.9, 03 §3.1, A10 (`a10-shares`), A12 (`a12-folder-shares-endpoint`), `share-expired-cron`, `permissions-expired-cron`, A13 (`a13-shares-permissions-join`) |
| 35 | **A14 user search endpoint = `GET /api/users/search?q=&limit=` (user-only scope, department/role 필터 deferred, cursor 미지원, `isAuthenticated()` 공개)** | **scope = user only** — share subject picker 용도. `subject_type='department'`/`'role'`/`'everyone'` 4종 중 user 선택만 lookup 필요(department는 docs/02 §2.2 정의되었으나 V_ 마이그레이션 0 → 도메인 부재. role은 enum 고정값. everyone은 literal). 검색 알고리즘 = `LOWER(display_name) LIKE :p ESCAPE '\\' OR LOWER(email) LIKE :p ESCAPE '\\'`로 A9 search와 동일 패턴. **q minLength 2자 (normalize 후) + limit default 20/cap 50** — A9 일관. **LIKE escape (`%` `_` `\` → backslash)** — A9 동형. **400 reuse `INVALID_SEARCH_QUERY`** — 신규 enum 추가 회피. **cursor 미지원** — 검색 결과는 limit 절단(최대 50). subject picker UX는 type-ahead 단축 검색 가정 (q 좁혀가며 50개 이내 수렴). 페이지네이션 필요 시점에 별도 ADR로 nextCursor 도입. **`isAuthenticated()` 공개 정책** — ADR #18(관리자 초대 only 등록)로 사용자 명단 = trust boundary 내부 정보. share subject picker가 admin-only가 아닌 EDIT 보유자(부모 폴더 ADR #28 권한 위임)에게 노출되어야 하므로 ROLE 가드 부적절. **audit emission 없음** — A9 search와 동일 (검색 행위 자체 감사는 보안 트랙 별도). **`department`/`role` 필터 deferred** — frontend 미사용 + V_ migration 부재. 도입 시 controller param + service overload만으로 hook (LIKE WHERE 절 확장). | 02 §7.14, 03 §3, A14 (`a14-user-search`) |

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
