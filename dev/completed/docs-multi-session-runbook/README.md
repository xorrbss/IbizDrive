# docs-multi-session-runbook — docs/04 §15.7 multi-session 자율 작업 트러블슈팅

- 시작: 2026-05-09
- 본 세션(2026-05-09)이 발견한 운영 패턴을 docs/04 §15 사내 베타 운영 런북에 추가:
  - `mergeStateStatus` 5단계 분기 (UNSTABLE/UNKNOWN/DIRTY/CLEAN/MERGED)
  - 백그라운드 폴링 패턴 (30s 간격, cache miss 회피)
  - 다른 세션이 master에 직접 commit한 경우 회피 + 본 세션 손대지 않음
  - rebase + force-push-with-lease 충돌 복구
- 코드 0줄. backend/frontend 무변경.
- 머지 후 `dev/completed/`로 이동.
