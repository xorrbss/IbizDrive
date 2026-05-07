# Wave 1.5 — auditor-cron-readonly (context)

## 출발점

master HEAD `20aa8d6` (Wave 1 T2 closure backfill 직후, 2026-05-07). Wave 1 T1/T2/T3 종료, Wave 2 T4/T5/T6 종료, admin-storage-overview 머지.

T3(`wave1-t3-system-cron-readonly`, #65) closure 본문에 다음 deferred 항목 명시:

> AUDITOR가 cron 설정 확인을 요청할 가능성 — 현재는 403. 필요 시 별도 deferred 트랙(read-only 권한 확장)으로 처리

본 트랙이 그 deferred 항목을 회수.

## 영향받는 파일

### Backend (수정)

- **AdminSystemController.java** (L54): `@PreAuthorize("hasRole('ADMIN')")` → `@PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")`. javadoc L23~24 정정.
- **AdminSystemControllerTest.java** (L160~164): `getCronStatus_auditor_returns403` 케이스명/expectation flip → `getCronStatus_auditor_returns200`. 응답 jobs 배열 검증을 ADMIN 케이스와 동일하게 추가하여 단순 200이 아닌 정상 페이로드 확인.

### Docs

- **docs/04-admin-operations.md** §7.x 권한 표 — `/api/admin/system/cron` 행을 ADMIN-only → ADMIN+AUDITOR.
- **BETA-RELEASE.md** — 해당 deferred 항목 라인 갱신(있을 시).
- **docs/progress.md** — closure 엔트리 (Wave 1.5).

## 주요 패턴 / 규약

### 가드 표현식

T2 audit-export 패턴 채택:
```java
@PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
```
ADR #21 admin endpoint 패턴과 동형, hasAnyRole 대신 or 표현식을 쓰는 이유는 기존 admin 코드와 일관성 유지(audit-export, audit-query 모두 or 사용).

### 테스트 케이스 flip

기존 케이스 주석 `// 운영 cron 설정은 ADMIN 단독 책임 — AUDITOR 미허용`은 삭제. 새 케이스 주석으로 "AUDITOR read-only 확장 — Wave 1.5 / docs/04 §7.x" 명시.

## 함정 / 주의

1. **AdminGuard 프론트 게이팅**: `frontend/src/app/admin/layout.tsx:19`의 `<AdminGuard>`가 layout 레벨에서 `roles.includes('ADMIN')`만 통과시킴 (AdminGuard.tsx:28). 즉 본 트랙 머지 후에도 AUDITOR가 브라우저로 `/admin/system`을 열면 `/files`로 redirect됨. **이는 본 트랙의 의도된 OUT-OF-SCOPE** — 별도 "auditor-admin-ui-access" 트랙에서 AdminGuard를 role-aware하게 리팩터(AuditorOrAdminGuard 신설 또는 props로 확장).

2. **mutation 미허용 일관성 유지**: 본 트랙은 GET endpoint만 다룸. 향후 cron mutation(POST/PATCH) endpoint가 추가될 때 AUDITOR 차단 정책을 명시할 책임은 그 트랙에. 현재 컨트롤러에 mutation 메서드가 없으므로 본 트랙에서 별도 가드 추가 불필요.

3. **WebRequestContextHolder 무관**: read-only + audit emit 0이라 actor IP/UA 캡처 이슈 없음.

## 검증 방법

- 단일 테스트: `cd backend && mvn -pl . test -Dtest='AdminSystemControllerTest'`
- 백엔드 전체: `cd backend && mvn test`
- 프론트엔드 변경 없음.
