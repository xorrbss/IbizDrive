package com.ibizdrive.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ibizdrive.storage.*} 속성 바인딩 — A15 (docs/02 §5, ADR #36 가칭).
 *
 * <p>backend storage abstraction 진입점. MVP는 {@code type=local}만 지원 ({@link LocalFsStorageClient}
 * 가 활성화). {@code type=s3}는 v1.x로 별도 ADR 발효 후 활성.
 *
 * <p>기존 application.yml의 {@code ibizdrive.storage.s3.*} 블록은 v1.x 진입 시 활성화 예정 — MVP는 무시.
 *
 * @param type  storage 백엔드 — {@code "local"} | {@code "s3"} (MVP는 "local"만 동작).
 * @param local local FS 백엔드 설정.
 */
@ConfigurationProperties(prefix = "ibizdrive.storage")
public record StorageProperties(String type, Local local) {

    public StorageProperties {
        if (type == null || type.isBlank()) type = "local";
        if (local == null) local = new Local(null);
    }

    /**
     * Local FS 백엔드 설정.
     *
     * @param root 객체 저장 root 디렉터리 경로. 상대 경로면 작업 디렉터리 기준. 부재 시 자동 생성.
     */
    public record Local(String root) {
        public Local {
            if (root == null || root.isBlank()) root = "./uploads";
        }
    }
}
