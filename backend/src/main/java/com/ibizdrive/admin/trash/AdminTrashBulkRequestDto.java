package com.ibizdrive.admin.trash;

import java.util.List;
import java.util.UUID;

/**
 * Wave 2 T9 follow-up — admin global trash bulk request (spec §3.1).
 *
 * <p>{@code action}은 wire상 {@code "restore"} 또는 {@code "purge"} (소문자 정확 일치).
 * 그 외 값은 service에서 {@link IllegalArgumentException} → 글로벌 핸들러 400.
 *
 * <p>{@code items}는 1~200개. 0 또는 201+ → service에서 cap 위반 400.
 * 동일 {@code (type, id)} 중복은 허용 (idempotent — 두 번째 호출은 NOT_FOUND/NO_OP로 failed에 누적).
 *
 * <p>{@code Item.type}은 wire상 {@code "file"} 또는 {@code "folder"} (소문자, {@link
 * com.ibizdrive.trash.TrashItemType#wire()}와 동일). dispatch 시점에 service에서 파싱.
 */
public record AdminTrashBulkRequestDto(
    String action,
    List<Item> items
) {
    public record Item(String type, UUID id) {}
}
