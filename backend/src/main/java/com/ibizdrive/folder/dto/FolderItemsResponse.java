package com.ibizdrive.folder.dto;

import java.util.List;

/**
 * {@code GET /api/folders/{id}/items} 응답 wrapper — {@code 200 { items: [...] }}.
 *
 * <p>최상위에 단일 키({@code items})를 두는 패턴은 {@link FolderDetailResponse}/{@code Map.of("tree", ...)}와
 * 일관 — 향후 pagination cursor를 추가할 때 wrapper 확장이 깨지지 않도록 한다 (v1.x).
 */
public record FolderItemsResponse(List<FolderItemDto> items) {}
