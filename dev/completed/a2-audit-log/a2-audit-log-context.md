---
Last Updated: 2026-04-28 (CLOSED — A2 마일스톤 종료, A3 진입점 핸드오프)
Status: ✅ CLOSED
---

# A2 Audit Log — Context (CLOSED)

## 종료 상태

A2 backbone GREEN 종료 — PR #2 squash merged (`dd372d7`), master CI run 25024916976 ✅.
세부 회고는 `docs/progress.md` 2026-04-28 A2 마일스톤 closure 블록 참조 (단일 진실 출처).

## A3 진입점 (다음 세션)

**A3 — 권한 매트릭스 + `PermissionService` + audit emit 시점**

진입 명령:
```bash
cd C:\project\IbizDrive
# 1. 새 worktree 생성 (a2 패턴)
git worktree add .claude/worktrees/a3-permission -b claude/a3-permission origin/master
# 2. dev-docs bootstrap
#    - dev/active/a3-permission/{plan,context,tasks}.md
#    - 기존 dev/active/a3-mutation/는 빈 디렉토리 — 명명 충돌 시 정리
```

### A3 scope (high-level, 상세는 a3 plan에서 확정)
1. **A3.1** docs/03 §3 권한 매트릭스 본문 채움 (현재 스켈레톤 — **블로커 #1 해소 지점**)
2. **A3.2** `PermissionService` + `@PreAuthorize` (백엔드 단일 권한 평가)
3. **A3.3** `effectivePermissionsCacheKey` `userId:role:v0` → 권한 변경 trigger 기반 hash (A1 deviation #2 해소)
4. **A3.4** A2 enum의 `permission.granted/revoked/changed` 3종 실 emit 시점 결정 + audit_log 기록
5. **A3.5** 통합 E2E (A2 패턴: `@SpringBootTest` + Testcontainers + HttpClient5)

### A3 의존성 / 메모
- 폴더/파일 도메인 부재 상태 — A3는 **user-level 권한**(role 기반)만 우선. resource-level(file_permission)은 **A4**로 미룸
- A2의 hidden gap 2건(actorIp X-Forwarded-For, LIKE 이스케이프)은 A3 scope 외 — v1.x 추적
- A2의 deferred 6건(파티셔닝/콜드/`audit.exported` runtime/`file.viewed`/`user.password.changed`/dev seed+e2e)도 A3 scope 외

## 블로커 (마일스톤 외부)

| # | 블로커 | 해소 phase |
|---|---|---|
| 1 | **docs/03 §3 권한 매트릭스 미완성** — 백엔드 `PermissionService` 구현의 사양 부재 | A3.1 |
| 2 | A1 frontend 인증 페이지 + admin 라우팅 부재 — A2 deferred(dev seed + e2e) unblocker | 별도 frontend 트랙 |

## 외부 진실 출처 (참조)

- `docs/progress.md` 2026-04-28 A2 closure 블록 — 마일스톤 회고 단일 진실 출처
- `docs/00-overview.md` §5 ADR #24 (AOP+Event 하이브리드), #25 (DB role 분리)
- `docs/02-backend-data-model.md` §2.8 (audit_log 스키마), §7.12 (`/api/admin/audit` endpoint)
- `docs/03-security-compliance.md` §4 (감사 정책)
- `backup/pre-reset-20260427-0036` 브랜치 — commit-message granularity 보존본
