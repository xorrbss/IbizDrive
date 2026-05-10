package com.ibizdrive.folder;

/**
 * spec §1.3 + §5.6 — cross-workspace move 시 destinationFolderId가 null(=root 직접 이동) 또는 자기 자신/
 * 후손인 경우. HTTP 400 + {@code ERR_INVALID_DESTINATION}.
 *
 * <p>{@code IllegalArgumentException}({@code BAD_REQUEST})과 분리한 이유: frontend가 destination
 * picker로 재입력 유도하는 분기 가능한 envelope가 필요.
 */
public class InvalidMoveDestinationException extends RuntimeException {
    public InvalidMoveDestinationException(String message) {
        super(message);
    }
}
