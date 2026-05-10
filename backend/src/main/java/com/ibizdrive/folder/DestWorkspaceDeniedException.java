package com.ibizdrive.folder;

/**
 * spec §5.6 권한 검증 — cross-workspace move 시점에 destination 폴더에 {@code UPLOAD} 권한이
 * 없거나 source 폴더에 {@code EDIT}+{@code SHARE}가 없으면 throw. HTTP 403 + {@code ERR_DEST_WORKSPACE_DENIED}.
 *
 * <p>Plan A의 same-scope move({@code MOVE} + 새 부모 {@code EDIT})와 권한 분기가 다르므로 별도 envelope.
 */
public class DestWorkspaceDeniedException extends RuntimeException {
    public DestWorkspaceDeniedException(String message) {
        super(message);
    }
}
