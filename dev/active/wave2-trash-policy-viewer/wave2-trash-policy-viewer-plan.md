# wave2-trash-policy-viewer — 휴지통 보존 정책 read-only UI

## 목적

PR #108 (wave2-trash-retention-config) 후속. 운영자가 현재 보존 일수를 yml 직접 열지 않고도
`/admin/trash/policy` 페이지에서 확인할 수 있게 한다. mutation은 v1.x++.

## 범위

**In-scope**
- Backend: `GET /api/admin/trash/policy` — `AdminTrashPolicyDto { retentionDays }` 반환. ADMIN-only.
- Frontend: `/admin/trash/policy` 페이지 + hook + types + AdminSideNav 링크.
- 변경 절차 안내(yml + 재기동) + cron 운영 cross-link `/admin/system`.
- docs/02 §7.x 신규 endpoint + docs/04 §8.3 closure 마커.

**Out-of-scope (YAGNI)**
- 보존 일수 mutation API (PUT) — `@ConfigurationProperties` 부팅 바인딩, runtime 변경은 v1.x++.
- cron 운영 상태(enabled/cron/zone) — 별도 endpoint(`/admin/system`, Wave 1 T3 + admin-cron-toggle #102 진행 중)가 진실의 출처.
- 폴더/파일별 fine-grained 보존 정책 — v1.x++.

## 설계 결정

1. **신규 controller(AdminTrashPolicyController) 분리** — listing/bulk이 있는 AdminTrashController와 책임 분리. 1 endpoint·1 책임 (KISS).
2. **TrashRetentionProperties 재사용** — PR #108에서 외부화한 record 그대로 의존. 새 properties 0.
3. **Cron 분리** — `/admin/trash/policy`는 retention 정적 값에만 책임. cron 상태는 admin-cron-toggle (#102)이 DB-backed runtime mutation으로 발전시키는 영역과 정확히 분리.
4. **viewer-only**: 변경은 yml + 재기동 안내. 실수로 GUI에서 즉시 변경되어 hard purge가 갑자기 일어나는 사고 방지.

## 수용 기준

- [ ] `GET /api/admin/trash/policy` 200 (ADMIN), 401, 403 (MEMBER/AUDITOR) 매트릭스 통과.
- [ ] `/admin/trash/policy` 페이지 retentionDays + 변경 절차 + cron cross-link 노출.
- [ ] AdminSideNav에 "휴지통 정책" 항목 (ADMIN-only).
- [ ] non-default retention 값(예: 14)도 그대로 노출 (회귀 가드).
- [ ] DEFERRED_ITEMS에서 '정책' 제거 (closure).
- [ ] backend admin.trash slice + frontend page/sidenav test GREEN.
- [ ] typecheck/lint exit 0.

## 위험 / 롤백

- 위험 ↓: backend 신규 endpoint + frontend 신규 페이지, 기존 wire 0 변경.
- 롤백: revert.
