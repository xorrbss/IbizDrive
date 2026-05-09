# format-bytes-tb-nan-guard — formatBytes TB 단위 + NaN/Infinity 가드

- 시작: 2026-05-09
- `frontend/src/lib/formatBytes.ts` 두 가지 작은 회귀 보강:
  1. **TB 단위 추가** — 1024 GB 이상이 "1500.0 GB"가 아닌 "1.5 TB"로 표기. v1.x storage.usedBytes 증가 대비.
  2. **NaN/Infinity 폴백** — 비정상 입력 시 `'-'` 반환. 일시적 nullable API 반환 시 "NaN GB" 표기 회귀 방지.
- 음수는 실 사용 시 backend 자동 계산이라 발생 가능성 낮음 — 별도 처리 X.
- 회귀 가드: 기존 4 케이스 + TB(2) + NaN/Infinity(2) = 8 케이스 총.
- 머지 후 `dev/completed/`로 이동.
