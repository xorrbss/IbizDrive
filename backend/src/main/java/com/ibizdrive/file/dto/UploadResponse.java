package com.ibizdrive.file.dto;

import com.ibizdrive.file.UploadResult;

import java.util.UUID;

/**
 * {@code POST /api/files} 응답 본문 — 신규 파일 INSERT (201) 또는 새 version append (200) 공통.
 *
 * <p>{@link UploadResult}의 wire 표현. {@code newFile=true}이면 controller가 201 Created를,
 * {@code false}이면 200 OK를 반환한다 (frontend M5 인터페이스 합치).
 */
public record UploadResponse(FileDto file, UUID versionId, int versionNumber, boolean newFile) {
    public static UploadResponse from(UploadResult r) {
        return new UploadResponse(
            FileDto.from(r.file()),
            r.version().getId(),
            r.version().getVersionNumber(),
            r.newFile()
        );
    }
}
