package com.ibizdrive.admin.trash;

import java.util.List;

/**
 * Wave 2 T9 — admin trash listing 응답 envelope. {@code nextCursor=null}이면 마지막 페이지.
 */
public record AdminTrashPage(
    List<AdminTrashItemDto> items,
    String nextCursor
) {}
