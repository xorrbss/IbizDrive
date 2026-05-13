package com.ibizdrive.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Permission} enum 단위 테스트 (docs/03 §3.1).
 *
 * <p>9 resource-level 값 (READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/PURGE) +
 * 1 system-level 값 (APPROVE_ADMIN_ACTION, ADR #47 dual-approval Phase 2 추가). JSON wire format은
 * enum 이름과 동일한 UPPER_SNAKE_CASE — SpEL 표현식 {@code hasPermission(#id, 'folder', 'READ')}이
 * 동일한 문자열을 사용하므로 wire() == name() 동치를 유지한다.
 */
class PermissionEnumTest {

    @Test
    void enum_has_exactly_ten_values() {
        Set<Permission> all = Set.of(Permission.values());
        assertEquals(10, all.size());
        assertTrue(all.containsAll(Set.of(
            Permission.READ,
            Permission.UPLOAD,
            Permission.EDIT,
            Permission.MOVE,
            Permission.DOWNLOAD,
            Permission.DELETE,
            Permission.SHARE,
            Permission.PERMISSION_ADMIN,
            Permission.PURGE,
            Permission.APPROVE_ADMIN_ACTION
        )));
    }

    @Test
    void wire_format_equals_name() {
        for (Permission p : Permission.values()) {
            assertEquals(p.name(), p.wire(), "wire() must equal name() for SpEL string compat");
        }
    }

    @Test
    void from_wire_round_trip() {
        for (Permission p : Permission.values()) {
            assertEquals(p, Permission.from(p.wire()));
        }
    }

    @Test
    void from_unknown_wire_throws() {
        assertThrows(IllegalArgumentException.class, () -> Permission.from("UNKNOWN"));
    }

    @Test
    void jackson_serializes_to_wire_string() throws Exception {
        ObjectMapper om = new ObjectMapper();
        assertEquals("\"READ\"", om.writeValueAsString(Permission.READ));
        assertEquals(Permission.PURGE, om.readValue("\"PURGE\"", Permission.class));
    }
}
