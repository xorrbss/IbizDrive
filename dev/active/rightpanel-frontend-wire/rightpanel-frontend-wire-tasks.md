# Tasks — RightPanel Frontend Wire (P_panel-B)

Last Updated: 2026-05-13

## Phase 상태

| phase | 상태 | 비고 |
|---|---|---|
| P1 — helper (kindLabel) | ✓ 완료 | `lib/fileIcon.ts`에 추가 |
| P2 — detail 탭 7 row wire | ✓ 완료 | viewCount 제외 |
| P3 — 헤더 (file icon + 액션 3개) | ✓ 완료 | 더보기 disabled placeholder |
| P4 — PreviewCard placeholder | ✓ 완료 | kind별 soft 배경 10색 |
| P5 — 탭 라벨 (버전 N) | deferred | versionsCount 필드 추가 후 재오픈 |
| P6 — docs + backlog closure | ✓ 완료 | progress.md entry + v1x-backlog.md strikethrough. PR# TBD followup |
| F — PR 생성 + 머지 후 archive | 대기 | active → completed 이동 |

---

## P1 — helper ✓

- [x] `frontend/src/lib/fileIcon.ts`에 `kindLabel` export 추가 (별도 파일 대신 합치기 — 같은 도메인)

### 작업 전 필독

- `design-reference/panels.jsx` L211~215 `kindLabel(k)` 매핑 표.
- `frontend/src/components/icons/FileTypeIcon.tsx` — `FileTypeIconKind` enum.

### 원본 코드 참조

- `design-reference/panels.jsx`:
  - L211~215: `{folder: "폴더", doc: "문서", pdf: "PDF 문서", sheet: "스프레드시트", slides: "프레젠테이션", image: "이미지", video: "비디오", figma: "Figma 파일", code: "코드", archive: "압축파일"}`.

### 구현 대상

- `frontend/src/lib/fileIcon.ts` — `kindLabel(kind: FileTypeIconKind): string` Record 기반 추가 export. 새 파일은 만들지 않음.

### 검증 참조

- `pnpm typecheck` PASS.

### 문서 반영

- 없음 (helper 내부).

---

## P2 — detail 탭 7 row wire

- [x] `RightPanel.tsx` `PanelBody` rewrite (`<dl>` → `<div>` row 구조)
- [x] mini-avatar 인라인 컴포넌트 (`<DetailUser>` 형태) — colorForUser hash + initialOf 인라인
- [x] mini-avatar-stack 인라인 컴포넌트 — max=4 + "+N" 칩
- [x] `RightPanel.test.tsx` row 가시성 가드 추가

### 작업 전 필독

- `design-reference/panels.jsx` L52~76 (detail-list 8 row 중 viewCount 제외 7 row).
- `frontend/src/components/admin/teams/Avatars.tsx` (colorForUser/initialOf 로직).
- `frontend/src/types/file.ts` `FileItem.owner/sharedWith/folderPath` 타입.

### 원본 코드 참조

```jsx
<DetailRow label="종류" value={kindLabel(file.kind)} />
<DetailRow label="크기" value={formatSize(file.size)} />
<DetailRow label="소유자" value={owner ? (<span className="detail-user"><Avatar user={owner} size={18} /><span>{owner.name}</span></span>) : "—"} />
<DetailRow label="수정한 사람" value={modifiedBy ? (...) : "—"} />
<DetailRow label="수정일" value={formatDateTime(file.modifiedAt)} />
<DetailRow label="공유됨" value={file.shared?.length ? (<AvatarStack ... max={4} />) : "비공개"} />
<DetailRow label="경로" value={<span className="detail-path">...</span>} />
<DetailRow label="위치" value={file.status === "restricted" ? (<span className="tag-restricted">...권한 제한</span>) : "공개 링크 없음"} />
```

### 구현 대상

- `RightPanel.tsx`:
  - `PanelBody({ file }: { file: FileItem })` — 7 row `<div>` grid 구조 (`grid-cols-[80px_1fr]`).
  - `DetailUser({ name, id })` — 미니 아바타(18px) + name. owner/sharedWith 단일 표시용.
  - `DetailAvatarStack({ users, max=4 })` — 18px overlap stack + "+N" 칩.
  - `colorForUser`/`initialOf` — Avatars.tsx 와 동일 로직 인라인 (extract는 YAGNI).
  - `folderPath` → "내 드라이브 / 영업팀 / 계약서" 텍스트 join (folderPath가 undefined 시 "—").
- `RightPanel.test.tsx`:
  - 종류/소유자/공유됨/경로/위치 row 가시성 가드.
  - sharedWith=[] 시 "비공개" 표시.
  - folderPath=[] 시 "—" 또는 "내 드라이브".
  - restricted 미지원 — value="공개 링크 없음" 항상 (FileItem.restricted 가 false면).

### 검증 참조

- `pnpm typecheck && pnpm test --run RightPanel`.
- `frontend/src/components/files/RightPanel.test.tsx` 기존 4탭 가드 회귀 0.

### 문서 반영

- 없음 (UI 정합).

---

## P3 — 헤더 (file icon + 액션 3개)

- [x] `RightPanel.tsx` `<header>` 좌측에 `<FileTypeIcon kind={fileIconKind(file)} size={20}>` 추가
- [x] 헤더 아래 액션 row (`<div role="toolbar">`) — 다운로드/공유/더보기
- [x] 다운로드 = `api.downloadFile(fileId)` 호출
- [x] 공유 = `setTab('permissions')` (디자인 onOpenPermissions 의미)
- [x] 더보기 = disabled placeholder + `aria-label="더보기"` + TODO 주석
- [x] `RightPanel.test.tsx` 액션 버튼 가드 추가

### 작업 전 필독

- `design-reference/panels.jsx` L20~34 (rp-head + rp-title-row + rp-actions).
- `frontend/src/lib/api.ts` L640~660 (`downloadFile(id)`).
- `frontend/src/components/icons/FileTypeIcon.tsx`.

### 원본 코드 참조

```jsx
<div className="rp-head">
  <div className="rp-title-row">
    <FileIcon kind={file.kind} size={18} />
    <span className="rp-title" title={file.name}>{file.name}</span>
    <button className="icon-btn xs" onClick={onClose} title="닫기"><UIIcon name="close" size={13} /></button>
  </div>
  <div className="rp-actions">
    <button className="btn-ghost btn-xs"><UIIcon name="download" /><span>다운로드</span></button>
    <button className="btn-ghost btn-xs" onClick={onOpenPermissions}><UIIcon name="user-plus" /><span>공유</span></button>
    <button className="btn-ghost btn-xs btn-icon-only" title="더보기"><UIIcon name="dots" /></button>
  </div>
</div>
```

### 구현 대상

- `RightPanel.tsx`:
  - `<header>`: file kind icon 20px + 파일명 + 닫기 (현재 닫기 보존).
  - 아래 액션 row — 3 ghost 버튼:
    - 다운로드: lucide-react `Download` + "다운로드", onClick=`api.downloadFile(fileId)`.
    - 공유: lucide-react `UserPlus` + "공유", onClick=`setTab('permissions')`.
    - 더보기: lucide-react `MoreHorizontal`, disabled, aria-label="더보기", TODO.
  - 로딩 중이면 액션 버튼 disabled.

### 검증 참조

- `pnpm typecheck && pnpm test --run RightPanel`.

### 문서 반영

- 없음.

---

## P4 — PreviewCard placeholder

- [x] `RightPanel.tsx` 내부 `PreviewCard({ file })` 컴포넌트 (또는 별도 파일)
- [x] kind별 soft 배경 색상 map
- [x] 큰 FileTypeIcon (size=42 — 360px 패널 폭 정합으로 52→42 축소) + 4 placeholder line div

### 작업 전 필독

- `design-reference/panels.jsx` L195~209 (PreviewCard).

### 원본 코드 참조

```jsx
function PreviewCard({ file }) {
  const map = {
    folder: "var(--accent-soft)", doc: "#E8F0FB", pdf: "#FCE8E4",
    sheet: "#E3F3E9", slides: "#FCEEDA", image: "#F1E6F6",
    video: "#F9E3EA", figma: "#EFE6FA", code: "#E5EAEF", archive: "#EEEBE2",
  };
  return (
    <div className="preview-card" style={{ background: map[file.kind] || "#E8F0FB" }}>
      <FileIcon kind={file.kind} size={52} />
      <div className="preview-lines">
        <div /><div style={{ width: "75%" }} /><div style={{ width: "85%" }} /><div style={{ width: "55%" }} />
      </div>
    </div>
  );
}
```

### 구현 대상

- `RightPanel.tsx` 내부:
  - `RightPanelPreviewCard({ file })` — 위 매핑 그대로 (folder는 Tailwind `bg-accent-soft` 또는 hex).
  - placeholder lines: 4개의 height 6 + 다양 width div + opacity 40%.
  - 위치: 헤더와 탭 사이 (rp-preview wrapper).

### 검증 참조

- `pnpm typecheck && pnpm test --run RightPanel`.
- 시각 회귀 = dev preview 수기.

### 문서 반영

- 없음.

---

## P5 — 탭 라벨 (deferred)

- versions count 노출 후 재오픈. 본 트랙에서는 미진행.

---

## P6 — docs + backlog closure

- [x] `docs/v1x-backlog.md` Tier 1 line 48 strikethrough + closure 마커 (PR # 채워서 followup)
- [x] `docs/progress.md` 본 트랙 entry 추가

### 작업 전 필독

- `docs/v1x-backlog.md` line 48 (현재 RightPanel 디자인 fidelity 라인).
- `docs/progress.md` head — 동일 형식의 closure entry 예시.

### 구현 대상

- backlog: 라인 strikethrough `~~...~~` + closure 표시 + viewCount 분리 명시.
- progress: 본 트랙 phase 별 변경 요약 + 검증 결과.

### 검증 참조

- grep `RightPanel 디자인 fidelity` — closure 마커 확인.

### 문서 반영

- 본 트랙 PR # 정정 (followup commit) — 머지 후.
