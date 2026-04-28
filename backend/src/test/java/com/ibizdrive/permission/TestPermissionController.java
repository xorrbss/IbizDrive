package com.ibizdrive.permission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 테스트 전용 컨트롤러 — {@code PermissionEvaluatorIntegrationTest}에서 SpEL
 * {@code hasPermission(...)} 가 실제 endpoint 차단/허용을 만드는지 검증한다.
 *
 * <p>실제 controller가 도입되기 전(folder/file 도메인은 A4) {@code @PreAuthorize} 패턴 자체의
 * GREEN 검증 목적. 운영 코드 아님 — {@code src/test/java}에만 존재.
 */
@RestController
public class TestPermissionController {

    @GetMapping("/api/test/folders/{id}")
    @PreAuthorize("hasPermission(#id, 'folder', 'READ')")
    public Map<String, String> readFolder(@PathVariable String id) {
        return Map.of("id", id, "ok", "true");
    }

    @GetMapping("/api/test/folders/{id}/edit")
    @PreAuthorize("hasPermission(#id, 'folder', 'EDIT')")
    public Map<String, String> editFolder(@PathVariable String id) {
        return Map.of("id", id, "ok", "true");
    }

    @GetMapping("/api/test/admin/purge/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> purge(@PathVariable String id) {
        return Map.of("id", id, "ok", "true");
    }
}
