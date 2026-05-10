# team-domain-hardening — Context

Last Updated: 2026-05-09

## SESSION PROGRESS

- **세션 1 (2026-05-09)**: Plan A 30/30 완료 직후 Plan A2로 진입. Bootstrap Dev Docs (plan/context/tasks). 코드베이스 + spec 정사 완료. backlog 4 → 5 → 2 → 3 묶음 선택.
- **세션 1 (2026-05-09) — Phase 1 완료**: P1 T1~T4 (last-OWNER guard + role change audit chain) 마무리. 8 commits on `feat/team-centric-pivot-plan-a` (T1: 7b40110 / T2: 4502eef + fix 1358be8 / T3: f17e9ee + fix 44a265d / T4: 895aae3 + fix 1cdb54a). 같은 세션에 코-세션이 본 브랜치에 직접 push: `dcabc40` (Plan C T3 — ShareControllerTest), `6fe62ea` (Plan D T4 — AuditEventType FOLDER/FILE_MOVED_CROSS_WORKSPACE). 우리 영역과 충돌 없음.
- **세션 1 (2026-05-09) — Phase 2 완료**: P2 T5~T7 (Team archive/restore + audit chain + service) 마무리. 3 commits (T5: 55f8246 / T6: 7b86fb9 / T7: 8e6ca8f). spec/code review 모두 ✅ Approved. backend 28 TeamService* tests + 7 TeamAuditListener tests + 12 TeamTest tests green. frontend typecheck green.
- **세션 1 (2026-05-09) — Phase 3 완료, 트랙 종료**: P3 T8 (spec/docs drift fix) 마무리. 1 commit (2099f64). spec §1.5/§2.2/§5.4 + docs/03 §4.1 + docs/02 §8 동기. 트랙 전체 11 커밋, 코-세션 commit 2개와 분리 보존됨. dev/active → dev/completed 이동.

## Current Execution Contract

- **모드**: 자율 실행 (`feedback_autonomous_mode`). 60/70/75/80% 임계값 + handoff 체크리스트.
- **브랜치**: `feat/team-centric-pivot-plan-a` (HEAD = 865939b). master에서 31 commit ahead. **push 금지**(co-session 충돌 회피).
- **컨벤션 (Plan A에서 확립, 반드시 지킬 것)**:
  - 마이그레이션 IT (`V*MigrationIT.java`): JUnit Jupiter (`assertEquals`/`assertThrows`), `@DataJpaTest`+`@Testcontainers(disabledWithoutDocker=true)` + Postgres 15-alpine.
  - 엔티티 단위 테스트(`*Test.java`, no DB): AssertJ.
  - 서비스 단위 테스트: Mockito (peer = `TeamServiceCreateTest`), NOT `@SpringBootTest`.
  - 컨트롤러 테스트: `@WebMvcTest` 슬라이스 + `@MockBean` (peer = `WorkspaceControllerTest`, `AdminDepartmentControllerTest`).
  - E2E 통합 테스트: `@SpringBootTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `@Testcontainers` + `@DynamicPropertySource` (peer = `TeamPivotEndToEndTest`).
  - 인증된 사용자 추출: `@AuthenticationPrincipal IbizDriveUserDetails principal` → `principal.getUser().getId()` (NOT `SecurityContextHolder`).
  - `@PreAuthorize` bean SpEL: `@MockBean(name = "beanName")` 명시 필수.
  - DTO null 필드 omit: `@JsonInclude(JsonInclude.Include.NON_NULL)` 명시 (글로벌 설정 없음).
  - audit 위임: 도메인 event publish → `AuditListener(@TransactionalEventListener AFTER_COMMIT)`가 record. 직접 emit 금지.
  - 정규화: `NormalizeUtil.normalizeFileName()` / `normalizedNameForDedup()`.
  - Folder 생성: `FolderMutationService.createRootForScope(...)` 위임 (Folder 생성자 protected 유지).
  - AuditEvent: 9-component record, `AuditService.record(AuditEvent)`.
  - AuditTargetType / AuditEventType accessor: `.wire()`.
  - 테스트 메서드 명명: `methodUnderTest_scenario` 언더스코어.
  - Javadoc: 인터페이스 + 모든 public 메서드 (peer = `TeamRepository`, `DepartmentRepository`).
  - YAGNI 엄수: plan 외 추가 금지. 의문은 BLOCKED + TODO.

## 트랙 종료 상태 (2026-05-09)

- **완료**: P1 + P2 + P3 (8 task / 11 commit / 모든 review ✅).
- **후속 작업** (본 트랙 외):
  - `ERR_TEAM_ARCHIVED` (423) enforcement: FolderMutationService/FileUploadService에서 archived team 쓰기 차단 — docs/02 §8에서 "예약" 마킹됨. 별도 트랙(Plan B 백엔드 또는 후속).
  - frontend `frontend/src/types/audit.ts` drift 3 wire (`team.created`, `team.member.added`, `team.member.removed`) 동기 — plan-b-frontend 코-세션 영역.
  - phase9 worktree 통합 (backlog #1) — 별도 의사결정 필요.
  - workspace listing endpoint (backlog #9) — plan-b-frontend의 `useWorkspaces`와 짝.

## 다음 세션 읽기 순서

1. `team-domain-hardening-context.md` (이 파일) — 진행 상태 + 컨벤션
2. `team-domain-hardening-tasks.md` — 체크박스 상태 확인
3. `team-domain-hardening-plan.md` — phase별 acceptance / 리스크 / 범위 외
4. 코드 진입점:
   - `backend/src/main/java/com/ibizdrive/team/TeamService.java`
   - `backend/src/main/java/com/ibizdrive/audit/TeamAuditListener.java`
   - `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java`
   - `src/types/audit.ts` (frontend)
5. spec: `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §1.1 / §1.5 / §2.2 / §5.4

## 핵심 파일과 역할

| 파일 | 역할 | 변경 예정 phase |
|---|---|---|
| `backend/src/main/java/com/ibizdrive/team/TeamService.java` | create/invite/remove + (신규) changeRole/archive/restore | P1, P2 |
| `backend/src/main/java/com/ibizdrive/team/Team.java` | entity + (신규) archive/restore 도메인 메서드 | P2 |
| `backend/src/main/java/com/ibizdrive/team/TeamMembership.java` | entity (changeRole 도메인 메서드 보유) | (변경 없음) |
| `backend/src/main/java/com/ibizdrive/team/TeamMembershipRepository.java` | countByTeamIdAndRole 보유 | (변경 없음) |
| `backend/src/main/java/com/ibizdrive/team/LastOwnerRequiredException.java` | (신규) | P1 T1 |
| `backend/src/main/java/com/ibizdrive/team/TeamMemberRoleChangedEvent.java` | (신규 record) | P1 T2 |
| `backend/src/main/java/com/ibizdrive/team/TeamArchivedEvent.java` | (신규 record) | P2 T5 |
| `backend/src/main/java/com/ibizdrive/team/TeamRestoredEvent.java` | (신규 record) | P2 T5 |
| `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` | enum 51 → 54 | P1 T2, P2 T5 |
| `backend/src/main/java/com/ibizdrive/audit/TeamAuditListener.java` | listener 3 → 6 method | P1 T2, P2 T5 |
| `frontend/src/types/audit.ts` | AuditEventType union | P1 T2, P2 T5 |
| `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` | 에러 → envelope 매핑 | P1 T1 |
| `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` | §1.5 drift fix + 시맨틱 매핑 | P3 T8 |
| `docs/03-security-compliance.md` | §4.1 audit enum 표 동기 | P3 T8 |
| `docs/02-backend-data-model.md` | §8 에러 코드 표 동기 | P3 T8 |

## 중요한 의사결정

1. **DEC-1**: Department `archive()` 별칭 메서드는 추가하지 않는다. 호출자가 없어 dead code (YAGNI). docs에 `Department.deactivate ≡ Team.archive` 1:1 매핑만 표기.
2. **DEC-2**: archive 후 Folder/File 쓰기 차단(ERR_TEAM_ARCHIVED 423)은 본 트랙 범위 외. archive flag만 set, enforce는 후속 작업.
3. **DEC-3**: archive 시 멤버십 freeze(invite/remove 차단)는 spec §2.2 명시 없음 → 본 트랙 범위 외. 단순 archivedAt 갱신만.
4. **DEC-4**: spec §1.5 drift(`team.restored` 누락)는 P3에서 닫음. T5에서 enum 추가하면서 동시 fix해도 무방.
5. **DEC-5**: `TeamMemberRoleChangedEvent`의 oldRole/newRole 보존 → audit before/after 둘 다 발행 (peer pattern: `AdminDepartmentUpdatedEvent` before/after).
6. **DEC-6**: T1과 T2의 docs 반영(에러코드, audit enum)은 T8에 위임 가능. 그러나 같은 commit에 함께 가는 게 review 편리 — 각 task 내에서 docs 동기 책임.
7. **DEC-7 (drift)**: frontend errors 모듈 신설하지 않음. codebase는 `api.ts` JSDoc + 호출 지점 string literal 컨벤션. 호출자 없는 상수 추가는 dead code(YAGNI).
8. **DEC-8 (drift)**: envelope wire code는 prefix 없음 (peer `RENAME_CONFLICT`, `DEPARTMENT_CONFLICT`). spec docs는 `ERR_` prefix(`ERR_TEAM_OWNER_REQUIRED`) — wire는 `TEAM_OWNER_REQUIRED`로 매핑. T8에서 spec §5.4 footnote로 명시.

## 빠른 재개 안내

```bash
# 1. 위치 확인
git -C C:\project\IbizDrive branch --show-current  # feat/team-centric-pivot-plan-a 확인
git -C C:\project\IbizDrive log --oneline -3       # 865939b 또는 새 커밋들

# 2. tasks.md 체크박스 확인 후 다음 미완료 task 진입

# 3. 검증
cd backend && ./gradlew test --tests "*Team*"
cd .. && pnpm typecheck && pnpm lint

# 4. selective add (co-session WIP 회피)
git add -p backend/src/...   # 본 트랙 변경만 stage
git commit -m "feat(team-centric-pivot): ..."
```

## 주의사항 (재확인)

- 다른 worktree(`phase9`, `plan-c-share-team`, `plan-b-frontend`)와 영역 충돌 회피.
- `backend/src/test/java/com/ibizdrive/permission/PermissionServiceGrantRevokeTest.java` 코-세션 WIP — 건드리지 말 것.
- 새 worktree(`docs/superpowers/plans/2026-05-09-team-centric-pivot-plan-b-frontend-foundation.md`) 도 코-세션 작업물 — 건드리지 말 것.
- master에 직접 commit 금지.
- subagent dispatch 전 항상 `git -C C:\project\IbizDrive branch --show-current` 확인 (master 미끄러짐 방지).
