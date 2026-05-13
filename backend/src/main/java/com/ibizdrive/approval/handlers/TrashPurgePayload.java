package com.ibizdrive.approval.handlers;

import java.util.List;
import java.util.UUID;

/**
 * `trash_purge` action payload (ADR #47, docs/02 §2.11 Tier 0).
 *
 * <p>`PendingAdminApproval.payloadJson`에 Jackson 직렬화된 형태로 저장. handler가
 * approve transition 시 deserialize 후 {@link com.ibizdrive.admin.trash.AdminTrashService#bulk}로
 * 위임 — single-approver 경로(`POST /api/admin/trash/bulk` action='purge')와 동일 service라
 * per-item audit 발생도 그대로.
 *
 * <p>{@link Item#type}은 wire 일관성 위해 lowercase 문자열({@code "file"} 또는 {@code "folder"}) 보존.
 * {@link com.ibizdrive.admin.trash.AdminTrashBulkRequestDto.Item}와 동일 직렬화.
 *
 * <p>self-approval 가드(ADR #47): secondary admin UUID가 payloadJson에 포함되면 substring 일치로
 * 차단된다. items[].id는 file/folder UUID — secondary admin UUID와 충돌 가능성은 무시 가능
 * (Tier 0). reason 문자열에 secondary admin id를 우연히 적는 운영자 행위만 주의.
 *
 * @param items  1..200개 항목 — controller bulk 검증과 정합
 * @param reason primary 요청 사유 (audit 트레일용, optional)
 */
public record TrashPurgePayload(
    List<Item> items,
    String reason
) {
    public record Item(String type, UUID id) {}
}
