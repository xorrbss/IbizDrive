task: m-rp-rightpanel-completion / Phase M-RP.2
last_updated: 2026-05-02
working_files:
  # backend
  - backend/src/main/java/com/ibizdrive/file/FileDownloadService.java
  - backend/src/main/java/com/ibizdrive/file/FileVersionController.java
  - backend/src/main/java/com/ibizdrive/file/FileVersionMutationService.java (NEW)
  - backend/src/test/java/com/ibizdrive/file/FileDownloadServiceTest.java
  - backend/src/test/java/com/ibizdrive/file/FileVersionMutationServiceTest.java (NEW)
  - backend/src/test/java/com/ibizdrive/file/FileVersionControllerIT.java
  # frontend
  - frontend/src/lib/api.ts
  - frontend/src/lib/queryKeys.ts
  - frontend/src/lib/api.versions.test.ts
  - frontend/src/hooks/useRestoreVersion.ts (NEW)
  - frontend/src/hooks/useRestoreVersion.test.tsx (NEW)
  - frontend/src/components/files/VersionsTab.tsx
  - frontend/src/components/files/RightPanel.test.tsx
  # docs
  - docs/02-backend-data-model.md
  - docs/03-security-compliance.md
notes: G2 옵션 A 확정 (current_version_id 재지정, 새 version row X, 멱등). ADR #39 closure 시 작성.
