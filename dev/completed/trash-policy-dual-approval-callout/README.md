# trash-policy-dual-approval-callout — /admin/trash/policy 페이지 dual-approval 의존성 명시

- 시작: 2026-05-09
- wave2-trash-policy-viewer (PR #114) 후속.
- `retention_change`가 docs/04 §15.4 dual-approval workflow 대상으로 등록됐으나 현재 페이지 안내에 반영 안 됨. 운영자가 yml 직접 변경 권장이라 v1.x 무중단 변경에 dual-approval 거치는 단계임을 사전 인지하도록 한 줄 callout 추가.
- 변경: 헤더 설명 + "보존 일수 변경 방법" 섹션 끝에 dual-approval 의존성 1줄 + docs/04 §15.4 backlink 1줄.
- 회귀 가드: 기존 page.test.tsx에 dual-approval 텍스트 노출 검증 1 케이스 추가.
- 머지 후 `dev/completed/`로 이동.
