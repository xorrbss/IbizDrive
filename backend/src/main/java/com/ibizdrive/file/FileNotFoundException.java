package com.ibizdrive.file;

import com.ibizdrive.common.error.ResourceNotFoundException;

/**
 * 파일이 활성 상태로 존재하지 않을 때 — 404 매핑.
 *
 * <p>{@link com.ibizdrive.common.error.GlobalExceptionHandler}가 부모 타입
 * {@link ResourceNotFoundException}을 받아 404 envelope으로 변환 — 별도 매핑 불필요.
 *
 * <p>대상 케이스:
 * <ul>
 *   <li>존재하지 않는 파일 ID (한 번도 생성된 적 없음)</li>
 *   <li>soft-deleted 파일 (휴지통) — mutation 흐름(rename/move/delete)에서는 활성 파일만 대상</li>
 *   <li>restore 흐름의 역으로, 이미 활성 상태인 파일을 restore 호출했을 때도 동일 매핑</li>
 *   <li>restore 시 {@code original_folder_id}가 가리키는 폴더가 활성 상태가 아닐 때</li>
 * </ul>
 */
public class FileNotFoundException extends ResourceNotFoundException {
    public FileNotFoundException(String message) {
        super(message);
    }
}
