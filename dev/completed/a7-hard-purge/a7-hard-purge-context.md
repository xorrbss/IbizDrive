---
Last Updated: 2026-04-30
Status: 📋 BOOTSTRAP — A7.0 진입 대기 (게이트 0)
---

# A7 — Hard Purge Job — Context

## SESSION PROGRESS

- **2026-04-30 bootstrap** — plan/context/tasks 3파일 작성 + worktree `feature/a7-hard-purge` 생성 (master `fdeb610` = A6 closure 기준).
  - 동기: A6 closure progress 블록의 "다음 단계 — Hard purge job (purge_after 경과 row 영구 삭제 + S3 객체 삭제). docs/04 §13 배치 트랙" 진입.
  - 범위: DB-only hard purge (S3 객체 deferred via ADR #31). `/api/trash/*` admin endpoint은 A8 reserve.
  - 분기점: backend storage 모듈 0개 → S3 cleanup은 storage 도입 시점에 별도 잡(`orphan.detect`)에서 처리.
- **다음**: 게이트 0 통과 → A7.0 docs patch (ADR #31 + docs/02 §2.5 line 37 주석 + docs/04 §13 row)

## Current Execution Contract

- **자율 실행 모드** 활성 (memory/feedback_autonomous_mode.md). 게이트마다 사용자 보고 + 다음 게이트 대기. 단, 본 세션은 사용자가 "물어보지 말고 자율 수행해" 명시적 승인 → 게이트별 보고는 간략화하고 막힘 없을 시 자체 판단으로 진행.
- **Decision style**: 추천안 + 근거. A/B/C 분기 질문 회피.
- **TDD**: RED → GREEN per phase. A7.1부터 RED 테스트 작성 → 구현.
- **단일 PR**: A2/A3/A5/A6 패턴.
- **Worktree**: `C:/project/IbizDrive/.claude/worktrees/a7-hard-purge`, branch `feature/a7-hard-purge`.

## 현재 active task

- **Phase**: bootstrap (게이트 0 — dev-docs 3파일 commit 직전)
- **다음**: bootstrap commit → 사용자 보고(자율 모드는 묵시적 OK) → A7.0 docs patch

## 다음 세션 읽기 순서

1. `a7-hard-purge-plan.md` — 전체 계획 + DoD + 핵심 결정 8개
2. 본 `a7-hard-purge-context.md` — SESSION PROGRESS (가장 마지막 항목 = 현재 상태)
3. `a7-hard-purge-tasks.md` — phase별 체크박스 + 미완료 task 참조 블록
4. `docs/02-backend-data-model.md`:
   - §2.5 (line 153~180) — `file_versions` FK 정책
   - §7.11 (line 1109~1131) — 휴지통 (A7) row 정책
   - §6.5 (line 661~692) — 휴지통 SQL 가이드 (folder cascade는 A6에서 추가됨)
5. `docs/04-admin-operations.md` §13 (line 305~317) — 배치 작업 표
6. `dev/completed/a6-folder-mutation-delete/` — cascade BFS + audit root-only 패턴 참조
7. `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` — `SYSTEM_PURGE_EXECUTED` 위치 확인

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `backend/.../config/SchedulingConfig.java` | (NEW) `@EnableScheduling` 토글 (`@ConditionalOnProperty`) |
| `backend/.../purge/HardPurgeJob.java` | (NEW) `@Scheduled` cron — service 위임만 |
| `backend/.../purge/HardPurgeService.java` | (NEW) 트랜잭션 본체 + audit emit |
| `backend/.../purge/HardPurgeProperties.java` | (NEW) `app.purge.{enabled, max-per-run}` |
| `backend/.../purge/PurgeResult.java` | (NEW) record — runId, counts, durationMs, truncated, orphanStorageKeys |
| `backend/.../file/FileRepository.java` | (EDIT) `findExpiredFileIds`, `hardDeleteByIds`, `findStorageKeysByFileIds` |
| `backend/.../folder/FolderRepository.java` | (EDIT) `findExpiredFolderIds`, `hardDeleteByIds`, `findParentIdsByIds` |
| `backend/.../file/FileVersionRepository.java` | (EDIT) `findStorageKeysByFileIds`, `deleteByFileIds` |
| `backend/.../audit/AuditEventType.java` | (READ-ONLY) `SYSTEM_PURGE_EXECUTED` 이미 정의됨 |
| `docs/00-overview.md` | (EDIT) §5 ADR #31 신설 |
| `docs/02-backend-data-model.md` | (EDIT) §2.5 line 37 주석 + §7.11 batch 행 footnote |
| `docs/04-admin-operations.md` | (EDIT) §13 `purge.expired` 행 footnote (한도, audit, ADR #31 backlink) |

## 중요한 의사결정 (변경 시 docs 동기화)

1. **DB-only purge** (ADR #31) — S3 객체는 orphan으로 잔존, storage 모듈 도입 시 처리. 이 결정 변경 시: storage 모듈 신설 + ADR #31 close + `HardPurgeService` 재작성.
2. **Audit summary-only** — `SYSTEM_PURGE_EXECUTED` 1건/run. per-row enum(`FILE_PURGED`/`FOLDER_PURGED`)은 A8 manual purge에 reserve. 변경 시: A6 root-only 패턴과 정합성 재검토.
3. **MAX_PURGE_PER_RUN=10000** — properties로 조정 가능. 환경별 튜닝 가능하도록.
4. **Schedule cron Asia/Seoul** — docs/04 §13 "매일 00:00" 정합. 환경별 변경 시 `app.purge.cron` 추가 검토.
5. **No ShedLock** — single-instance MVP. 멀티화 시 ADR.

## 빠른 재개 안내

- 다음 작업자: `a7-hard-purge-tasks.md`의 첫 미완료 phase로 진입.
- 막히면: 본 context의 "중요한 의사결정" 5개와 plan의 §리스크 표 확인. 그래도 모호하면 사용자 게이트.
- 검증 명령:
  - 단일 모듈 테스트: `./gradlew :backend:test --tests "com.ibizdrive.purge.*"`
  - 전체 회귀: `./gradlew :backend:test`
  - 컴파일만: `./gradlew :backend:compileJava :backend:compileTestJava`
- 커밋 메시지 컨벤션 (A6 PR #15 참조):
  - phase 단위: `feat(A7.x): <title>` 또는 `docs(A7.x): <title>`
  - bootstrap: `chore(A7): bootstrap dev-docs (hard purge job)`
  - closure: `chore(A7): closure — A7 마일스톤 종료 + dev-docs archive`
