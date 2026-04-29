package com.ibizdrive.folder;

import com.ibizdrive.common.error.ResourceNotFoundException;

/**
 * 폴더가 활성 상태로 존재하지 않을 때 — 404 매핑.
 *
 * <p>{@link com.ibizdrive.common.error.GlobalExceptionHandler}가 부모 타입
 * {@link ResourceNotFoundException}을 받아 404 envelope으로 변환 — 별도 매핑 불필요.
 *
 * <p>대상 케이스:
 * <ul>
 *   <li>존재하지 않는 폴더 ID (한 번도 생성된 적 없음)</li>
 *   <li>soft-deleted 폴더 (휴지통) — mutation 흐름에서는 활성 폴더만 대상이므로 동일하게 404</li>
 *   <li>move의 ancestor walk 도중 ancestor row 부재 (데이터 corruption 또는 race)</li>
 * </ul>
 */
public class FolderNotFoundException extends ResourceNotFoundException {
    public FolderNotFoundException(String message) {
        super(message);
    }
}
