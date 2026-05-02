# Phase 3 Triage Decisions — Sign-off + Remediation

Last Updated: 2026-05-02
Source: `findings/stride-gap-analysis.md` MVP-blocker 후보 3건 + 사용자 sign-off (2026-05-02 G2)

## G2 Sign-off 결정

사용자 sign-off: **A안 (추천 그대로)**.

| # | 항목 | 결정 | 처리 방식 |
|---|---|---|---|
| 1 | 악성 파일 업로드 — 확장자 화이트리스트 | **v1.x deferred** | docs/03 §5.3 inline 마커 |
| 2 | Information Disclosure — production stacktrace | **MVP-fix** | `application.yml` 1줄 추가 |
| 3 | Spring Security 헤더 명시화 | **MVP-fix** | `SecurityConfig.java` `.headers()` chain 추가 |

## Remediation 결과

### Fix #1 (deferred 마커) — `docs/03-security-compliance.md` §5.3 / §5.4

변경:
- §5.3 4개 체크박스 모두 `*v1.x deferred (외부 출시 시 도입)*` 마커 추가
- §5.3 상단에 MVP 결정 인용 박스 추가:
  > 사내 베타에서는 §5.4 `Content-Disposition: attachment` + `X-Content-Type-Options: nosniff`로 1차 방어. 직접 렌더링 차단 → XSS 페이로드는 다운로드 후 사용자 명시 실행 필요.
- §5.4 첫 두 항목 [x] 체크 (구현 evidence 명시):
  - `Content-Disposition: attachment` → `FileDownloadController:76-114` (RFC 5987 인코딩 포함)
  - `X-Content-Type-Options: nosniff` → `SecurityConfig.headers().contentTypeOptions()` (Fix #3)
- §5.4 세 번째 항목 (HTML/SVG 별도 도메인) v1.x deferred 마커

### Fix #2 — `backend/src/main/resources/application.yml`

추가 (server: 블록 하위):
```yaml
  error:
    include-stacktrace: never
    include-message: on-param
    include-binding-errors: on-param
```

근거:
- Spring Boot default(`on-param`)는 `?trace=true` 쿼리에 stacktrace 노출 가능
- `never`로 강제 → production stacktrace leak 차단
- `include-message: on-param`은 디버그 시점 `?message=true`로만 활성화 (개발 편의 + 운영 안전 양립)

### Fix #3 — `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java`

`csrf(...)` 호출과 `securityContext(...)` 호출 사이에 `.headers(...)` chain 추가:
```java
.headers(h -> h
    .contentTypeOptions(c -> {})
    .frameOptions(f -> f.deny())
    .cacheControl(c -> {}))
```

근거:
- Spring Security 6 default가 동일 헤더 자동 활성화 → MVP 동작 변화 0
- Spring Security 7+ 기본값 변경 가능성 방어 (forward compat)
- 보안 정책의 명시적 가시성 ↑ (audit/리뷰 시점 default 의존 검증 불필요)

## 검증

| 항목 | 결과 |
|---|---|
| backend test (`./gradlew test`) | 75 classes / **723 tests / 522 PASS / 201 skipped (no Docker IT) / 0 fail / 0 error** — baseline 정합 (회귀 0) |
| backend `compileJava` | BUILD SUCCESSFUL — 컴파일 GREEN |
| frontend 변경 | 없음 (frontend test 재실행 불필요) |
| docs/03 변경 영향 | 본문 텍스트만 — 빌드/테스트 영향 0 |

## 비고

1. SecurityConfig 변경은 Spring Security 6 default 의존을 명시화만 한 것이라 동작/응답 헤더 자체는 변경 없음. SecurityConfigTest의 기존 단위 테스트(있다면)도 영향 받지 않음.
2. application.yml `server.error.*`는 운영 분리 profile이 도입되면 `application-prod.yml`로 이관 가능. 현재는 단일 application.yml에 명시.
3. 확장자 화이트리스트 deferred는 사내 베타 가정 기반 — 외부 출시 트리거링 시점에 별도 트랙으로 부활 필요. `docs/03 §5.3` 마커가 backlog 인덱스 역할.
