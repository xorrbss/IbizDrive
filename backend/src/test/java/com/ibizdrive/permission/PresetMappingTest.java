package com.ibizdrive.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Preset} → {@link Permission} 매핑 단위 테스트 (docs/03 §3.2 표).
 *
 * <p>5 preset (read/upload/edit/share/admin)이 정확한 권한 집합을 보유한다.
 * "DELETE (자기 것)" 등 세부 조건은 service 레벨 — 본 매핑은 권한 enum 보유 여부만.
 *
 * <p>wire format은 lower-case (read/upload/edit/share/admin) — docs/03 §3.2 표 표기와 일치.
 */
class PresetMappingTest {

    @Test
    void preset_has_exactly_five_values() {
        assertEquals(5, Preset.values().length);
    }

    @Test
    void read_preset_grants_read_and_download_only() {
        assertEquals(EnumSet.of(Permission.READ, Permission.DOWNLOAD), Preset.READ.permissions());
    }

    @Test
    void upload_preset_grants_read_upload_download() {
        assertEquals(
            EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.DOWNLOAD),
            Preset.UPLOAD.permissions()
        );
    }

    @Test
    void edit_preset_grants_six_permissions_excluding_share_admin_purge() {
        assertEquals(
            EnumSet.of(
                Permission.READ,
                Permission.UPLOAD,
                Permission.EDIT,
                Permission.MOVE,
                Permission.DOWNLOAD,
                Permission.DELETE
            ),
            Preset.EDIT.permissions()
        );
    }

    @Test
    void share_preset_grants_seven_permissions_excluding_admin_purge() {
        assertEquals(
            EnumSet.of(
                Permission.READ,
                Permission.UPLOAD,
                Permission.EDIT,
                Permission.MOVE,
                Permission.DOWNLOAD,
                Permission.DELETE,
                Permission.SHARE
            ),
            Preset.SHARE.permissions()
        );
    }

    @Test
    void admin_preset_grants_eight_permissions_excluding_purge() {
        Set<Permission> expected = EnumSet.allOf(Permission.class);
        expected.remove(Permission.PURGE);
        assertEquals(expected, Preset.ADMIN.permissions());
        assertFalse(Preset.ADMIN.permissions().contains(Permission.PURGE),
            "PURGE must NOT be in admin preset — system ROLE ADMIN only (docs/03 line 334)");
    }

    @Test
    void no_preset_grants_purge() {
        for (Preset preset : Preset.values()) {
            assertFalse(preset.permissions().contains(Permission.PURGE),
                preset.name() + " must not grant PURGE");
        }
    }

    @Test
    void wire_format_is_lowercase() {
        assertEquals("read", Preset.READ.wire());
        assertEquals("upload", Preset.UPLOAD.wire());
        assertEquals("edit", Preset.EDIT.wire());
        assertEquals("share", Preset.SHARE.wire());
        assertEquals("admin", Preset.ADMIN.wire());
    }

    @Test
    void from_wire_round_trip() {
        for (Preset preset : Preset.values()) {
            assertEquals(preset, Preset.from(preset.wire()));
        }
    }

    @Test
    void from_unknown_wire_throws() {
        assertThrows(IllegalArgumentException.class, () -> Preset.from("system_admin"));
    }

    @Test
    void jackson_serializes_to_lowercase() throws Exception {
        ObjectMapper om = new ObjectMapper();
        assertEquals("\"read\"", om.writeValueAsString(Preset.READ));
        assertEquals(Preset.ADMIN, om.readValue("\"admin\"", Preset.class));
    }
}
