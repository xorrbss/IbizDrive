package com.ibizdrive.file;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code file_versions} table (docs/02 §2.5, V5 마이그레이션).
 *
 * <p>ADR #29 — A4에서는 schema + DEFERRABLE FK만 도입했고, A5.1에서 entity/repository 승격.
 * 스캔 워커/업로드 commit 트랜잭션은 후속 트랙으로 이월(보장사항 (a)(b)(c) 유지).
 *
 * <p>관계 매핑 정책 (FileItem/Folder와 동일 패턴):
 * <ul>
 *   <li>{@code fileId} / {@code uploadedBy}는 {@code UUID} 단순 컬럼. {@code @ManyToOne} 미사용 —
 *       lazy proxy 비용 + cycle 위험 회피. service layer가 필요 시 명시적으로 fetch.</li>
 *   <li>DB FK ({@code file_versions.file_id REFERENCES files(id) ON DELETE RESTRICT},
 *       {@code uploaded_by REFERENCES users(id)})는 V5에서 강제 — 진실의 출처는 schema.</li>
 * </ul>
 *
 * <p>{@code scan_result JSONB} 컬럼은 의도적으로 entity에 매핑하지 않음 — A5 list endpoint
 * (docs/02 §7.6) 응답 스키마에 포함되지 않으며, JSONB ↔ JPA 변환 의존성을 audit 모듈과 동일하게
 * 회피(JdbcTemplate 분리). 스캐너 워커 도입 시점에 별도 매핑 검토 (KISS — YAGNI).
 *
 * <p>Lombok 미사용 — A4 entity 패턴(Folder/FileItem) 보존.
 */
@Entity
@Table(name = "file_versions")
public class FileVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** S3 객체 키 = UUID. 원본 파일명 절대 미저장 (CLAUDE.md §3 원칙 9). DB UNIQUE. */
    @Column(name = "storage_key", nullable = false, unique = true)
    private UUID storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String checksumSha256;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    /** DB CHECK lowercase(pending/clean/infected/error) ↔ Java UPPERCASE 변환은 converter 담당. */
    @Convert(converter = VersionScanStatusConverter.class)
    @Column(name = "scan_status", nullable = false, length = 20)
    private VersionScanStatus scanStatus;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "comment", length = 500)
    private String comment;

    protected FileVersion() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public void setFileId(UUID fileId) {
        this.fileId = fileId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public UUID getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(UUID storageKey) {
        this.storageKey = storageKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public VersionScanStatus getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(VersionScanStatus scanStatus) {
        this.scanStatus = scanStatus;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
