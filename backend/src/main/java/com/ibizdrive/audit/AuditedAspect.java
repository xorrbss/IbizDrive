package com.ibizdrive.audit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ibizdrive.user.IbizDriveUserDetails;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * {@link Audited} 어노테이션이 부착된 메서드의 정상 종료(@AfterReturning)를 가로채
 * {@link AuditService#record(AuditEvent)}를 자동 호출한다 (ADR #24).
 *
 * <p>설계 결정:
 * <ul>
 *   <li>{@code @AfterReturning} — 메서드가 예외로 종료되면 advice가 실행되지 않아 audit row를
 *       남기지 않는다. "성공한 액션만 기록"이라는 정책과 일치.
 *   <li>SpEL 컨텍스트는 {@link MethodBasedEvaluationContext}로 메서드 인자 이름과
 *       {@code #result} 변수를 모두 노출한다.
 *   <li>actor / IP / UA는 호출 시점의 보안 컨텍스트 + 웹 요청 컨텍스트에서 추출. 비-HTTP
 *       호출이면 null (스케줄러 등 시스템 호출 케이스를 자연스럽게 처리).
 *   <li>SpEL 평가 또는 record 호출 자체가 실패해도 비즈니스 메서드는 이미 정상 종료된 상태이므로
 *       throw 시 호출자에게 영향 — 그러나 audit insert 실패는 ERROR 로그만 남기고 swallow
 *       (감사 누락 < 비즈니스 결과 손실, context.md §함정 2). REQUIRES_NEW이므로 비즈니스
 *       트랜잭션과 격리.
 * </ul>
 */
@Aspect
@Component
public class AuditedAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditedAspect.class);
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAM_NAMES = new DefaultParameterNameDiscoverer();

    private final AuditService auditService;

    public AuditedAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Audited audited, Object result) {
        try {
            UUID targetId = resolveTargetId(joinPoint, audited, result);
            UUID actorId = currentActorId();
            AuditEvent event = new AuditEvent(
                audited.event(),
                actorId,
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                audited.targetType(),
                targetId,
                null,
                null,
                null
            );
            auditService.record(event);
        } catch (RuntimeException ex) {
            // 비즈니스 메서드는 이미 성공 — audit 실패가 호출자에 전파되면 안 됨.
            log.error("audit emission failed for event={} method={}",
                audited.event(), joinPoint.getSignature().toShortString(), ex);
        }
    }

    private static UUID resolveTargetId(JoinPoint jp, Audited audited, Object result) {
        String spel = audited.target();
        if (spel == null || spel.isBlank()) return null;

        MethodSignature sig = (MethodSignature) jp.getSignature();
        Method method = sig.getMethod();
        MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(
            jp.getTarget(), method, jp.getArgs(), PARAM_NAMES);
        ctx.setVariable("result", result);

        Expression expr = PARSER.parseExpression(spel);
        Object value = expr.getValue(ctx);
        if (value == null) return null;
        if (value instanceof UUID u) return u;
        return UUID.fromString(value.toString());
    }

    private static UUID currentActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof IbizDriveUserDetails ud) {
            return ud.getUser().getId();
        }
        return null;
    }
}
