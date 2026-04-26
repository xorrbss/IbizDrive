package com.ibizdrive.user;

/**
 * 시스템 ROLE — docs/03 §3.2.5 / docs/00 ADR #17 권한 매트릭스 단일 진실 출처.
 *
 * <p>DB 컬럼 {@code users.role}는 동일 문자열을 저장하며 CHECK 제약으로 보호된다
 * (Flyway V2 {@code users_role_check}).
 *
 * <p>권한 enum 9종(READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/PURGE)과
 * 별개로, 본 ROLE은 시스템 전역 가드(예: {@code PURGE}는 {@code ADMIN}만 보유)에 사용된다.
 */
public enum Role {
    MEMBER,
    AUDITOR,
    ADMIN
}
