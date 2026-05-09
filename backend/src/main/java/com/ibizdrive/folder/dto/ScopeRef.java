package com.ibizdrive.folder.dto;

import com.ibizdrive.folder.ScopeType;

import java.util.UUID;

/**
 * Workspace scope 참조 — Folder/File 응답 DTO에 임베드되는 {@code scope: { type, id }} 블록.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.3, §5.5.
 * frontend가 응답을 받아 {@code /d/:slug/...} 또는 {@code /t/:slug/...} 라우트로 라우팅할 수 있도록
 * scope discriminator를 명시한다.
 *
 * <p>{@link #type}: lowercase 문자열 ({@code "department"} | {@code "team"}) — {@link ScopeType#dbValue()}와
 * 동치. JSON wire에서 enum 표현이 변동하지 않도록 명시 dbValue 직렬화.
 *
 * <p><b>YAGNI — workspaceName 미포함</b>: spec §5.5는 {@code scope: { type, id, name }}을 명시하지만,
 * Plan A 범위에서는 name 조회를 deferred work로 미룬다. frontend는 {@code GET /api/workspaces/me}
 * (Plan A Task 15) 또는 department/team 상세 endpoint로 name을 별도 resolve한다.
 * 이렇게 하면 본 DTO factory가 entity getter만으로 자족적이며 Workspace/Team service에 결합되지 않는다.
 */
public record ScopeRef(String type, UUID id) {
    /**
     * Entity의 scope getter 결과로부터 ScopeRef를 만든다.
     *
     * <p>defensive null 처리: {@code type}이 null이면 (V13 NOT NULL 이전 상태이거나 detached entity인
     * 경우) {@code null}을 반환 — caller(상위 DTO factory)는 그대로 scope 필드에 set, 응답에서는
     * {@code @JsonInclude(NON_NULL)}이 키를 생략한다.
     */
    public static ScopeRef of(ScopeType type, UUID id) {
        if (type == null) {
            return null;
        }
        return new ScopeRef(type.dbValue(), id);
    }
}
