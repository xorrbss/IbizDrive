# v1.x Backlog — 우선순위 정리

> **목적**: v1.0.0-beta 출시 직후 다음 트랙 진입 결정에 사용. Tier × effort × blocker 3축으로 정렬.
>
> **단일 진실의 출처**: 항목 자체는 `BETA-RELEASE.md` §7 + `docs/progress.md` 각 트랙 closure entry. 본 문서는 그 항목들의 **우선순위/시점**만 다룬다. 새 항목 추가 시 양쪽 동시 갱신.
>
> **Last Updated**: 2026-05-13 (file-favorites-p2a — backend impl + audit enum 4종)

---

## 분류 기준

- **Tier 0 — v1.0.x patch**: 베타 운영 중 즉시 대응 가능. 회귀에서 발견된 bug fix + 작은 UX gap.
- **Tier 1 — v1.1**: 베타 후 첫 후속 출시에 같이 가는 게 자연스러운 기능 parity / spec 정합. 결정 완료 + spec 있는 트랙.
- **Tier 2 — v1.2+**: 큰 트랙. Multi-phase / 외부 의존 / 인프라 진화.
- **v2.x — 장기**: decision 미정 또는 인프라 결정 선결.

**effort**: S(단일 PR, 1~3 commit) / M(multi-phase, 3~10 commit, 1~2주) / L(multi-track, 10+ commit, 1개월+).

**blocker**: 없음 / 외부 의존 / spec 부재 / decision 미정 / 다른 트랙 선결.

---

## Tier 0 — v1.0.x patch (베타 운영 중 즉시)

| 항목 | effort | blocker | ref | 비고 |
|---|---|---|---|---|
| 회귀에서 발견된 critical bug fix | TBD | 없음 | golden path 회귀 결과 | 회귀 통과 직후 inventory. 현재 미정 |
| ~~Storage 용량 TB 단위 표시 (1024 GB+)~~ | — | — | ✓ 2026-05-09 format-bytes-tb-nan-guard (PR #134) — backlog stale | **closure (stale entry 정정)** — `formatBytes.ts`에 이미 TB 분기 + NaN/Infinity 폴백 구현됨 (`bytes / 1024^4 → '%.1f TB'`). 회귀 가드 5건(1.0/1.5/2.0 TB + 1024 GB - 1 byte 경계 + NaN/Infinity 3종) PASS. PB 미도입(KISS). 본 entry는 backlog drift였음 |
| ~~단축키 ↔ action 매핑 통합~~ | — | — | ✓ 2026-05-12 keyboard-shortcut-actions (PR #209) | **closure (점진 — 사용자 결정)** — `lib/keyboardShortcuts.ts` `ACTION_IDS`/`ActionId` + `KeyboardShortcut.action?: ActionId` optional + 3 wired 항목(`/`,`⌘K · Ctrl+K`,`?`) 마킹. `hooks/useGlobalShortcuts.ts` `ACTION_HANDLERS` Record dispatch 분리 (chord matching 그대로, CustomEvent listener 호환). 정합 가드 5건. 컨텍스트 의존 단축키(F2/Delete/↑↓/Ctrl+A 등)는 미통합 — 향후 ActionId 추가 + 컴포넌트 dispatcher 통합으로 진화 |
| ~~docs drift 정정 — schema §2 (V17 trash_policy) + API §7.12 endpoint spec 3건~~ | — | — | ✓ 2026-05-12 tier0-drift-sweep (PR #202) | **closure** — (a) §2.12 `trash_policy` 표 entry **이미 존재** (drift check 자체가 stale, line 495). (b) §7.12 `/api/admin/download-logs` `/api/admin/permission-logs` `/api/admin/storage-usage` 3건은 backend/frontend 0건 = never-implemented. **AdminAudit 통합으로 대체** → deprecation marker 채택 (본 PR). (c) audit enum 정합 OK |

---

## Tier 1 — v1.1 (베타 후 첫 후속 출시 후보)

| 항목 | effort | blocker | ref | 비고 |
|---|---|---|---|---|
| ~~Quota mutation Phase 5~~ | — | — | ✓ 2026-05-12 본 트랙 / progress.md | **closure** — `UserQuotaEnforcer` + `FileUploadService.upload` 진입 가드 + storage_used 증분 + 413 QUOTA_EXCEEDED + GlobalExceptionHandler 매핑 |
| ~~Quota Phase 6 — storage_used 감소~~ | — | — | ✓ 2026-05-13 본 트랙 / progress.md | **closure** — `User.releaseStorage` + `UserQuotaEnforcer.release` + `TrashPurgeService` 단건/folder cascade + `HardPurgeService` 배치 cron 모두 wire. 감소 합산은 file_versions sizeBytes 합계 per owner. monotonic 한계 해소 |
| ~~Admin Sharing 페이지 (디자인 zip P1)~~ | — | — | ✓ 2026-05-12 design-sweep-phase-3 (PR #200) | **closure** — frontend visual fidelity 완료, backend endpoint는 별도 v1.x 트랙 |
| ~~Admin Overview 위젯 보강 (디자인 zip P2)~~ | — | — | ✓ 2026-05-12 design-sweep-phase-3 (PR #200) | **closure** — UploadChart / FlagRow / DeptRow / audit-mini 추가 |
| ~~Admin Storage cleanup-list 위젯 (디자인 zip P2)~~ | — | — | ✓ 2026-05-12 design-sweep-phase-3 (PR #200) | **closure** — CleanupList 위젯 추가 |
| ~~Admin Retention/Audit 스타일 보강 (디자인 zip P3)~~ | — | — | ✓ 2026-05-12 design-sweep-phase-3 (PR #200) | **closure** — LegalHoldList(mock) + SeverityTabs/AuditStream + DashboardKpiCard delta/tone/progress |
| ~~잔여 admin 페이지 admin-grid 재구성 (디자인 zip follow-up)~~ | — | — | ✓ 2026-05-12 admin-grid-rebuild (PR #207) | **closure (옵션 B — wrapper 통일)** — 6 페이지(members/departments/permissions/teams/system/trash) wrapper utility(`flex-1 overflow-auto p-6 space-y-*`/`p-8 max-w-[960px]`) → `admin-grid` 통일. `admin-body`가 overflow+padding 처리, `admin-grid`는 flex-col gap 16px max-width 1400px 표준 layout. 위젯 추가 rebuild는 별도 트랙(필요 시) |
| ~~AdminMembers 디자인 fidelity (admin.jsx §AdminMembers L280~411)~~ | — | — | ✓ 2026-05-12 design-sweep-admin-members-fidelity (PR #208) | **closure (옵션 B+ 점진적)** — `members/page.tsx` ListSection에 KPI 4장(전체/관리자/외부게스트 placeholder/MFA placeholder) + SectionCard wrapping + Filter Bar(search + role select + status select frontend filter) + `MemberRoleChip`/`MemberStatusChip` 신설 + 행 시각 보강. backend 변경 0, 회귀 가드 29건 PASS. 부서/MFA 컬럼은 backend 미지원(ADR #18 blocker)으로 placeholder 유지 |
| ~~파일 타입 아이콘 fidelity (icons.jsx §ICONS L4~73)~~ | — | — | ✓ 2026-05-12 design-sweep-file-type-icons (PR #211) | **closure** — `FileTypeIcon` 신규(10 kind 컬러 SVG 1:1 inline, doc/pdf/sheet/slides/image/video/figma/code/archive + folder) + `fileIconFor`(lucide 5종 단색) → `fileIconKind` mime→kind 매핑 rewrite. FileRow/FileCard 호출자 swap. backend 변경 0, 132 tests PASS |
| ~~RightPanel 디자인 fidelity (panels.jsx §RightPanel L8~)~~ | — | — | ✓ 2026-05-13 rightpanel-backend-extend (PR #218 — P_panel-A) + rightpanel-ui-consume (PR #220 — P_panel-B detail rows) + rightpanel-frontend-wire (PR #221 — P_panel-C 풀세트) | **closure (viewCount 분리)** — P_panel-A backend `FileDetailResponse.owner/sharedWith/folderPath` 노출, P_panel-B detail 3 row(소유자/공유/경로) wire(co-session), P_panel-C 풀세트 통합: 헤더 파일 아이콘 + 액션 toolbar(다운로드/공유/더보기 disabled placeholder) + `PreviewCard` placeholder(kind별 soft 배경) + detail 탭 7 row(종류/크기/소유자 avatar/수정자 avatar/수정일/공유됨 AvatarStack max=4+N명/경로 breadcrumb/위치 restricted-tag). 다운로드 = `api.downloadFile`, 공유 = 권한 탭 전환, 더보기는 별도 트랙(dropdown menu 동작 미정). master merge 시 PR #220과 RightPanel.tsx conflict → 풀세트 채택. **viewCount는 ADR #9 (`FILE_VIEWED` audit) blocker로 분리** — row 자체 미렌더. 회귀 가드 RightPanel.test 21건 PASS. PR #222 sub-PR C 별도 entry는 본 PR이 정확히 그 작업이라 흡수 closure |
| ~~Audit severity backend 컬럼~~ | — | — | ✓ 2026-05-12 audit-severity-backend | **closure** — V19 `audit_log.severity` + `AuditSeverityMapper` 단일 진실 + emitter/query/export wire + frontend `severityOf` 폐기 |
| ~~DashboardKpiCard delta 데이터 wiring~~ | — | — | ✓ 2026-05-12 PR #202 (우발 흡수) + 본 closure (PR #205) | **closure** — backend `AdminDashboardSummaryResponse.*Delta` (8 필드) + `AdminDashboardService.computeDelta` + repo `count*AsOf` (5 repo) + frontend types/admin + DashboardSummary 8 wiring + 테스트. 30d stock + audit prev 24h 윈도우. P4 트랙으로 시작했으나 co-session edit absorption으로 PR #202 머지에 포함 — 별도 PR 부재 |
| ~~FileRow badge wiring (P2c shareCount / P2d itemsCount / P2b restricted)~~ | — | — | ✓ 2026-05-13 file-badge 트랙 (PR #210/#213/#215) | **closure** — `FolderItemDto`에 `shareCount`/`itemsCount`/`restricted` 3 필드 + `PermissionRepository.countActiveByResources` + `Folder/FileRepository.countByParentIdInGroupedActive`. PR #199 design-sweep-phase-2에서 FE 배지 UI는 mount, 본 트랙이 BE wiring. docs/02 §7.5 wire spec 갱신. 3 PR 모두 single-day shipping. 본 docs PR로 closure |
| ~~FileRow starred 배지 wiring (P2a) — backend~~ | — | — | ✓ 2026-05-13 file-favorites-p2a (PR #237) | **closure (backend impl)** — V22 `favorites` 테이블(composite PK user_id+resource_type+resource_id, CASCADE on user delete) + 4 endpoint(`POST/DELETE /api/{files\|folders}/{id}/star`) READ-가드 + `FavoriteService` 멱등 + AFTER_COMMIT audit emit + `FolderItemDto.starred` wiring(batch query `findStarredResourceIds`). audit 4 enum 추가(file/folder × starred/unstarred). 단위 테스트 10건 PASS. frontend wiring(`useToggleStar` hook + FileRow onClick)은 별도 PR 분리 |
| FileRow starred 배지 wiring (P2a) — frontend | S | 없음 (backend 본 PR로 완비) | 2026-05-13 file-favorites-p2a 종료 후 잔여 | `useToggleStar` hook + FileRow 별 아이콘 onClick 토글 wiring + optimistic update. backend 4 endpoint(`/api/{files\|folders}/{id}/star`) + `FolderItemDto.starred` 응답 필드 wire. v1.x small PR |
| **2인 승인 framework Phase 4 FE follow-ups** | S | Phase 1~3 + admin UI + email listener + AdminTabBar pending 배지 완료 (#219+#223+#226+#228+#229+#230+#233+#235+#236+본 PR) | BETA §7 / ADR #47 / progress.md 2026-05-14 | backend Phase 3 (Tier 0 3 handler + cron) + Phase 4 admin UI (#235 `/admin/approvals` 페이지 + queryKeys/api/hooks/AdminTabBar) + Phase 4 email listener (#236 `AdminApprovalEmailListener`, 4 transition 한국어 템플릿, soft-delete 가드, per-recipient 격리) + AdminTabBar pending count 배지(본 PR — `useAdminPendingApprovalsCount` + `totalElements`). 잔여: 202 응답 처리 (admin/users, admin/trash, admin/trash/policy 페이지에서 `APPROVAL_REQUIRED` envelope handling) — FE 작업 |
| ~~Admin Grant Phase C/D~~ | — | — | ✓ 2026-05-11 grant-permission-dialog Phase C+D (PR #163) + /admin/permissions 진입점 (PR #193) | **closure** — Phase C subject(`everyone`/`user`/`department`) 라디오 + `UserSearchCombobox`/`DepartmentSearchCombobox` 재사용, Phase D `ResourcePermissionsList` 통합("권한 부여" 버튼 + `aria-haspopup`/`aria-expanded`). `/admin/permissions` `AdminGrantPermissionTrigger`는 별도 진입점. ROLE/TEAM grant 평가는 v2.x (PR #162 spec realign) |
| audit_level + FILE_VIEWED + FOLDER_AUDIT_LEVEL_CHANGED emit | M | ADR #9 결정 보류 | BETA §7 / docs/04 §6 line 269 | 파티션 전략 결정 선결 (audit_log 폭증 대비) |
| 확장자 whitelist + MIME magic | M | spec 부재 | BETA §7 / docs/03 §5.3 | Content-Disposition 1차 방어 외 추가 layer. allow-list 정의 필요 |
| MFA / refresh rotation | M | ADR #18 결정 보류 | BETA §7 / ADR #18 | `USER_MFA_ENABLED` emit deferred. TOTP vs FIDO2 결정 |

---

## Tier 2 — v1.2+ (큰 트랙 / multi-phase)

| 항목 | effort | blocker | ref | 비고 |
|---|---|---|---|---|
| **Team-centric pivot Plan A Phase 3+** | L | 21 task spec 정렬됨 | spec `2026-05-09-team-centric-pivot-design.md` | Plan A Phase 1~8+10 (24/30 task) 완료. 잔여 6 task + Phase 11+ |
| **Plan B frontend foundation** | L | Plan A Phase 3+ 선결 | spec `2026-05-09-team-centric-pivot-design.md` §4.5 | 사이드바 3-section 트리 신규 구조 |
| Plan F | TBD | spec 부재 | BETA §7 | 의도 확인 필요 (사용자에게 spec 출처 요청) |
| Progress streaming (SSE) | M | spec 부재 | BETA §7 / docs/01 §18 | 업로드 progress + 폴더 변경 실시간 broadcast |
| AV 스캔 통합 | L | 외부 의존 (ClamAV 등) | BETA §7 / docs/03 §5 | 비동기 큐 + 격리 폴더 spec 선결 |
| tus 재개 업로드 | M | 외부 lib(tusd) | docs/01 §18 v1.x | 대용량 파일 재개. spec 부재 |
| Presigned URL + S3 migration | L | 인프라 결정 (S3 vs MinIO) | BETA §7 / docs/03 §5 | `LocalFsStorageClient` → `S3StorageClient` |
| KMS 객체 암호화 | M | S3 migration 선결 | BETA §7 | per-object KMS key |
| Cross-region replication | M | S3 migration 선결 | BETA §7 | DR |
| DB backup cron | S | managed PG 사용 시 미구현 가능 | BETA §7 / docs/04 §13 | `SYSTEM_BACKUP_COMPLETED` emit deferred |

---

## v2.x (장기 / decision 미정)

| 항목 | effort | blocker | ref | 비고 |
|---|---|---|---|---|
| **Legal Hold (전체)** | L | spec docs/00 §4.3 (스켈레톤) | BETA §7 / docs/00 §4.3 | `ADMIN_LEGAL_HOLD_PLACED/RELEASED` emit deferred. 법무팀 요건 확인 선결 |
| **ROLE/TEAM grant 평가** | M | spec realign PR #162 (v2.x 분리) | progress.md 2026-05-10 / docs/01 §14.5.4 | `PermissionRepository.findEffective` 쿼리 확장 + `PermissionResolver` 분기 + UUID-encoded role enum 또는 컬럼 분리 |
| Admin/permissions 전역 grant resource picker | M | ROLE/TEAM 선결 | progress.md 2026-05-10 / 2026-05-11 | folder tree + file search picker 컴포넌트 |
| SCIM | L | 외부 IdP 결정 | ADR #18 | 사용자 자동 동기화 |
| 단축키 사용량 분석 (audit / telemetry) | S | decision 미정 | progress.md 2026-05-11 | KPI / 단축키 활성도 |
| mobile-view | — | **폐기** | CLAUDE.md §3 원칙 13 (PR #179) | 사내 데스크탑 메인. 진행 금지 |

---

## 출시 직후 권장 진입 순서 (참고)

1. **회귀 결과 inventory** (Tier 0) — golden path 회귀에서 critical bug 발견 시 즉시 patch 트랙.
2. **Quota Phase 3~5** (Tier 1, M, blocker 0) — Phase 1+2가 머지된 직후라 가장 정합. user storage 가시화 가치 高.
3. **Admin Grant Phase C/D** (Tier 1, M, blocker 0) — Phase A/B + 진입점이 머지된 직후. 운영 작업 비용 ↓.
4. **2인 승인 framework** (Tier 1, L) — 운영 안정성. v2.x 보안 사고 가능성 ↓.
5. **Team-pivot Plan A Phase 3+** (Tier 2, L) — 가장 큰 미완 트랙. 별도 multi-session worktree 권장.

병행 가능 여부:
- Quota / Grant Phase C/D / 2인 승인은 backend service layer가 분리되어 있어 multi-session 병렬 가능 (memory: subagent gradle contention 주의).
- Team-pivot Plan A는 widespread 변경이라 단일 worktree 권장.

---

## 갱신 규칙

- 새 트랙 closure 시 본 문서에서 항목 제거 + `progress.md` closure entry는 그대로 유지.
- 새 backlog 항목 추가 시 `BETA-RELEASE.md` §7과 본 문서 동시 갱신.
- Tier 재분류는 effort/blocker 상태 변화 시에만 (예: blocker 해소 → Tier 상승).
