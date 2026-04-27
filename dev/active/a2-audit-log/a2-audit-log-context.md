---
Last Updated: 2026-04-27
---

# A2 Audit Log — Context

## 트랙 결정 5+1 (확정)

| # | 결정 | 근거 요약 |
|---|---|---|
| 1 | AOP `@Audited` + Security Event listener 하이브리드 | AuthService 침투 0, 어노테이션으로 grep 가능, 트랜잭션 롤백 자동 처리. ADR #24 |
| 2 | 보존 3년 + Legal Hold 무기한 + 월별 파티션 (MVP 단일) | docs/03 §4.3 명시값 채택, ADR 신규 등록 불필요 |
| 3 | DB role 분리 + REVOKE UPDATE/DELETE + RED 42501 | docs/02 §2.8 SQL 그대로, app_user/audit_admin/db_superuser 3 role. ADR #25 |
| 4 | role 기반 read (`ADMIN`/`AUDITOR` 전체, `MEMBER` self) | A1 V2 Role enum 재사용, A3 권한 시스템 비의존 |
| 5 | 백엔드 enum = 단일 진실, ts mirror | docs/00 §3.4 계약점 그대로, MVP는 수동 + CI lint |
| 6 | frontend mock → fetch 교체 (api.getAuditLogs only) | UI/테스트 변경 0, M12 표면 보존 |

## 핵심 참조

- **docs/00 §5 ADR**: #18 (MVP 인증 범위), #20 (lockout/세션) — 인증 이벤트 5종 명세 근거
- **docs/02 §2.8**: audit_log 스키마 + REVOKE 정책 (코드로 구현 대상)
- **docs/02 §9.4**: 월별 파티셔닝, INSERT-only 부하, read replica
- **docs/03 §4.1**: 41개 이벤트 타입 + frontend 추가 1건(`audit.exported`) = 42개
- **docs/03 §4.4**: 불변성 강제 — application role과 admin role 분리
- **frontend/src/types/audit.ts**: 백엔드 enum 작성 시 1:1 대조 reference. 정확히 38 값 (file 8 / version 3 / folder 6 / permission 3 / share 3 / user 5 / admin 7 / system 2 / audit 1)
- **frontend/src/lib/api.audit.test.ts**: A2.3 read API 계약 (1-indexed page, occurredAt DESC, inclusive 날짜, 부분 actorName)

## A1 교훈 (처음부터 적용)

1. **`@WebMvcTest` 슬라이스 선제** — 로딩 빠름, 권한·JSON 검증에 충분. 진짜 통합은 `@SpringBootTest` 1~2개만.
2. **`TestRestTemplate` + HttpClient5** — JDK HttpURLConnection은 401 응답 후 streaming POST body 재전송 불가 (A1 682a34c). 본 트랙은 read-only API라 영향 적지만 통합 테스트에서 401 검증 시 적용.
3. **Spring Session JDBC 영향 entity는 처음부터 `Serializable`** — A1 967b0bc 교훈. AuditLog는 Spring Session에 안 들어가지만 사용자 컨텍스트(`IbizDriveUserDetails`)가 attribute에 저장됨 → 이 클래스에 추가 필드 시 직렬화 호환성 검증.
4. **CORS @ConfigurationProperties** — A1 42dcd3e. 본 트랙에서 CORS 변경 없음 (read API는 동일 origin 가정). 변경 시 `CorsProperties` 재검증.
5. **Testcontainers 의존성** — Docker Desktop 필요. CI는 ubuntu-latest로 그린. 로컬 Windows에서 Docker 부재 시 `@SpringBootTest` 통합만 skip되고 `@WebMvcTest`는 동작.

## 함정/주의

### 1. `target_type` CHECK 제약과 frontend `AuditResourceType` 불일치
docs/02 §2.8 CHECK = `('file', 'folder', 'user', 'permission', 'share', 'system')` (6개). frontend `AuditResourceType`은 `audit` 추가(7개). **V3에서 CHECK도 7개로 넓힘** — `audit.exported` 이벤트가 self-reference. 마이그레이션 + docs/02 §2.8 동기 갱신.

### 2. AOP + 트랜잭션 경계
`@Audited`가 `@AfterReturning`이라 비즈니스 트랜잭션이 커밋된 후에 audit row가 insert됨 → 비즈니스 성공이지만 audit insert 실패하는 순간 발생 가능. 처리:
- audit insert는 별도 `REQUIRES_NEW` 트랜잭션
- 실패 시 ERROR 로그 + Sentry/CloudWatch alert (MVP는 로그만)
- 데이터 무결성 vs 가용성 트레이드오프 — 보안팀 합의 필요시 `@Around` + 동일 트랜잭션으로 변경

### 3. Frontend mock의 `id: string` vs DB `id: BIGSERIAL`
프론트는 string ID 가정, 백엔드는 BIGSERIAL. A2.3에서 응답 직렬화 시 `Long.toString()`. ts 타입 변경 없음.

### 4. `actorIp` IPv6
`HttpServletRequest.getRemoteAddr()`는 reverse proxy 뒤에서 lb IP 반환 → `X-Forwarded-For` 우선 처리. Spring `ForwardedHeaderFilter` 활용 검토. MVP는 `getRemoteAddr()` 그대로, A2.5 통합 테스트에서 실제 IP 검증 후 보강.

### 5. `audit.exported` 이벤트 emit 시점
A2 범위 밖 (관리자 export 기능 자체가 v1.x). enum에는 추가하되 emit 코드는 작성 안 함. frontend `auditCsv.ts`(이미 mock에 존재)가 A2.6에서 어떻게 처리되는지 확인 필요 — export 시 frontend가 `record()` 별도 호출 또는 endpoint가 자동 emit. **임시 결정**: csv export endpoint 자체가 v1.x → 본 트랙은 enum 정의만.

## 롤백 경로

- **backup branch**: `backup/pre-reset-20260427-0036` (origin push 완료)
- **backup hash**: `132170ed15b2d8e07b7dd6b7de7033942047c84e` (reset 직전 a2 브랜치)
- **rollback 명령**:
  ```bash
  git fetch origin
  git reset --hard origin/backup/pre-reset-20260427-0036
  ```
- **무엇을 잃지 않음**: backup의 코드 변경분은 origin/master(`eda6f75`) squash에 모두 포함됨. backup은 commit message granularity 보존용.

## 외부 의존

- **PostgreSQL `42501`** — REVOKE 동작 검증의 표준 SQLState. `org.postgresql.util.PSQLException.getSQLState()`로 추출.
- **Testcontainers PostgreSQL** — V3, V4 마이그레이션이 적용되는지 확인하기 위해 fresh container.
- **Spring Security event publishing** — `AuthenticationSuccessEvent`, `AbstractAuthenticationFailureEvent`, `LogoutSuccessEvent`. Spring Boot autoconfig가 자동 등록.

## SESSION PROGRESS

### Session 1 (2026-04-27) — 셋업 + A2.0 + A2.1a (CI red 종료)

**완료**:
- worktree reset → origin/master(eda6f75) 정렬, backup 브랜치 push
- 메모리 2건 갱신 (자율 루프 + 컨텍스트 한계 프로토콜)
- dev-docs 3파일 작성 — commit `a6076f0`
- ADR #24, #25 — commit `a6076f0`
- A2.0 V3+V4 + RED+GREEN — commit `440b0b0` ✅ CI 24960561196
- A2.1a AuditService + Enums — commit `fd28368` ❌ CI 24960745059
- handoff snapshot — commit `344afa6`

### Session 2 (2026-04-27) — A2.1a fix (CI green)

**완료**:
- 메모리 추가 (Context savings protocol — 능동 절약 13규칙)
- A2.1a CI 실패 분석 → root cause 재정의:
  - 1차 진단(이전 세션): "users row seed 부재" → **틀림**. 테스트에 이미 seedUser 존재.
  - 실제 원인: `@DataJpaTest` outer 트랜잭션 안의 INSERT는 commit 전 → `AuditService.record()`의
    `@Transactional(REQUIRES_NEW)`가 잡는 별도 connection에서 row 미가시 → FK 23503 위반.
  - 부수 원인: `actor_ip` assertion이 `"203.0.113.42/32"` 기대 → PostgreSQL inet 타입은 host
    주소(/32)일 때 mask 생략 → 실제 `"203.0.113.42"`. CIDR 표기는 명시 입력 시에만 보존.
- fix 1: `seedUser` body를 `TransactionTemplate(REQUIRES_NEW)`로 즉시 commit — commit `1196e11`
- fix 2: inet assertion 교정 — commit `cf0be93`
- CI run 24961391600 ✅ green (170 tests, 0 failed)

**TDD 상태**: A2.1a GREEN 통과. A2.1b RED 진입 가능.

**uncommitted**: 없음. dev/process/a2-audit-log-s2.md는 작업 종료 시 삭제 예정.

**다음 액션**:
1. A2.1b RED — `AuditedAspectTest` 작성:
   - `@Audited` 메서드 정상 종료 → `record()` 호출 1회 (Mockito spy)
   - 메서드 throw → `record()` 호출 0회
   - SpEL target ID 추출 (`#fileId`, `#result.id`)
2. A2.1b GREEN — `@Audited` annotation, `AuditedAspect`, `WebRequestContextHolder`
3. commit `feat(A2.1b): @Audited AOP + WebRequestContext` → CI 확인

**learnings (다음 작업에 적용)**:
- `@DataJpaTest` + `REQUIRES_NEW` 조합은 항상 visibility 함정. seed는 별도 트랜잭션에서 commit.
- inet/cidr/macaddr 같은 PostgreSQL native 타입의 텍스트 표현은 실제 값 SELECT로 검증할 것
  (가정 금지). 비슷한 함정: jsonb whitespace, timestamptz timezone 표기.

### Session 3 (2026-04-27) — A2.1b @Audited AOP (CI green)

**완료**:
- `Audited` annotation (event/targetType/target SpEL)
- `AuditedAspect` — `@AfterReturning("@annotation(audited)")` + `MethodBasedEvaluationContext`
  - 메서드 정상 종료 → `AuditService.record()` 호출
  - throw → 호출 안 됨 (성공한 액션만 기록)
  - audit 실패는 ERROR 로그 swallow (비즈니스 결과 보호, REQUIRES_NEW로 트랜잭션 격리)
- `WebRequestContextHolder` — `RequestContextHolder`에서 IP/UA 추출, 비-HTTP 컨텍스트는 null
- `AuditedAspectTest` — `AspectJProxyFactory` + Mockito mock으로 단위 검증 (3 cases all pass)
- spring-boot-starter-aop 의존성 추가
- A2.0 tasks 체크박스 정리 (440b0b0 commit 반영)
- commit `a0f9f7e` push, CI run 24982973669 ✅ green

**TDD 상태**: A2.1b GREEN 통과. 다음 사이클: A2.3 read API + 권한 (Spec 4번 결정).
A2.2는 A2.0에서 이미 RED→GREEN 통과했으므로 별도 사이클 불필요 (또는 단순 verification만).

**uncommitted**: 없음. dev/process/a2-audit-log-s3.md는 작업 종료 시 삭제 예정.

**다음 액션 (A2.3)**:
1. RED: `AuditQueryControllerTest` (`@WebMvcTest`) — 권한 매트릭스 (ADMIN/AUDITOR/MEMBER/익명)
2. RED: 필터 테스트 — eventType/actorQuery/fromDate/toDate/page/pageSize, frontend api.audit.test.ts 1:1 계약
3. GREEN: `AuditQueryController`, `AuditQueryService`, DTOs (`AuditLogPageDto`, `AuditLogEntryDto`)
4. commit `feat(A2.3): GET /api/admin/audit + role-based scope`

**A2.1b 설계 노트**:
- `@AfterReturning`만 부착 — `@AfterThrowing` 의도적으로 생략. 정책 = "성공한 액션만 기록".
- `MethodBasedEvaluationContext` 사용 → 인자 이름(`#fileId`)과 `#result` 양쪽 자동 노출.
  ParameterNameDiscoverer 필요 — `DefaultParameterNameDiscoverer`(JDK 8+ -parameters 또는 디버그 정보)로 충분.
- 단위 테스트가 `AspectJProxyFactory` 기반이라 Spring 컨텍스트 부팅 없이 빠르게 돈다 (수 초 이내).
  통합 테스트 (실제 SecurityContext + WebRequestContext)는 A2.4/A2.5에서 listener+E2E 시 함께 검증.

### Session 4 (2026-04-27) — A2.3 Read API + 권한 매트릭스

**완료**:
- `AuditQueryFilters` (record): fromDate/toDate/actorQuery/eventType nullable
- `AuditLogEntryDto`, `AuditLogPageDto` (records, frontend `AuditLogEntry`/`Page`와 wire 동치)
  - `id: String` (BIGSERIAL → string), UUID 직렬화, `OffsetDateTime` ISO 8601, JSONB → `Map`
- `AuditQueryService.search(filters, page, pageSize, viewerId, viewerRole)`:
  - JdbcTemplate + 동적 WHERE (Specification 대신 KISS — 6개 필터 한정)
  - MEMBER scope 강제 (`actor_id = ?`), ADMIN/AUDITOR 전체
  - 날짜: `fromDate >= 자정 UTC`, `toDate < 다음날 자정 UTC` (inclusive 양 끝)
  - actorQuery: `LOWER(display_name) LIKE %q%` (CI 부분매칭)
  - 정렬: `occurred_at DESC, id DESC`, page 1-indexed, pageSize 1..200 클램프
  - metadata: `::text` SELECT → ObjectMapper로 `Map<String, Object>` 역직렬화 (실패 시 빈 맵 폴백)
- `AuditQueryController` `GET /api/admin/audit?fromDate&toDate&actorQuery&eventType&page&pageSize`
  - 빈 문자열 필터 → null 정규화 (프론트 `'' = 전체` 시그니처)
  - `@AuthenticationPrincipal IbizDriveUserDetails` → `getUser().getId()/getRole()`
- `AuditQueryControllerTest` (`@WebMvcTest`): 8 케이스 — 익명 401, ADMIN/AUDITOR/MEMBER scope 인자 캡처,
  default page=1/size=20, custom params 통과, blank → null, JSON shape (frontend `AuditLogEntry` 1:1)
- `AuditQueryServiceTest` (`@DataJpaTest` + Testcontainers): 9 케이스 — 권한 3종, DESC 정렬, eventType,
  actorQuery CI partial, 날짜 inclusive, 1-indexed pagination, 빈 결과
- docs/02 §7.12: `/api/admin/audit-logs` → `/api/admin/audit`, guard `isAuthenticated()` (MEMBER scope=self)
- 로컬 검증: controller test PASS (Docker 불필요). service test는 CI에서 Docker 가용 시 실행
  (`@Testcontainers(disabledWithoutDocker = true)`)

**TDD 상태**: A2.3 GREEN 통과 (controller slice 로컬 검증). service test는 CI 결과로 확정.
다음 사이클: A2.4 — A1 인증 이벤트 emission (Listener), AuthService 침투 0 강제.

**uncommitted**: 모든 변경 + dev/process/a2-audit-log-s4.md (commit 후 삭제).

**다음 액션 (A2.4)**:
1. RED: `AuthAuditListenerTest` — `AuthenticationSuccessEvent`, `BadCredentialsEvent`,
   `AuthenticationFailureLockedEvent`, `LogoutSuccessEvent` → 각각 audit_log row 검증
2. RED: `git diff` — `AuthService.java`, `LoginAttemptTracker.java`, `AuthController.java` 변경 0줄
3. GREEN: `AuthAuditListener` (`@EventListener`) — actor 추출 (success는 principal, failed는 email→users.id lookup)
4. commit `feat(A2.4): A1 auth events → audit_log (listener only)`

**A2.3 설계 결정 노트**:
- `JpaSpecification` 대신 JdbcTemplate 동적 WHERE — write 경로(AuditService)와 스택 통일 + KISS
- 권한 분기는 service 단일 진입점 (controller `@PreAuthorize` 미사용) — 트랙결정 #4가 service 책임
- `@WebMvcTest` slice는 Mockito ArgumentCaptor로 service 호출 인자 검증 (실제 SQL은 별도 통합 테스트)
- Testcontainers 분리 (`disabledWithoutDocker`) → 로컬 빠른 피드백 + CI 정확도 양립
