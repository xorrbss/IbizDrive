package com.ibizdrive.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.stream.Stream;

/**
 * 감사 이벤트 타입 (docs/03 §4.1, ADR #24).
 *
 * <p>총 52개 값. {@code frontend/src/types/audit.ts}의 {@code AuditEventType} 유니언과 1:1 동기 —
 * 변경 시 docs/03 §4.1 + frontend types/audit.ts 양쪽 갱신 (계약).
 *
 * <p>Java enum 이름은 {@code UPPER_SNAKE_CASE}, JSON wire format은 {@code lower.dot.notation}.
 * {@link #wire()}가 wire format을 반환하고 {@link #from(String)}이 역변환한다.
 */
public enum AuditEventType {

    // 파일 (8)
    FILE_VIEWED("file.viewed"),
    FILE_DOWNLOADED("file.downloaded"),
    FILE_UPLOADED("file.uploaded"),
    FILE_RENAMED("file.renamed"),
    FILE_MOVED("file.moved"),
    FILE_DELETED("file.deleted"),
    FILE_RESTORED("file.restored"),
    FILE_PURGED("file.purged"),

    // 버전 (3)
    VERSION_CREATED("version.created"),
    VERSION_RESTORED("version.restored"),
    VERSION_DOWNLOADED("version.downloaded"),

    // 폴더 (7)
    FOLDER_CREATED("folder.created"),
    FOLDER_RENAMED("folder.renamed"),
    FOLDER_MOVED("folder.moved"),
    FOLDER_DELETED("folder.deleted"),
    FOLDER_RESTORED("folder.restored"),
    FOLDER_PURGED("folder.purged"),
    FOLDER_AUDIT_LEVEL_CHANGED("folder.audit_level_changed"),

    // 권한 / 공유 (7)
    PERMISSION_GRANTED("permission.granted"),
    PERMISSION_REVOKED("permission.revoked"),
    PERMISSION_EXPIRED("permission.expired"),
    PERMISSION_CHANGED("permission.changed"),
    SHARE_CREATED("share.created"),
    SHARE_REVOKED("share.revoked"),
    SHARE_EXPIRED("share.expired"),

    // 인증 (8)
    USER_REGISTERED("user.registered"),
    USER_LOGIN_SUCCESS("user.login.success"),
    USER_LOGIN_FAILED("user.login.failed"),
    USER_LOGOUT("user.logout"),
    USER_PASSWORD_CHANGED("user.password.changed"),
    USER_PASSWORD_FORGOT_REQUESTED("user.password.forgot_requested"),
    USER_PASSWORD_RESET("user.password.reset"),
    USER_MFA_ENABLED("user.mfa.enabled"),

    // 관리자 (11)
    ADMIN_USER_CREATED("admin.user.created"),
    ADMIN_USER_UPDATED("admin.user.updated"),
    ADMIN_USER_DEACTIVATED("admin.user.deactivated"),
    ADMIN_ROLE_CHANGED("admin.role.changed"),
    ADMIN_QUOTA_CHANGED("admin.quota.changed"),
    ADMIN_LEGAL_HOLD_PLACED("admin.legal_hold.placed"),
    ADMIN_LEGAL_HOLD_RELEASED("admin.legal_hold.released"),
    ADMIN_DEPARTMENT_CREATED("admin.department.created"),
    ADMIN_DEPARTMENT_UPDATED("admin.department.updated"),
    ADMIN_DEPARTMENT_DEACTIVATED("admin.department.deactivated"),
    ADMIN_CRON_TOGGLED("admin.cron.toggled"),

    // 시스템 (3)
    SYSTEM_BACKUP_COMPLETED("system.backup.completed"),
    SYSTEM_PURGE_EXECUTED("system.purge.executed"),
    STORAGE_ORPHAN_CLEANED("storage.orphan.cleaned"),

    // 팀 (4)
    TEAM_CREATED("team.created"),
    TEAM_MEMBER_ADDED("team.member.added"),
    TEAM_MEMBER_REMOVED("team.member.removed"),
    TEAM_MEMBER_ROLE_CHANGED("team.member.role_changed"),

    // 감사 로그 자체 (1)
    AUDIT_EXPORTED("audit.exported");

    private static final Map<String, AuditEventType> BY_WIRE =
        Stream.of(values()).collect(java.util.stream.Collectors.toMap(AuditEventType::wire, e -> e));

    private final String wire;

    AuditEventType(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static AuditEventType from(String wire) {
        AuditEventType t = BY_WIRE.get(wire);
        if (t == null) {
            throw new IllegalArgumentException("Unknown AuditEventType wire format: " + wire);
        }
        return t;
    }
}
