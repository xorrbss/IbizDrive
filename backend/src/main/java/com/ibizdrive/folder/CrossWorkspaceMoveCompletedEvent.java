package com.ibizdrive.folder;

import java.time.Instant;
import java.util.UUID;

/**
 * spec §5.6 step 8 — cross-workspace move 완료 이벤트. SSE 인프라 부재라 본 event는 audit
 * listener와 (v1.x에서 추가될) SSE broadcaster의 hook 역할.
 *
 * <p>Plan D Task 15/19 — Task 19 deliverable을 Task 15로 앞당겨 step 8 publish 가능하게 함.
 *
 * @param resourceType       "folder" 또는 "file"
 * @param sourceResourceId   이동된 root resource id
 * @param fromScopeType      source workspace scope type
 * @param toScopeType        destination workspace scope type
 * @param toScopeId          destination workspace scope id
 * @param subtreeFolderCount 이동된 폴더 수 (file path는 0)
 * @param subtreeFileCount   이동된 파일 수
 * @param revokedShareCount  revoke된 share 수
 * @param actorId            이동을 요청한 사용자 id
 * @param occurredAt         이벤트 발생 시각
 */
public record CrossWorkspaceMoveCompletedEvent(
    String resourceType,
    UUID sourceResourceId,
    ScopeType fromScopeType,
    ScopeType toScopeType,
    UUID toScopeId,
    int subtreeFolderCount,
    int subtreeFileCount,
    int revokedShareCount,
    UUID actorId,
    Instant occurredAt
) {}
