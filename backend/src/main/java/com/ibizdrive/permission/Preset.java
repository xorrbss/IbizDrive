package com.ibizdrive.permission;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Preset × 권한 매트릭스 (docs/03 §3.2).
 *
 * <p>5 preset. 노드(폴더/파일)에 권한을 부여할 때 단일 권한 enum 9종을 직접 다루지 않고
 * 의미 단위(read/upload/edit/share/admin)로 묶는다.
 *
 * <p>wire format은 lower-case (docs/03 §3.2 표 표기).
 *
 * <p>{@code PURGE}는 어떤 preset에도 포함되지 않으며 시스템 ROLE {@code ADMIN}만 보유한다
 * (docs/03 line 334 — 노드 admin preset에도 부여하지 않음).
 *
 * <p>{@code DELETE (자기 것)} / {@code DELETE (전체)} 등 세부 조건은 service 레벨 — 본 매핑은
 * 권한 enum 보유 여부만 다룬다.
 */
public enum Preset {

    READ("read", EnumSet.of(Permission.READ, Permission.DOWNLOAD)),

    UPLOAD("upload", EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.DOWNLOAD)),

    EDIT("edit", EnumSet.of(
        Permission.READ,
        Permission.UPLOAD,
        Permission.EDIT,
        Permission.MOVE,
        Permission.DOWNLOAD,
        Permission.DELETE
    )),

    SHARE("share", EnumSet.of(
        Permission.READ,
        Permission.UPLOAD,
        Permission.EDIT,
        Permission.MOVE,
        Permission.DOWNLOAD,
        Permission.DELETE,
        Permission.SHARE
    )),

    ADMIN("admin", EnumSet.complementOf(EnumSet.of(Permission.PURGE)));

    private static final Map<String, Preset> BY_WIRE =
        Stream.of(values()).collect(java.util.stream.Collectors.toMap(Preset::wire, p -> p));

    private final String wire;
    private final Set<Permission> permissions;

    Preset(String wire, EnumSet<Permission> permissions) {
        this.wire = wire;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /** 본 preset이 부여하는 권한 집합 (불변). */
    public Set<Permission> permissions() {
        return permissions;
    }

    @JsonCreator
    public static Preset from(String wire) {
        Preset preset = BY_WIRE.get(wire);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown Preset wire format: " + wire);
        }
        return preset;
    }
}
