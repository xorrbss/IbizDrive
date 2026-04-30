---
Last Updated: 2026-04-29
Status: ⏳ in progress
Owner: frontend-m4-m10 worktree
---

# M15 — Layout Extras (Plan)

## 출처
docs/01 §18 row 15 — `SortChip + ViewSwitch + StorageBar + RightPanel 탭`

## 범위 (M15.x)

| Phase | 산출물 | 비고 |
|---|---|---|
| M15.0 | dev-docs bootstrap (plan/context/tasks) | - |
| M15.1 | **SortChip** — `FolderToolbar`에 정렬 드롭다운 (name/updatedAt/size × asc/desc), URL `?sort=&dir=` 동기화 | `useSortParams` 이미 존재 (read-only) → write 측은 router.replace로 직접 |
| M15.2 | **ViewSwitch** — `FolderToolbar` 우측 List/Grid 토글, URL `?view=list\|grid` (default list) | M16 Grid 본체 전 UI/state만. Grid 본체는 M16 |
| M15.3 | **StorageBar** — Sidebar 하단 (TrashLink 위), 사용량/한도 placeholder + progress bar | mock (`api.getStorageQuota`) |
| M15.4 | **RightPanel 탭** — 세부정보/버전/활동/권한 (4탭, 세부정보 외 placeholder) | 기존 RightPanel body를 탭 0번으로 이동 |
| M15.5 | closure (dev-docs archive + progress.md + PR 갱신) | - |

## 비범위

- Grid View 본체 (FileRow 카드 모드, FileTable 분기) — **M16**
- 버전/활동/권한 탭 실내용 — 백엔드 API 미정 (versions: A5 진행중, audit: 일부, permissions: 임시 mock)
- StorageBar 실수치 — 백엔드 quota API 미정 (mock 75% 사용 placeholder)
- 정렬을 백엔드에 위임하는 변경 — 현 mock은 frontend sort, M15.1도 frontend sort 유지

## 핵심 결정 사항 (사전)

1. **URL이 진실 출처 (docs/01 §1.1)**: SortChip / ViewSwitch 모두 search params로 동기화 (Zustand 복제 X). `useSortParams` 패턴 그대로 확장.
2. **ViewSwitch state는 URL ?view**: 새로고침/공유 시 view mode 보존. Zustand 사용 X.
3. **StorageBar는 placeholder mock**: `api.getStorageQuota` 추가 (used/total bytes), `useStorageQuota` 훅. 실수치는 백엔드 후 교체.
4. **RightPanel 탭 — 단일 컴포넌트 내부 분기**: 별도 파일 분리하지 않음 (KISS). 탭 라벨/상태는 컴포넌트 내부 useState (URL 동기화 비범위 — 패널 자체가 ?file에 종속).
5. **세부정보 외 탭은 명시적 "준비 중" placeholder**: empty 가짜 컨텐츠 X, 명확한 coming-soon 메시지.

## 구현 순서 / 격리

각 phase는 독립 commit:
- M15.1: `feat(M15.1): SortChip + URL ?sort&?dir 양방향`
- M15.2: `feat(M15.2): ViewSwitch + URL ?view`
- M15.3: `feat(M15.3): StorageBar + useStorageQuota`
- M15.4: `feat(M15.4): RightPanel 4-tab`
- M15.5: closure
