package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin이 신규 user를 초대(생성) 시 publish — m-admin-entry-rewrite, ADR #21 closure.
 *
 * <p>{@link AdminAuditListener}가 구독해 {@code admin.user.created} audit row INSERT.
 * SignupService의 {@link com.ibizdrive.auth.UserRegisteredEvent}와 별개 — admin이 생성한
 * 유저는 셀프 가입과 다른 경로(타인이 생성, 강제 PW 변경 필요)이므로 이벤트도 분리.
 *
 * @param userId  새로 생성된 user의 id
 * @param actorId 초대를 수행한 관리자의 user id (audit actor)
 * @param email   생성된 user의 정규화된 email (lowercase)
 */
public record AdminUserCreatedEvent(UUID userId, UUID actorId, String email) {
}
