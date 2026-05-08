# Tasks — trash retention externalization

## P1 신규 파일
- [x] TrashRetentionProperties (record + 0/음수 보정)
- [x] TrashConfig (@EnableConfigurationProperties)
- [x] TrashRetentionPropertiesTest (3 cases)

## P2 기존 수정
- [x] FileMutationService — PURGE_DAYS 제거 + DI
- [x] FolderMutationService — PURGE_DAYS 제거 + DI
- [x] application.yml — app.trash.retention-days: 30
- [x] FileMutationServiceTest TestConfig — 5번째 인자
- [x] FolderMutationServiceTest TestConfig — 5번째 인자

## P3 docs
- [x] docs/02 §6.5 — 외부화 명시
- [x] docs/04 §9.2 — 운영자 가이드
- [x] progress.md — 트랙 회고

## P4 검증
- [x] gradle compileJava + compileTestJava (cross-module)
- [x] gradle test TrashRetentionPropertiesTest

## P5 PR
- [ ] dev-process 정리
- [ ] feat PR + CI + merge
- [ ] archive PR
