package com.ibizdrive.file;

/**
 * {@link FileUploadService#upload} 반환 값 — 신규 파일 행과 신규 버전 행을 함께 노출 (A15, docs/02 §6.1).
 *
 * <p>{@code newFile = true}는 {@code files} row가 INSERT된 케이스(신규 파일 또는 RENAME 분기)이며,
 * {@code false}는 {@link UploadResolution#NEW_VERSION} 분기로 기존 row를 재사용한 케이스다. controller가
 * 응답 envelope을 분기할 때 사용 (신규 파일은 201, 새 버전은 200).
 */
public record UploadResult(FileItem file, FileVersion version, boolean newFile) {
}
