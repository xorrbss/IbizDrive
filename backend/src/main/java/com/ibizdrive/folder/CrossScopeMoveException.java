package com.ibizdrive.folder;

/**
 * Thrown when a folder/file move would cross workspace scope boundaries.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.2 (scope invariant),
 * §5.6 (cross-workspace explicit move action).
 *
 * <p>Plan A enforces same-scope unconditionally — DnD/암묵적 move 경로는 항상 same-scope만 허용.
 * 명시적 cross-workspace move ({@code allowCrossScope: true} + 영향 미리보기 + 트랜잭션 보장)는
 * Plan D scope. HTTP 매핑은 409 + {@code ERR_CROSS_SCOPE_MOVE}.
 */
public class CrossScopeMoveException extends RuntimeException {
    public CrossScopeMoveException() {
        super("ERR_CROSS_SCOPE_MOVE — cross-workspace move requires explicit cross-scope action (spec §5.6)");
    }
}
