package com.ibizdrive.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * CSRF 실패를 디버그 가능한 403 + JSON body로 매핑한다 (docs/02 §7.4 / docs/03 §1.3 계약).
 *
 * <p>본 핸들러가 없을 때의 흐름:
 * <ol>
 *   <li>{@code CsrfFilter}가 {@code InvalidCsrfTokenException} (또는 {@code MissingCsrfTokenException})
 *       을 자체 {@code accessDeniedHandler}로 처리한다 ({@code chain.doFilter} 호출 전 차단).</li>
 *   <li>기본 {@link AccessDeniedHandlerImpl}이 {@code response.sendError(403)}을 호출한다.</li>
 *   <li>Tomcat의 {@code sendError}는 {@code /error}로 forward를 트리거한다 (Spring Boot
 *       {@code BasicErrorController}). forward는 {@code SecurityFilterChain}을 다시 거치고,
 *       SESSION/Authentication이 없는 forwarded 요청은 {@code anyRequest().authenticated()}에 막혀
 *       {@code AccessDeniedException}이 발생한다.</li>
 *   <li>{@code ExceptionTranslationFilter}가 익명 사용자에 대해 이를 entry point로 위임 →
 *       {@code HttpStatusEntryPoint(UNAUTHORIZED)}가 응답을 401, body 없이 commit.</li>
 * </ol>
 * 결과: 클라이언트가 받은 401 빈 body는 CSRF 실패의 진짜 원인을 가린다 (디버그 불가).
 *
 * <p>본 핸들러는 1단계의 기본 {@link AccessDeniedHandlerImpl}을 대체하여 {@code sendError} 대신
 * {@code setStatus + write}로 직접 응답을 작성한다. {@code sendError}가 호출되지 않으므로 Tomcat의
 * {@code /error} forward가 트리거되지 않고, 클라이언트는 깨끗한 {@code 403 + {"code":"CSRF_MISMATCH"}}
 * 를 받는다.
 *
 * <p>CSRF 외의 {@link AccessDeniedException} (예: 인증된 사용자의 권한 부족)은 그대로 기본
 * 핸들러로 위임 — {@code GlobalExceptionHandler}가 {@code @ExceptionHandler(AccessDeniedException)}
 * 으로 controller 단에서 받아 {@code PERMISSION_DENIED} envelope을 발급한다 (docs/02 §7.2).
 *
 * <p>Wire: {@code SecurityConfig.exceptionHandling().accessDeniedHandler(this)} —
 * {@code CsrfConfigurer}가 {@code ExceptionHandlingConfigurer}의 핸들러를 {@code CsrfFilter}에 주입한다.
 *
 * <p>{@link SecurityConfig}의 {@code @Bean} 메서드로 등록되어 SecurityConfig를 {@code @Import}하는
 * 모든 슬라이스 테스트에 자동 노출된다 ({@code @Component} 사용 시 21개 테스트 파일의 {@code @Import}를
 * 일일이 갱신해야 하는 부담 회피).
 */
public class CsrfAwareAccessDeniedHandler implements AccessDeniedHandler {

    private static final String CSRF_MISMATCH_BODY = "{\"code\":\"CSRF_MISMATCH\"}";

    private final AccessDeniedHandler delegate = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (accessDeniedException instanceof CsrfException) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(CSRF_MISMATCH_BODY);
            return;
        }
        delegate.handle(request, response, accessDeniedException);
    }
}
