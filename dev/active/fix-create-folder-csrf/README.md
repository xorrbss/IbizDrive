# fix-create-folder-csrf — POST /api/folders X-CSRF-TOKEN 헤더 누락 회귀

- 시작: 2026-05-09
- 운영 hotfix — ADMIN role 운영자도 "폴더 생성 권한이 없습니다"로 거부되던 사용자 보고.
- 원인: `api.createFolder` fetch 헤더에 CSRF 토큰 누락 → Spring CSRF filter가 403 차단.
- 수정: 다른 mutation 패턴(`'X-CSRF-TOKEN': csrf`) 동형으로 1줄 추가 + 회귀 가드 4 tests.
- 머지 후 `dev/completed/`로 이동.
