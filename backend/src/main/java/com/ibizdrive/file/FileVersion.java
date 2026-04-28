package com.ibizdrive.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

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

    @Column(name = "storage_key", nullable = false)
    private UUID storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "scan_status", nullable = false, length = 20)
    private String scanStatus = "pending";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scan_result", columnDefinition = "jsonb")
    private String scanResult;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "comment", length = 500)
    private String comment;

    protected FileVersion() {
        // JPA
    }

    public FileVersion(UUID id,
                       UUID fileId,
                       int versionNumber,
                       UUID storageKey,
                       long sizeBytes,
                       String checksumSha256,
                       String mimeType,
                       UUID uploadedBy) {
        this.id = id;
        this.fileId = fileId;
        this.versionNumber = versionNumber;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.mimeType = mimeType;
        this.uploadedBy = uploadedBy;
    }
}
