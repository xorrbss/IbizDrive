package com.ibizdrive.folder;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Folder#assignScope(ScopeType, UUID)} 도메인 메서드 단위 테스트 — Plan A Task 7
 * (team-centric pivot).
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.2.
 * 순수 단위 테스트 — V13의 NOT NULL DB 제약은 IT 레벨에서 검증, 본 테스트는
 * 입력 검증과 raw-String ↔ enum 변환만 확인한다.
 */
class FolderScopeTest {

    @Test
    void scopeFieldsAreReadable() {
        Folder folder = new Folder();
        UUID scopeId = UUID.randomUUID();

        folder.assignScope(ScopeType.TEAM, scopeId);

        assertThat(folder.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(folder.getScopeId()).isEqualTo(scopeId);

        UUID otherId = UUID.randomUUID();
        folder.assignScope(ScopeType.DEPARTMENT, otherId);

        assertThat(folder.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(folder.getScopeId()).isEqualTo(otherId);
    }

    @Test
    void assignScopeRejectsNull() {
        Folder folder = new Folder();

        assertThatThrownBy(() -> folder.assignScope(null, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scopeType");

        assertThatThrownBy(() -> folder.assignScope(ScopeType.TEAM, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scopeId");
    }
}
