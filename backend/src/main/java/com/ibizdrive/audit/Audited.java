package com.ibizdrive.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드를 감사 로그 emission 대상으로 표시한다 (ADR #24).
 *
 * <p>적용 시 {@link AuditedAspect}가 메서드 정상 종료(@AfterReturning) 시점에
 * {@link AuditService#record(AuditEvent)}를 자동 호출한다. 메서드가 예외로 종료되면
 * audit row를 남기지 않는다 — 성공한 액션만 기록한다는 정책.
 *
 * <p>호출자는 비즈니스 로직 자체를 변경할 필요 없이 단순히 본 어노테이션을 부착하면 된다.
 * 침투성 0이 핵심 가치 (A1 인증 코드처럼 침투를 피해야 하는 다수 영역에 동일 패턴 적용 가능).
 *
 * <p>SpEL 평가 컨텍스트:
 * <ul>
 *   <li>메서드 인자: {@code #argName} 또는 {@code #a0}, {@code #p0} 등 위치 기반 변수
 *   <li>리턴 값: {@code #result} (정상 종료 시점이라 항상 사용 가능)
 *   <li>{@link org.springframework.context.expression.MethodBasedEvaluationContext} 사용
 * </ul>
 *
 * <p>예시:
 * <pre>{@code
 * @Audited(event = AuditEventType.FILE_DELETED,
 *          targetType = AuditTargetType.FILE,
 *          target = "#fileId")
 * public void delete(UUID fileId) { ... }
 *
 * @Audited(event = AuditEventType.FILE_UPLOADED,
 *          targetType = AuditTargetType.FILE,
 *          target = "#result.id")
 * public FileEntity upload(MultipartFile mf) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 발행할 감사 이벤트 타입. */
    AuditEventType event();

    /** 대상 리소스 타입. {@code AuditEvent.targetType}에 매핑된다. */
    AuditTargetType targetType();

    /**
     * 대상 리소스 ID를 추출하는 SpEL 표현식. {@code UUID}로 평가되는 값을 기대.
     * 빈 문자열이면 targetId를 null로 둔다 (시스템 이벤트 등).
     */
    String target() default "";
}
