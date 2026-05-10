package com.ibizdrive.folder.dto;

import java.util.UUID;

/**
 * spec §5.6 preview 응답 — 정리될 명시 권한 grant 참조.
 * 실제 row 정보는 {@code id}와 함께 (subjectType, subjectId, preset)을 표시해 다이얼로그가
 * "어떤 권한이 사라지는지" 사용자에게 명시할 수 있게 한다.
 */
public record PermissionRef(
    UUID id,
    String subjectType,
    UUID subjectId,
    String preset
) {}
