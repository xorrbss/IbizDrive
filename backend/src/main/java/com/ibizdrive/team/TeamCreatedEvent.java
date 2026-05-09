package com.ibizdrive.team;

import java.util.UUID;

/**
 * Team 생성 도메인 이벤트 — Plan A Task 16.
 *
 * <p>{@link TeamService#create}가 트랜잭션 commit 후 publish.
 * 리스너(Plan A Task 28 TeamAuditListener 등)가 audit / projection / cache 무효화에 사용.
 */
public record TeamCreatedEvent(UUID teamId, UUID createdBy, String name) {}
