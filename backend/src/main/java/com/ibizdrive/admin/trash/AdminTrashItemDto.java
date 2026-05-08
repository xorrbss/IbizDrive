package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;

import java.time.Instant;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash row DTO (spec §4.4).
 *
 * <p>user-facing {@code TrashItemDto}와 분리 — admin 전용 메타(owner, originalParent name/path,
 * sizeBytes) 노출. {@code originalParentId == null} = root.
 *
 * <p>{@code sizeBytes}는 항상 not-null:
 * <ul>
 *   <li>file: 자기 자신 {@code size_bytes}.</li>
 *   <li>folder: subtree size — 자기 자신 + 모든 하위 폴더의 file size 합 (Wave 2 T9 follow-up
 *       folder-subtree-size). 빈 폴더는 0. 운영자가 휴지통에서 큰 폴더를 식별 가능.</li>
 * </ul>
 *
 * <p>V10 — {@code deletedById}/{@code deletedByEmail}: 삭제 actor (cross-owner 추적).
 * NULL 의미: (a) V10 적용 이전 삭제분 (backfill 미실시), (b) deleter 계정 hard-delete (FK
 * ON DELETE SET NULL), (c) 알 수 없음. UI에서는 "—"로 렌더한다.
 *
 * <p>full-path-resolve follow-up — {@code originalParentPath}: 삭제 직전 부모 폴더의 절대 경로.
 * leading {@code /} 포함, trailing slash 없음 (예: {@code /회사/팀A/문서}). 부모가 root였던 경우
 * {@code /<parentName>}, {@code originalParentId == null}이면 path도 null. 부모 chain은
 * 살아있는/삭제된 폴더 모두 따라가며 (휴지통의 부모 row 보존 정책), 데이터 cycle 방지로 depth 100
 * 상한. 같은 이름의 폴더가 여러 곳에 존재해도 운영자가 항목을 즉시 식별 가능.
 * {@code originalParentName}은 path 계산 실패(데이터 corruption)에도 표시 가능한 fallback 용도로 유지.
 */
public record AdminTrashItemDto(
    UUID id,
    String name,
    TrashItemType type,
    Instant deletedAt,
    Instant purgeAfter,
    UUID ownerId,
    String ownerEmail,
    UUID originalParentId,
    String originalParentName,
    String originalParentPath,
    Long sizeBytes,
    UUID deletedById,
    String deletedByEmail
) {}
