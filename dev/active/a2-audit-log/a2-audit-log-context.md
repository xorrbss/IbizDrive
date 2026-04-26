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
