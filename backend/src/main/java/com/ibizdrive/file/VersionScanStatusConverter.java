package com.ibizdrive.file;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link VersionScanStatus} ↔ V5 {@code file_versions.scan_status VARCHAR(20)} 변환기.
 *
 * <p>DB CHECK는 lowercase {@code ('pending', 'clean', 'infected', 'error')}을 요구하지만 Java
 * enum 컨벤션은 UPPERCASE. {@code @Enumerated(EnumType.STRING)} 대신 본 converter를 entity 필드에
 * 명시적으로 {@code @Convert} 부착해 사용한다 ({@code autoApply=false} — 다른 도메인 enum과의
 * 우발적 적용 차단).
 */
@Converter(autoApply = false)
public class VersionScanStatusConverter implements AttributeConverter<VersionScanStatus, String> {

    @Override
    public String convertToDatabaseColumn(VersionScanStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public VersionScanStatus convertToEntityAttribute(String dbData) {
        return VersionScanStatus.fromDbValue(dbData);
    }
}
