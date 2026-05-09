package com.ibizdrive.file;

import com.ibizdrive.folder.ScopeType;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FileItem#assignScope(ScopeType, UUID)} 도메인 메서드 단위 테스트 — Plan A Task 8
 * (team-centric pivot).
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.2.
 * 순수 단위 테스트 — V13의 NOT NULL DB 제약은 IT 레벨에서 검증, 본 테스트는
 * 입력 검증과 raw-String ↔ enum 변환만 확인한다. Task 7의 FolderScopeTest와 동일 패턴.
 */
class FileScopeTest {

    @Test
    void assignScopeStoresValues() {
        FileItem file = new FileItem();
        UUID scopeId = UUID.randomUUID();

        file.assignScope(ScopeType.TEAM, scopeId);

        assertThat(file.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(file.getScopeId()).isEqualTo(scopeId);

        UUID otherId = UUID.randomUUID();
        file.assignScope(ScopeType.DEPARTMENT, otherId);

        assertThat(file.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(file.getScopeId()).isEqualTo(otherId);
    }

    @Test
    void assignScopeRejectsNull() {
        FileItem file = new FileItem();

        assertThatThrownBy(() -> file.assignScope(null, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scopeType");

        assertThatThrownBy(() -> file.assignScope(ScopeType.TEAM, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scopeId");
    }
}
