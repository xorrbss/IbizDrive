package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;

import java.util.List;
import java.util.UUID;

/**
 * Wave 2 T9 follow-up — admin global trash bulk response (spec §3.1).
 *
 * <p>응답 status는 부분 실패에도 항상 200 — 운영 진단은 {@code failed[]} 배열로. 전체 실패도
 * 200 + 모든 항목이 {@code failed}. status 분기(2xx/4xx)는 cap/action 검증 실패에만 적용.
 *
 * <p>{@code FailedItem.error}는 단건 예외 이름을 wire 문자열로 매핑한 안정적 enum-like 값:
 * {@code "NOT_FOUND"}, {@code "NAME_CONFLICT"} 등. 새 에러 코드 추가 시 docs/02 §8 동기화.
 */
public record AdminTrashBulkResponseDto(
    List<Item> succeeded,
    List<FailedItem> failed
) {
    public record Item(TrashItemType type, UUID id) {}
    public record FailedItem(TrashItemType type, UUID id, String error) {}
}
