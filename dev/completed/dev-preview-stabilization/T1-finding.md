# T1 — Login 401 진단 결과

**Last Updated:** 2026-05-10
**Worktree:** `C:/project/IbizDrive/.claude/worktrees/login-401-fix`
**Branch:** `feat/login-401-fix`

> **Note (2026-05-10):** 이 트랙의 plan/context/tasks 3개 docs는 다른 세션이 `feat/preview-seed` 브랜치로
> 옮겨 commit했다 (`de6d21d feat(dev-preview): T5 idempotent seed SQL + README + dev-docs bootstrap`).
> 본 T1-finding.md는 feat/login-401-fix 브랜치 commit으로 트래킹된다. T1/T2 체크박스 업데이트는
> feat/preview-seed 머지 후 또는 별도 followup PR에서 진행 (co-session 충돌 회피).

## 결론 (TL;DR)

`POST /api/auth/login`이 **빈 body 401**을 반환하는 직접 원인은 **CSRF 토큰 검증 실패 → `AccessDeniedHandlerImpl.sendError(403)` → Spring Boot의 `/error` ErrorPage forward → forwarded 요청에서 익명 사용자 `AuthorizationFilter` deny → `ExceptionTranslationFilter` → `HttpStatusEntryPoint(UNAUTHORIZED)` → 401 빈 body** 위장이다.

진짜 결함은 **(a) CSRF 실패가 401로 위장되어 디버그 불가**, **(b) docs/02 §7.4·docs/03 §1.3 계약(`403 + {"code":"CSRF_MISMATCH"}`)이 미구현**.

context.md 빠른 재개 안내의 curl이 사용한 헤더 이름 `X-XSRF-TOKEN`(Spring default)은 backend가 기대하는 `X-CSRF-Token`과 **이름이 다른 헤더**("XSRF" vs "CSRF"). HTTP는 case-insensitive이지만 단어 자체가 다르다. CsrfFilter가 토큰을 못 읽고 `InvalidCsrfTokenException`을 발생시켜 위장 흐름이 트리거된다.

## 코드 경로

### 1. Backend가 기대하는 헤더 이름
`backend/src/main/java/com/ibizdrive/config/SecurityConfig.java:64-69`
```java
@Bean
public CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repo.setHeaderName("X-CSRF-Token");   // ← Spring default(X-XSRF-TOKEN)에서 변경
    return repo;
}
```

### 2. login은 CSRF 면제 대상이 **아님**
`backend/src/main/java/com/ibizdrive/config/SecurityConfig.java:115-117`
```java
.ignoringRequestMatchers("/api/auth/signup",
                         "/api/auth/password/forgot",
                         "/api/auth/password/reset"))
```
→ login은 빠져 있음. 의도적 (login CSRF 방어). signup은 면제 대상이라 정상 동작 (HTTP 201).

### 3. CSRF 실패의 1차 처리
Spring Security 6.3.3 `CsrfFilter.doFilterInternal`은 토큰 mismatch 시 자체 `accessDeniedHandler`를 호출하고 chain을 종료한다 (이때 `ExceptionTranslationFilter`는 거치지 않는다):

```java
if (!equalsConstantTime(csrfToken.getToken(), actualToken)) {
    AccessDeniedException exception = !missingToken
        ? new InvalidCsrfTokenException(...)
        : new MissingCsrfTokenException(actualToken);
    this.accessDeniedHandler.handle(request, response, exception);
    return;
}
```

기본 `AccessDeniedHandlerImpl.handle()`은 `response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden")`을 호출. 여기까진 클라이언트가 받을 응답이 403이어야 한다.

### 4. Spring Boot ErrorPage forward가 위장 흐름을 만든다
Tomcat은 `sendError`를 가로채 `/error`로 forward한다. forwarded 요청은:
1. **새 SecurityFilterChain 경유** — SESSION/Authentication이 없는 forwarded 요청이라 익명 처리.
2. `AuthorizationFilter`가 `/error`를 `anyRequest().authenticated()` 매처로 검사 → `AccessDeniedException("Access Denied")`.
3. `ExceptionTranslationFilter.handleAccessDeniedException`은 익명 사용자를 detect하고 `InsufficientAuthenticationException`으로 wrap → `sendStartAuthentication` → `HttpStatusEntryPoint.commence(UNAUTHORIZED)`.
4. 응답: 401, body 미작성.

### 5. ErrorResponse 미구현
`backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java:13` 주석:
> `403` → `{"code": "CSRF_MISMATCH"}` (Spring Security가 직접 발급)

이 계약은 docs로 명시되어 있으나 실제 코드는 미구현. AuthExceptionHandler/GlobalExceptionHandler 어디에도 `BadCsrfTokenException`/`InvalidCsrfTokenException` 매핑 없음. Spring Security가 직접 응답을 발급하는 entry point/handler도 등록되지 않아 default empty-body 401로 떨어짐.

### 6. Frontend는 정합
`frontend/src/lib/api.ts:1238-1249` (login 함수): `'X-CSRF-TOKEN': csrf` 송신 (대소문자 무관, backend 기대 `X-CSRF-Token`과 동일 헤더로 인식).

### 7. 사용자 curl이 사용한 헤더는 다른 헤더
context.md 빠른 재개 안내: `-H "X-XSRF-TOKEN: $T"` (Spring default). backend는 `X-CSRF-Token` 기대 — `X-XSRF-TOKEN`과는 이름 자체가 다른 헤더 ("XSRF" vs "CSRF"). CsrfFilter가 토큰을 못 읽어 §3 흐름.

## 재현·검증 로그 (실측 2026-05-10 20:29)

backend boot:
```
SPRING_DATASOURCE_URL=jdbc:postgresql://115.21.71.140:13401/ibizdrive_design_preview \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres \
./gradlew bootRun \
  --args='--logging.level.org.springframework.security=DEBUG \
          --logging.level.org.springframework.security.web.csrf=TRACE \
          --logging.level.org.springframework.security.web.access=TRACE'
```

Spring Security 6.3.3. SecurityFilterChain:
```
DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter, SecurityContextHolderFilter,
SessionValidityFilter, HeaderWriterFilter, CsrfFilter, RequestCacheAwareFilter,
SecurityContextHolderAwareRequestFilter, AnonymousAuthenticationFilter,
SessionManagementFilter, ExceptionTranslationFilter, AuthorizationFilter
```

### 시나리오 A — `X-XSRF-TOKEN` (Spring default 헤더, 사용자 curl 그대로)
응답:
```
HTTP/1.1 401
Content-Length: 0
```
로그:
```
DEBUG o.s.security.web.FilterChainProxy        : Securing POST /api/auth/login
DEBUG o.s.security.web.csrf.CsrfFilter         : Invalid CSRF token found for http://localhost:8080/api/auth/login
DEBUG o.s.s.w.access.AccessDeniedHandlerImpl   : Responding with 403 status code
DEBUG o.s.security.web.FilterChainProxy        : Securing POST /error                ← Spring Boot ErrorPage forward
TRACE o.s.s.w.a.AnonymousAuthenticationFilter  : Set SecurityContextHolder to AnonymousAuthenticationToken
TRACE estMatcherDelegatingAuthorizationManager : Authorizing POST /error
TRACE estMatcherDelegatingAuthorizationManager : Checking authorization on POST /error using AuthenticatedAuthorizationManager
TRACE o.s.s.w.a.ExceptionTranslationFilter     : Sending AnonymousAuthenticationToken ... to authentication entry point since access is denied
org.springframework.security.access.AccessDeniedException: Access Denied
    at o.s.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:98)
    at o.s.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
    ...
```

### 시나리오 B — `X-CSRF-Token` (정합 헤더)
응답:
```
HTTP/1.1 200
Set-Cookie: SESSION=...; Path=/; HttpOnly; SameSite=Lax
{"user":{"id":"01ebb04f-...","email":"preview@local.test","name":"Preview","kind":"human","mustChangePassword":false},"departments":[],"roles":["MEMBER"],"effectivePermissionsCacheKey":"51059c8b4b79e5c4"}
```
→ 정상 로그인. 시드 4개 계정의 비밀번호 hash, AuthService.login, establishSession 모두 정상.

### 시나리오 C — CSRF 헤더 완전 누락
응답: `HTTP/1.1 401, Content-Length: 0` (시나리오 A와 동일). 로그도 동일 흐름.

## 401 빈 body의 정확한 출처 (실측 확정)

```
1. CsrfFilter
   → InvalidCsrfTokenException (또는 MissingCsrfTokenException)
   → this.accessDeniedHandler.handle(req, res, ex)             [CsrfFilter.java]
2. AccessDeniedHandlerImpl.handle(req, res, ex)
   → response.sendError(403, "Forbidden")                       [AccessDeniedHandlerImpl.java]
3. Tomcat ErrorPageFilter — sendError 콜에 대응해 forward(/error) [Spring Boot 기본]
4. /error 재경유 SecurityFilterChain
   → 새 SecurityContext (forward라 SESSION/Authentication 없음)
   → AnonymousAuthenticationFilter: anonymous principal
   → AuthorizationFilter: /error는 anyRequest().authenticated() 매처에 걸림 → AccessDeniedException
5. ExceptionTranslationFilter
   → 익명 사용자 → InsufficientAuthenticationException으로 wrap
   → HttpStatusEntryPoint.commence(UNAUTHORIZED)               [SecurityConfig.java:144]
6. response.setStatus(401) → body 미작성 → Content-Length: 0
```

## 도입 commit

`SecurityConfig.csrfTokenRepository()`의 `setHeaderName("X-CSRF-Token")` 변경:
- `git blame -L 66,67 backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` →
  `ee7781ba (xorrbss 2026-05-07 12:59:55 +0900) repo.setHeaderName("X-CSRF-Token");`
- commit ee7781b: `fix(auth-csrf-audit): CSRF header alignment + signup audit FK regression` — 의도적 정합 변경 (Spring default `X-XSRF-TOKEN`을 docs/02 §7.1·docs/03 §1.3 계약 `X-CSRF-Token`로 align). Frontend는 정합 헤더 사용 → 본 변경은 **결함 도입이 아니라 frontend 정렬**.

→ 401 빈 body 위장은 **본 commit과 무관한 별도의 잠재 결함**. frontend가 정합 헤더를 보내면 동작하지만 외부 도구·디버그 시 default 헤더(X-XSRF-TOKEN)를 보낼 때 디버그 불가능한 빈 401로 떨어진다.

## 잘못된 메타정보 정정 (followup 후보)

- context.md 빠른 재개 안내 curl이 `-H "X-XSRF-TOKEN: $T"`를 사용. backend 기대 헤더 `X-CSRF-Token`로 정정 필요. T6 (docs/local-dev.md) 작성 시 같은 정정 적용.
- context.md "controller 도달 전, backend log에 어떤 라인도 안 찍힘" 표현 — INFO 레벨에선 silent 정상. 가설은 옳음.

## T2 적용 fix

**채택안 — Custom AccessDeniedHandler** (`backend/src/main/java/com/ibizdrive/config/CsrfAwareAccessDeniedHandler.java`):

```java
public class CsrfAwareAccessDeniedHandler implements AccessDeniedHandler {
    private final AccessDeniedHandler delegate = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) throws IOException, ServletException {
        if (ex instanceof CsrfException) {
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write("{\"code\":\"CSRF_MISMATCH\"}");
            return;
        }
        delegate.handle(req, res, ex);
    }
}
```

`SecurityConfig`:
```java
.exceptionHandling(eh -> eh
    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
    .accessDeniedHandler(csrfAwareAccessDeniedHandler))
```

**효과**:
- CSRF 실패 → 직접 403 + JSON body. `sendError` 미호출 → ErrorPage forward 회피 → 빈 401 위장 제거.
- docs/02 §7.4 + docs/03 §1.3 `CSRF_MISMATCH` envelope 계약 실제 구현.
- 인증된 사용자의 권한 부족(CsrfException 아닌 AccessDeniedException)은 controller 단까지 도달 후 GlobalExceptionHandler가 받아 PERMISSION_DENIED 발급 (기존 동작 유지).
- 정합 헤더로 보내는 frontend는 영향 없음.

**기각안 — Custom AuthenticationEntryPoint**: ExceptionTranslationFilter를 거치지 않으므로 효과 없음.
**기각안 — `setHeaderName` 제거**: frontend 영역 건드리지 마는 사용자 제약 위반.

## 회귀 가드 (T2 추가 테스트)

### MockMvc-level 강화
- `LoginControllerIntegrationTest.login_withoutCsrf_returns403CsrfMismatch` — body assert 추가.
- `AuthMeLogoutIntegrationTest.logout_authenticatedWithoutCsrf_returns403CsrfMismatch` — body assert 추가.
- `SecurityIntegrationTest.postWithoutCsrf_returns403CsrfMismatch` — body assert 추가.

이전 버전(status만 검사)은 MockMvc가 servlet container의 ErrorPage forward를 흉내내지 않아 production이 빈 401을 반환하는 사이에도 PASS하던 false-positive였다. body assert가 회귀 가드 역할.

### Tomcat-level 신규
- `LoginCsrfRegressionIntegrationTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers(disabledWithoutDocker = true)`) — 4 시나리오:
  1. CSRF 헤더 누락 → 403 + CSRF_MISMATCH
  2. CSRF 헤더 잘못된 이름 (`X-XSRF-TOKEN`) → 403 + CSRF_MISMATCH
  3. CSRF 정합 헤더 → 200 + LoginResponse
  4. CSRF 정합 + 잘못된 PW → 401 + INVALID_CREDENTIALS (기존 envelope 보존)

- 로컬 Windows에서 Docker Desktop + testcontainers 비호환으로 4건 SKIPPED (memory: `Docker Desktop Windows + Testcontainers`). CI ubuntu-latest에서 grееn 검증 필수.

## 검증 결과

- `./gradlew test` (전체 backend test) — BUILD SUCCESSFUL.
- 실 backend (외부 PG `115.21.71.140:13401/ibizdrive_design_preview`) + curl 재검증:
  - 시나리오 A (X-XSRF-TOKEN 헤더): **HTTP 403 + `{"code":"CSRF_MISMATCH"}`** ✓
  - 시나리오 C (CSRF 헤더 누락): **HTTP 403 + `{"code":"CSRF_MISMATCH"}`** ✓
  - 시나리오 B (4개 preview 계정 + 정합 X-CSRF-Token): **모두 HTTP 200 + LoginResponse** ✓
    - admin@local.test / AdminPass123 → ROLE ADMIN
    - test2@local.test / TestPassword123 → ROLE MEMBER
    - design@test.local / DesignPass123 → ROLE MEMBER
    - preview@local.test / PreviewPass123 → ROLE MEMBER

## Acceptance check

- [x] 401 응답을 발생시키는 정확한 코드 라인 식별: `HttpStatusEntryPoint(UNAUTHORIZED).commence()` (SecurityConfig.java:144), trigger 경로는 `CsrfFilter` → `AccessDeniedHandlerImpl.sendError(403)` → Spring Boot ErrorPage forward `/error` → `AuthorizationFilter` deny → `ExceptionTranslationFilter` (anonymous → entry point).
- [x] 도입 commit 식별: `ee7781ba (xorrbss 2026-05-07)` `setHeaderName("X-CSRF-Token")` (의도적 정합, 결함 아님). 빈 401 위장은 별도 잠재 결함.
- [x] 3개 시나리오 (A/B/C)로 가설 실측 검증 완료.
- [x] T2 fix 경로 확정 + 구현 + 회귀 가드 추가 + 4 preview 계정 manual 검증 통과.
