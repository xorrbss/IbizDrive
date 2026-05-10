package com.ibizdrive.folder.dto;

import java.util.UUID;

/**
 * spec §5.6 preview 응답 — revoke될 share 참조.
 * sharedBy는 share 생성자 user id (감사 명료성).
 *
 * <p>resourceType/resourceId는 nullable — 본 plan에서는 share row 자체만 노출하고 share가
 * 가리키는 resource 정보 풍부화는 별도 task. preview UX는 sharedBy + count로 충분.
 */
public record ShareRef(
    UUID id,
    String resourceType,
    UUID resourceId,
    UUID sharedBy
) {}
