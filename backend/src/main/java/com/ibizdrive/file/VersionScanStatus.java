package com.ibizdrive.file;

/**
 * 파일 버전 바이러스/콘텐츠 스캔 상태 (docs/02 §2.5, V5 {@code file_versions.scan_status}).
 *
 * <p>DB CHECK 제약은 lowercase {@code ('pending', 'clean', 'infected', 'error')}을 enforce한다.
 * Java enum 식별자 컨벤션(UPPERCASE)과 mismatch가 있으므로 {@link VersionScanStatusConverter}가
 * entity 경로의 변환을 담당한다 — {@code @Enumerated(EnumType.STRING)}은 사용 불가.
 *
 * <p>A5.1 시점에는 entity persist 시 기본값 {@link #PENDING}으로 시작하며, 실제 상태 전이는
 * 스캐너 워커(후속 트랙)가 담당한다 (ADR #29 (a) — schema-only 의 점진적 entity 승격).
 */
public enum VersionScanStatus {

    PENDING("pending"),
    CLEAN("clean"),
    INFECTED("infected"),
    ERROR("error");

    private final String dbValue;

    VersionScanStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static VersionScanStatus fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        for (VersionScanStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown scan_status DB value: " + value);
    }
}
