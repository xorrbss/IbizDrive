package com.ibizdrive.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.team.TeamCreatedEvent;
import com.ibizdrive.team.TeamMemberAddedEvent;
import com.ibizdrive.team.TeamMemberRemovedEvent;
import com.ibizdrive.team.TeamMemberRoleChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan A Task 28 — Team 도메인 이벤트를 audit_log row로 변환.
 *
 * <p>{@link com.ibizdrive.team.TeamService}가 publish하는 도메인 이벤트를 수신해
 * {@link AuditService#record}로 audit row INSERT. {@link ShareAuditListener} 패턴의 변형:
 *
 * <ul>
 *   <li>{@link TransactionalEventListener}로 outer 트랜잭션 commit 후 발화 — outer rollback 시
 *       audit 발생 차단 (Plan A Task 16 audit 위임 정책의 안전장치).</li>
 *   <li>ADR #24: 발생 실패는 ERROR 로그 후 swallow (비즈니스 흐름 보호).</li>
 * </ul>
 *
 * <p>매핑:
 * <ul>
 *   <li>{@link TeamCreatedEvent} → {@link AuditEventType#TEAM_CREATED} ({@code afterState = {name}})</li>
 *   <li>{@link TeamMemberAddedEvent} → {@link AuditEventType#TEAM_MEMBER_ADDED} ({@code afterState = {userId}})</li>
 *   <li>{@link TeamMemberRemovedEvent} → {@link AuditEventType#TEAM_MEMBER_REMOVED} ({@code beforeState = {userId}})</li>
 *   <li>{@link TeamMemberRoleChangedEvent} → {@link AuditEventType#TEAM_MEMBER_ROLE_CHANGED} ({@code beforeState = {role}}, {@code afterState = {role}})</li>
 * </ul>
 */
@Component
public class TeamAuditListener {

    private static final Logger log = LoggerFactory.getLogger(TeamAuditListener.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TeamAuditListener(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@link TeamCreatedEvent} 수신 → {@link AuditEventType#TEAM_CREATED} audit row 생성.
     *
     * <p>{@code afterState}에 팀 이름을 JSON으로 보존. outer 트랜잭션 commit 후 발화하므로
     * rollback 시 audit 미생성 (audit-but-no-team 불일치 방지).
     *
     * @param event TeamService#create가 publish한 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamCreated(TeamCreatedEvent event) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", event.name());
        emit(new AuditEvent(
            AuditEventType.TEAM_CREATED,
            event.createdBy(),
            null, null,
            AuditTargetType.TEAM,
            event.teamId(),
            null,
            toJson(after),
            null
        ));
    }

    /**
     * {@link TeamMemberAddedEvent} 수신 → {@link AuditEventType#TEAM_MEMBER_ADDED} audit row 생성.
     *
     * <p>{@code afterState}에 추가된 멤버 userId를 JSON으로 보존.
     *
     * @param event TeamService#invite가 publish한 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamMemberAdded(TeamMemberAddedEvent event) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("userId", event.userId().toString());
        emit(new AuditEvent(
            AuditEventType.TEAM_MEMBER_ADDED,
            event.invitedBy(),
            null, null,
            AuditTargetType.TEAM,
            event.teamId(),
            null,
            toJson(after),
            null
        ));
    }

    /**
     * {@link TeamMemberRemovedEvent} 수신 → {@link AuditEventType#TEAM_MEMBER_REMOVED} audit row 생성.
     *
     * <p>{@code beforeState}에 제거된 멤버 userId를 JSON으로 보존 — 삭제 후 복구 추적용.
     *
     * @param event TeamService#remove가 publish한 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamMemberRemoved(TeamMemberRemovedEvent event) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("userId", event.userId().toString());
        emit(new AuditEvent(
            AuditEventType.TEAM_MEMBER_REMOVED,
            event.removedBy(),
            null, null,
            AuditTargetType.TEAM,
            event.teamId(),
            toJson(before),
            null,
            null
        ));
    }

    /**
     * {@link TeamMemberRoleChangedEvent} 수신 → {@link AuditEventType#TEAM_MEMBER_ROLE_CHANGED} audit row 생성.
     *
     * <p>{@code beforeState}에 변경 전 role, {@code afterState}에 변경 후 role을 JSON으로 보존.
     * 역할 전환 이력 추적 및 감사자의 before/after 비교 지원.
     *
     * @param event TeamService#changeRole (Plan A2 T3)이 publish한 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamMemberRoleChanged(TeamMemberRoleChangedEvent event) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("role", event.oldRole().name());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("role", event.newRole().name());
        emit(new AuditEvent(
            AuditEventType.TEAM_MEMBER_ROLE_CHANGED,
            event.actorId(),
            null, null,
            AuditTargetType.TEAM,
            event.teamId(),
            toJson(before),
            toJson(after),
            null
        ));
    }

    private void emit(AuditEvent event) {
        try {
            auditService.record(event);
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", event.eventType(), ex);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit state serialization failed", e);
        }
    }
}
