package com.ibizdrive.audit;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 현재 HTTP 요청 컨텍스트에서 actor IP / User-Agent를 추출한다 (audit 메타데이터 보강).
 *
 * <p>{@link RequestContextHolder}는 Spring MVC가 요청 진입 시 세팅하는 ThreadLocal.
 * 비-HTTP 컨텍스트(스케줄러, @EventListener async 등)에서 호출되면 모든 메서드는 null 반환.
 *
 * <p><b>X-Forwarded-For 처리</b>: 향후 reverse proxy 뒤에서 lb IP 대신 실제 client IP를
 * 받으려면 Spring {@code ForwardedHeaderFilter}를 활성화 — context.md §함정 4. 본 클래스는
 * {@link HttpServletRequest#getRemoteAddr()}만 사용하므로 필터 활성 시 자동으로 X-Forwarded-For가
 * remoteAddr에 반영된다.
 */
public final class WebRequestContextHolder {

    private WebRequestContextHolder() {
        // static utility
    }

    public static InetAddress currentIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        return parseInet(req.getRemoteAddr());
    }

    public static String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        return req.getHeader("User-Agent");
    }

    /** 테스트 외 직접 호출하지 말 것 — package-private 의도. */
    static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private static InetAddress parseInet(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return InetAddress.getByName(raw);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
