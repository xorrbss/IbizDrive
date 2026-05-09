package com.ibizdrive.folder;

/**
 * Workspace scope discriminator — Folder/FileItem이 속한 스코프 종류를 식별한다.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.2.
 * V13 Flyway 마이그레이션이 {@code folders.scope_type VARCHAR(20)} NOT NULL 컬럼을
 * 추가하며, 허용 값은 lowercase ({@code 'department'}, {@code 'team'}) — DB CHECK가
 * 진실의 출처(CLAUDE.md §3 원칙 6).
 *
 * <p>Java 표현형은 enum, DB 표현형은 lowercase String — Team.Visibility / Folder.auditLevel과
 * 동일한 raw-String column + enum-via-getter 패턴.
 */
public enum ScopeType {
    DEPARTMENT("department"),
    TEAM("team");

    private final String dbValue;

    ScopeType(String v) {
        this.dbValue = v;
    }

    public String dbValue() {
        return dbValue;
    }

    public static ScopeType fromDb(String s) {
        for (ScopeType t : values()) {
            if (t.dbValue.equals(s)) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown scope_type: " + s);
    }
}
