package com.ibizdrive.common.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * process-liveness 스모크 endpoint — 의존성(DB·디스크) 검사 없음 (A1.2 보안 매처 검증용).
 * LB/모니터링의 실제 헬스 판정은 {@code /actuator/health}를 사용할 것 (ADR #50 — DB 다운 시 DOWN).
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
