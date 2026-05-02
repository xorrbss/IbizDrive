# Baseline Report — Phase 1

Last Updated: 2026-05-02
Source HEAD: master `90274c7` (storage-orphan-cleanup ADR #38 closure)
Run host: Windows 11 + bash + Java 21 + Node/pnpm

## Backend Tests (`./gradlew test`)

명령: `cd backend && ./gradlew test --console=plain`

```
Starting a Gradle Daemon (subsequent builds will be faster)
> Task :test UP-TO-DATE
BUILD SUCCESSFUL in 3m 33s
```

UP-TO-DATE → 마지막 실행 결과 (2026-05-01) 그대로 활용. 카운트는 `build/test-results/test/*.xml` 집계.

| 메트릭 | 값 |
|---|---|
| 테스트 클래스 (XML 파일) | **75** |
| 총 tests | **723** |
| skipped | **201** (대부분 Testcontainers `disabledWithoutDocker=true` — Docker 미가용 환경 자동 skip) |
| failures | **0** |
| errors | **0** |
| executed (= total − skipped) | **522 PASS** |

storage-orphan-cleanup 트랙 추가분 포함 확인:
- `StorageOrphanCleanupServiceTest.xml` ✓ (8 tests Mockito, all run)
- `StorageOrphanCleanupIntegrationTest.xml` ✓ (Testcontainers, skipped)
- `StorageOrphanCleanupJobDisabledIntegrationTest.xml` ✓
- `LocalFsStorageClientTest.xml` ✓ (확장 listOlderThan 케이스 포함)

판정: **GREEN**. Phase 2 진입 가능.

추가 메모:
- Docker 환경에서 재실행 시 ~201 skipped 중 IT 다수가 활성화되어 executed 카운트가 723에 가까워질 것.
- Testcontainers IT는 `disabledWithoutDocker=true`로 의도적 skip이며 회귀 위험 신호 아님.

## Frontend Tests (`pnpm test --run`)

명령: `cd frontend && pnpm test --run`

| 메트릭 | 값 |
|---|---|
| 테스트 파일 | **72 passed** |
| 테스트 | **563 passed (총 563)** |
| 실패 | **0** |
| 실행 시간 | 349.38s |

판정: **GREEN**.

직전 baseline 비교:
- A16 closure (2026-05-02): 565/565
- 현재: 563/563
- 차이: −2. 가장 가능성 높은 원인 = ADR #36 multipart 복귀 시 FakeXHR mock 모듈 제거. 회귀 신호 아님 (잘못된 fixture를 정리한 결과).

## Frontend Typecheck / Lint / Build

| 명령 | 상태 | 메모 |
|---|---|---|
| `pnpm typecheck` | **GREEN** (exit 0) | tsc --noEmit, 에러 0 |
| `pnpm lint` | **GREEN** (exit 0) | eslint, warning/error 0 |
| `pnpm build` | **GREEN** | Compiled successfully in 43s. 9 static + 1 dynamic route |

빌드 결과 라우트 목록 (Next.js 15 App Router):

| Route | Size | First Load JS | Type |
|---|---|---|---|
| `/` | 144 B | 101 kB | static |
| `/_not-found` | 977 B | 102 kB | static |
| `/admin/audit/logs` | 3 kB | 126 kB | static |
| `/files` | 144 B | 101 kB | static |
| `/files/[...parts]` | 24.5 kB | 167 kB | **dynamic** (folder catch-all) |
| `/shares` | 2.46 kB | 116 kB | static |
| `/trash` | 5.12 kB | 128 kB | static |

분석:
- 관리자 라우트는 `/admin/audit/logs` 단 1개. 나머지 admin (users/departments/permissions/storage/policies/system 등 docs/04 §2 트리)는 frontend 미구현 → P1.4 인벤토리 분류와 정합 (대부분 v1.x).
- shared chunks 101 kB는 React 19 + Next 15 + TanStack Query + Zustand baseline. 정상 범위.

## Git 작업 디렉터리 상태

```text
D  dev/process/a10-shares-2026-05-01.md
?? .g3/, .g4/, .g5/
?? .gradle-user-home/, .gradle-user-home-2/, .gradle-user-home-3/, .gradle-user-home-codex/
?? .tmp-gradle-root-get/
```

분석:
- `dev/process/a10-shares-2026-05-01.md` deletion = 정상 (트랙 closure 후 잔존). Phase 4 closure commit에 흡수.
- `.gradle-user-home*` / `.g3-5/` / `.tmp-gradle-root-get/` = gradle 임시 / 자동 생성. **`.gitignore` 항목 추가 필요** (현재 `.gitignore`에 `.gradle*` 패턴 부재).
- 물리 디렉터리 삭제는 본 트랙에서 하지 않음 (사용자 확인 후 별도).

## Phase 2 진입 결정

| 게이트 항목 | 상태 |
|---|---|
| backend test GREEN | ✓ |
| frontend test GREEN | ✓ |
| 전체 baseline 카운트 기록 | ✓ |
| 작업 디렉터리 정리 가능 항목 식별 | ✓ |
| ADR 인덱스 작성 (P1.3) | ✓ → `findings/adr-index.md` |
| 빈 체크박스 인벤토리 (P1.4) | ✓ → `findings/empty-checkbox-inventory.md` |
| frontend typecheck/lint/build | ✓ (GREEN) |

→ G1 게이트 통과. Phase 2 진입 가능.
