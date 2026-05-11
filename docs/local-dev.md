# Local Dev Preview 가이드

> **목적**: 다른 세션 작업자(또는 신규 합류자)가 이 문서 한 장만 보고 30분 안에 IbizDrive 풀스택 — frontend (Next.js) + backend (Spring Boot) + PostgreSQL — 을 띄워서 디자인 fidelity와 기능 회귀를 직접 검증할 수 있게 한다.
>
> **scope**: dev 환경 전용. prod 운영 배포는 `application-prod.yml` + `BETA-RELEASE.md` 참조.

---

## 1. 사전 준비

| 도구 | 버전 | 비고 |
|---|---|---|
| **Java** | 21 (Temurin / Zulu / Oracle 무관) | `gradle/wrapper/gradle-wrapper.properties`가 toolchain 자동 다운로드 가능 |
| **Node.js** | 20+ | `frontend/package.json` `engines` 미설정. Next 15.3 요구사항 따름 |
| **pnpm** | 9+ | `corepack enable` 권장 |
| **Docker** | desktop / engine | preview DB 생성 시 `docker run --rm postgres:15 psql ...` 한 줄로만 사용. 영구 데몬 불필요 |
| **PostgreSQL** | 15+ (외부 또는 로컬) | 외부 PG 자격은 사용자에게 문의. CI는 컨테이너 사용 |

레포 클론과 worktree 분리는 `dev/active/dev-preview-stabilization/dev-preview-stabilization-context.md` 의 "빠른 재개" 섹션 참조.

---

## 2. 첫 실행 — preview DB 생성

dev preview는 운영 데이터와 격리된 별도 DB(`ibizdrive_design_preview`)를 사용한다. 외부 PG의 default DB(`postgres`)는 다른 트랙 데이터가 들어 있을 수 있으므로 **건들지 않는다**.

```bash
# 1) PG 자격 환경변수 설정 (한 세션 한정)
export PGHOST=<외부 PG host>          # 예: 115.21.71.140
export PGPORT=<외부 PG port>          # 예: 13401
export PGUSER=postgres
export PGPASSWORD=postgres

# 2) 새 DB 생성 (이미 있으면 에러 → 무시 가능)
docker run --rm -i \
  -e PGPASSWORD=$PGPASSWORD \
  postgres:15 \
  psql -h $PGHOST -p $PGPORT -U $PGUSER -d postgres \
  -c 'CREATE DATABASE ibizdrive_design_preview;'

# 3) 생성 확인
docker run --rm -i \
  -e PGPASSWORD=$PGPASSWORD \
  postgres:15 \
  psql -h $PGHOST -p $PGPORT -U $PGUSER -d ibizdrive_design_preview -c '\dt'
# → "Did not find any relations." (정상, 아직 schema 없음)
```

> Flyway가 backend 첫 부팅 시 V1~V15를 자동 적용하므로 schema/seed를 미리 만들 필요 없다.

로컬 PG로 띄우려면 위 자격 대신 `localhost:5432` + 본인이 만든 DB를 쓰면 된다. `application-local.yml`의 default url이 이미 `jdbc:postgresql://localhost:5432/ibizdrive_design_preview`다.

---

## 3. Backend 기동

### 3.1 단순 실행 (외부 PG, default 자격)

```bash
cd backend

# 외부 PG 자격을 환경변수로 주입
export DB_URL="jdbc:postgresql://115.21.71.140:13401/ibizdrive_design_preview"
export DB_USERNAME=postgres
export DB_PASSWORD=postgres

./gradlew bootRun
```

`./gradlew bootRun`이 `spring.profiles.active=local`을 자동 적용한다 (`backend/build.gradle.kts` bootRun task 설정). 별도로 `SPRING_PROFILES_ACTIVE=local` 같은 env를 줄 필요 없다.

`application-local.yml`이 하는 것:

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 환경변수 폴백 (미설정 시 `localhost:5432/ibizdrive_design_preview` + `postgres/postgres`)
- `org.springframework.security` 로깅 DEBUG (login 401 등 dev 디버깅)

Spring 표준 `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` 도 그대로 동작한다 (Spring property source priority가 yml placeholder를 override).

### 3.2 다른 profile로 띄우기

```bash
# prod 회귀 테스트 (cron 활성, secure cookie)
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun

# 또는 -D 플래그
./gradlew bootRun -Dspring.profiles.active=prod
```

env 또는 -D가 명시되어 있으면 build.gradle.kts의 default(local)는 적용되지 않는다.

### 3.3 부팅 성공 신호

```
2026-MM-DD HH:MM:SS  INFO ... Started IbizdriveApplication in 8.5 seconds
2026-MM-DD HH:MM:SS  INFO ... Tomcat started on port 8080
2026-MM-DD HH:MM:SS  INFO ... Flyway ... Successfully applied 15 migrations
```

V1~V15 적용은 빈 DB에서 첫 부팅 1회만. 두 번째부터는 "no migration necessary".

---

## 4. Frontend 기동

```bash
cd frontend
pnpm install --prefer-offline   # lock 변경 없으면 매우 빠름
pnpm dev
```

기본 포트 3000, 점유 시 자동으로 3001로 시프트. 콘솔이 최종 URL을 출력한다.

`/api/*`는 `next.config.ts` rewrites로 `http://localhost:8080`로 프록시된다 (동일 origin 가정 유지). `BACKEND_URL` env로 override 가능.

---

## 5. 시드 데이터 (선택)

빈 DB로도 signup → login → `/files` 진입 자체는 동작하지만, 부서·팀·파일이 없어 사이드바·FileTable이 비어 보인다. 디자인 fidelity 시각 검증(G1/G5/G6/G8)을 위해서는 시드가 필요하다.

```bash
# 시드 SQL (idempotent, ON CONFLICT DO NOTHING)
docker run --rm -i \
  -e PGPASSWORD=$PGPASSWORD \
  -v "$(pwd)/dev/preview:/seed" \
  postgres:15 \
  psql -h $PGHOST -p $PGPORT -U $PGUSER -d ibizdrive_design_preview \
  -f /seed/seed.sql
```

> **상태**: `dev/preview/seed.sql` 은 dev-preview-stabilization **T5 산출물**. 작성 전이면 이 단계는 skip 가능 — signup으로 admin 1명만 만들고 빈 화면 진입만 검증.
>
> 진행 상황: `dev/active/dev-preview-stabilization/dev-preview-stabilization-tasks.md` T5 블록.

### 5.1 빈 DB에서 admin 만들기 (시드 없이)

```bash
# CSRF 토큰 발급
curl -s -c /tmp/c.txt http://localhost:8080/api/auth/csrf > /dev/null
T=$(grep XSRF-TOKEN /tmp/c.txt | awk '{print $7}')

# Signup — backend는 헤더 이름 `X-CSRF-Token` 을 기대 (Spring 기본값 X-XSRF-TOKEN과 다름).
# 자세한 배경은 §6.2 트러블슈팅 + dev/active/dev-preview-stabilization/T1-finding.md 참조.
curl -i -s -b /tmp/c.txt -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" -H "X-CSRF-Token: $T" \
  -d '{"email":"admin@local.test","password":"AdminPass123","displayName":"Local Admin"}'
# → HTTP 201 + SESSION 쿠키
```

frontend signup 폼에서도 동일하게 가능. 권한 ADMIN 부여는 별도(직접 DB UPDATE 또는 admin grant flow).

---

## 6. 트러블슈팅

### 6.1 backend 부팅 시 `V13__... migration failed: column "scope_type" contains null values`

**원인**: 다른 트랙 데이터가 있는 DB(예: 외부 PG default `postgres`)에 V13을 적용하려 함. 기존 row가 NOT NULL 제약을 만족하지 못함.

**대응**: 그 DB를 건들지 말고, `ibizdrive_design_preview` 같은 신규 DB로 부팅. §2 절차 참조.

### 6.2 `/api/auth/login` 이 빈 body 401 (controller 도달 전)

**원인 (T1 진단 결과 — `dev/active/dev-preview-stabilization/T1-finding.md`)**: `SecurityConfig` 가 CSRF 헤더 이름을 **`X-CSRF-Token`** 으로 설정했는데(`backend/src/main/java/com/ibizdrive/config/SecurityConfig.java`), 기존 빠른 재개 curl과 일부 클라이언트가 Spring 기본값인 `X-XSRF-TOKEN` 을 쓰고 있어 토큰을 못 읽음 → `InvalidCsrfTokenException` → `AccessDeniedException` → 익명 사용자라 `HttpStatusEntryPoint(UNAUTHORIZED)` 가 **401 빈 body** 응답. 추가로 `docs/02 §7.4` / `docs/03 §1.3` 가 명시한 `403 + {"code":"CSRF_MISMATCH"}` 계약이 미구현이라 디버그가 어려움.

**대응**:
- curl로 직접 호출 시 헤더 이름을 **`X-CSRF-Token`** 으로 사용 (§5.1 예시 참조).
- frontend는 `frontend/src/lib/csrf.ts` 등에서 동일 이름을 쓰므로 정상 (frontend signup 폼은 정상 동작 보고 있음).
- backend 측 fix(403 매핑 + 계약 정렬)는 **T2** — `dev/active/dev-preview-stabilization/dev-preview-stabilization-tasks.md` T2 블록. fix 머지 후 본 섹션을 닫는다.

**임시 우회**: signup 직후 session이 자동 발급되므로, login 폼을 거치지 않고 signup → 즉시 `/files` 진입은 가능. UI fidelity 시각 검증만 필요하면 이 경로로 진행.

### 6.3 frontend가 `ECONNREFUSED 127.0.0.1:8080`

**원인**: backend가 안 떠 있거나, 다른 포트로 떠 있음.

**대응**:
- backend 콘솔에 `Tomcat started on port 8080` 라인 확인
- `lsof -i :8080` (또는 Windows `netstat -ano | findstr :8080`) 로 점유 프로세스 확인
- backend가 다른 포트면 frontend `BACKEND_URL=http://localhost:<port> pnpm dev`

### 6.4 frontend의 `/api/*`가 404 / CORS 오류

**원인**: dev에서는 `next.config.ts` rewrites가 proxy하므로 CORS 발생 자체가 비정상. 다른 origin (예: 3001 → 8080 직접 호출)에서 호출했거나, rewrites가 적용 안 된 build artifact를 띄움.

**대응**: dev 서버를 통해 (`http://localhost:300x/api/...`) 호출. `pnpm build && pnpm start`(prod build)는 reverse proxy 별도 설정 필요.

### 6.5 dev 도중 `application-local.yml`이 안 먹는 것 같음

확인:
```bash
./gradlew bootRun --info 2>&1 | grep -i "active profile"
# → "The following 1 profile is active: \"local\""
```

`SPRING_PROFILES_ACTIVE` 환경변수가 다른 값으로 set되어 있으면 그쪽이 우선. `unset SPRING_PROFILES_ACTIVE` 후 재시도.

---

## 7. 정리

```bash
# backend / frontend 종료 (Ctrl+C 또는 pkill)

# preview DB drop (다른 세션 영향 가능 — 사용자 컨펌 후)
docker run --rm -i \
  -e PGPASSWORD=$PGPASSWORD \
  postgres:15 \
  psql -h $PGHOST -p $PGPORT -U $PGUSER -d postgres \
  -c 'DROP DATABASE ibizdrive_design_preview;'

# worktree 제거 (해당 worktree 안에서가 아닌 main repo에서 실행)
git worktree remove C:/project/IbizDrive/.claude/worktrees/<task-slug>
```

> DB drop은 다른 세션(특히 T5 시드 작성 중)에 영향 가능. 단독 사용이 확실할 때만, 사용자 컨펌 받고 진행.

---

## 8. Backlinks

- 트랙 상위 plan: `dev/active/dev-preview-stabilization/dev-preview-stabilization-plan.md`
- 트랙 tasks (T1~T6): `dev/active/dev-preview-stabilization/dev-preview-stabilization-tasks.md`
- 트랙 context (SESSION PROGRESS): `dev/active/dev-preview-stabilization/dev-preview-stabilization-context.md`
- prod profile 가이드: `backend/src/main/resources/application-prod.yml`, `BETA-RELEASE.md`
- ADR 인덱스: `docs/00-overview.md` §5
- frontend proxy 설정: `frontend/next.config.ts`
- backend bootRun task 설정: `backend/build.gradle.kts` (bootRun block)
- backend local profile: `backend/src/main/resources/application-local.yml`
