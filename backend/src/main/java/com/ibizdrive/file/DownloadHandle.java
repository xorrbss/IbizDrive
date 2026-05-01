package com.ibizdrive.file;

import java.io.InputStream;

/**
 * {@link FileDownloadService#download} 반환 — 메타데이터 + 바이트 스트림 묶음 (A15.5).
 *
 * <p>{@code stream}은 호출자(controller)가 close 책임을 진다 (try-with-resources 또는
 * Spring의 {@code InputStreamResource} 흐름).
 */
public record DownloadHandle(FileItem file, FileVersion version, InputStream stream) {
}
