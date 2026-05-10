# IbizDrive — Dev Preview Seed

`seed.sql` — dev preview 환경에서 디자인 fidelity 시각 검증용 시드 데이터.

상위 트랙: `dev/active/dev-preview-stabilization/` (T5).

## 무엇이 들어가는가

| 종류 | 항목 |
|---|---|
| 부서 (1) | **디자인팀** (`0d000000-0000-0000-0000-000000000001`) |
| 팀 (1) | **디자인 챕터** (`07000000-0000-0000-0000-000000000001`) |
| 부서 폴더 (3) | `디자인팀/` (root) · `2026 Q1` · `Brand Assets` |
| 팀 폴더 (2) | `디자인 챕터/` (root) · `Sprint 26` |
| 파일 (6) | `2026 KPI 보고서.pdf` · `Q1 로드맵.docx` · `Brand Guidelines v3.pdf` · `Color Tokens.xlsx` · `스프린트 회고.md` · `Wireframe Hero.png` |
| 멤버십 (1) | `admin@local.test` 가 디자인 챕터의 OWNER + 디자인팀 부서 배정 |

파일은 metadata만 들어간다. `storage_key`/`checksum` 은 placeholder UUID/hex라 실제 다운로드/스트리밍은 시도하지 마라 (S3 객체 미생성). 디자인 fidelity는 **목록·그리드·우측 패널 metadata** 영역에서 검증.

## 사전조건

1. PG 에 V1~V15 Flyway migration 적용 완료. backend가 한 번 정상 기동했으면 자동 적용됨.
2. `admin@local.test` 사용자가 self-signup으로 이미 가입되어 있어야 한다 (ADR #41 — 첫 가입자 자동 ADMIN). 비밀번호 hash는 backend가 발급, 시드 SQL에는 평문/hash 모두 넣지 않는다.

   가입 명령 (frontend dev proxy 또는 backend 직접 호출):

   ```bash
   curl -X POST http://localhost:8080/api/auth/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"admin@local.test","password":"AdminPass123","displayName":"Admin"}'
   ```

   가입이 안 되어 있으면 `seed.sql`이 의미 있는 메시지로 fail한다 (`RAISE EXCEPTION` + HINT).

## 실행

### Option A — psql (PG client 설치돼 있을 때)

```bash
PGPASSWORD=<password> psql \
  -h <host> -p <port> -U <user> -d ibizdrive_design_preview \
  -f dev/preview/seed.sql
```

### Option B — docker (PG client 미설치 환경)

`postgres:15` image의 psql을 일회성 컨테이너로 실행:

```bash
docker run --rm -v "$(pwd)/dev/preview:/seed" \
  -e PGPASSWORD=<password> \
  postgres:15 \
  psql "postgresql://<user>@<host>:<port>/ibizdrive_design_preview" \
  -f /seed/seed.sql
```

기본 dev preview 자격 (외부 PG 환경, T4·T6 가이드 정합):

| | |
|---|---|
| host | `115.21.71.140` |
| port | `13401` |
| db | `ibizdrive_design_preview` (없으면 먼저 `CREATE DATABASE`) |
| user / pw | `postgres` / `postgres` |

## 멱등성

- 모든 INSERT는 `ON CONFLICT (id) DO NOTHING` (또는 composite PK).
- 모든 UPDATE는 `IS DISTINCT FROM` 가드.
- 동일 SQL 두 번 실행해도 에러 없이 no-op (트랜잭션 단위 보장).

## 검증

시드 후 backend가 띄워져 있으면:

```bash
# admin 로그인 → SESSION 받기 (세션 A T2 fix 머지 후 가능)
# 또는 signup 직후 받은 SESSION 재사용

# workspace API 검증
curl -s -H "Cookie: SESSION=<value>" http://localhost:8080/api/workspaces/me | jq .
# 기대: department.name="디자인팀", department.rootFolderId="0d000000-...-ff001",
#       teams[0].name="디자인 챕터", teams[0].rootFolderId="07000000-...-ff001"
```

또는 SQL 직접:

```sql
-- 부서/팀 root 연결 검증
SELECT 'department' AS kind, name, root_folder_id
  FROM departments
  WHERE id = '0d000000-0000-0000-0000-000000000001'
UNION ALL
SELECT 'team', name, root_folder_id
  FROM teams
  WHERE id = '07000000-0000-0000-0000-000000000001';

-- 폴더 카운트 (기대: 5)
SELECT count(*) FROM folders
  WHERE scope_id IN ('0d000000-0000-0000-0000-000000000001',
                     '07000000-0000-0000-0000-000000000001')
    AND deleted_at IS NULL;

-- 파일 카운트 (기대: 6)
SELECT count(*) FROM files
  WHERE scope_id IN ('0d000000-0000-0000-0000-000000000001',
                     '07000000-0000-0000-0000-000000000001')
    AND deleted_at IS NULL;

-- 멱등성: 같은 SQL 두 번 실행 후 카운트 동일해야 함
```

## 디자인 fidelity 시각 확인 포인트

시드 + admin 로그인 후 `/files` 진입 시:

| Gap | 확인 위치 |
|---|---|
| **G1** TopBar 48px | 모든 페이지 상단 |
| **G5** Row 34px (variant 추종) | FileTable 리스트 모드. 톱니바퀴(TweaksPanel)에서 `linear/notion/dropbox/terminal` 토글 시 row 높이 자동 변경 (34/40/38/28). |
| **G6** Grid 카드 172px | ViewSwitch 그리드 모드. 카드 폭. |
| **G8** DropOverlay 카드 | OS에서 파일 드래그 → 화면 중앙 surface-1 카드 (56px 원형 아이콘 + "여기에 놓아서 업로드" + 서브 텍스트) |

## 관련 문서

- 상위 트랙: `dev/active/dev-preview-stabilization/dev-preview-stabilization-plan.md`
- workspace 계약 spec: `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §4.5, §5.4
- 디자인 fidelity gap 보고: `dev/active/design-handoff-gap-report-2026-05-10.md` (PR #148 closure)
- local dev 셋업 가이드 (T6 산출물 — 미작성 시점): `docs/local-dev.md`
