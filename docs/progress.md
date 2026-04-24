# 진행 상황 (Progress Log)

> 각 세션이 완료될 때마다 **최상단에 추가**합니다. 기존 내용은 보존.
> 양식: `CLAUDE.md §7` 또는 각 BRIEF의 회고 섹션 참조.

---

## 2026-04-24 — M1 완료

### 완료
- [M1] folderId 중심 catch-all 라우팅 (`/files/[...parts]`)
- [M1] FolderTree / Breadcrumb URL 동기화
- [M1] canonical redirect (decodeURI 비교, 한글 URL 대응)
- [M1] 프로젝트 기본 셋업 (Providers, 훅, 스토어)
- [M1] `/files` → `/files/root` 리다이렉트
- [M1] Explorer 레이아웃 (사이드바 + 메인)
- [M1] loading / error / not-found 상태 페이지

### 계약 파일 추가
- src/lib/normalize.ts      (docs/02 §3)
- src/lib/queryKeys.ts       (docs/01 §6.1)
- src/lib/folderPath.ts      (docs/01 §17.3)
- src/lib/api.ts             (MOCK — M5에서 실제 API로 교체)

### 다음 세션 컨텍스트 (M2: FolderTree 심화 + TrashLink + QuickAccess 또는 M3: FileTable)
- api.ts는 현재 mock. 백엔드 나오면 실제 fetch로 교체. 계약은 docs/02 §7.3
- 서버 컴포넌트 전환은 M3에서 (notFound/redirect 조합)
- canonical redirect는 클라이언트에서 useEffect. 깜빡임 있으면 M3에서 서버 redirect로
- Next.js 16에서 Windows pnpm EPERM 이슈 발생 → 15.3.2로 다운그레이드, npm 사용
- next-env.d.ts의 `.next/types/routes.d.ts` import는 Next.js 16 전용 → 15에서는 무시됨

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (코드 템플릿 그대로 반영)

---

## (세션 기록이 여기에 쌓입니다)
