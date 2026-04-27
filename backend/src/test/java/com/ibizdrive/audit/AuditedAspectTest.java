package com.ibizdrive.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link AuditedAspect} 단위 테스트 — Spring 컨텍스트 없이 {@link AspectJProxyFactory}로
 * proxy를 만들어 검증. {@link AuditService}는 Mockito mock으로 대체.
 *
 * <p>핵심 검증 (ADR #24):
 * <ol>
 *   <li>{@code @Audited} 메서드 정상 종료 → {@code record(AuditEvent)} 호출 1회 + 어노테이션 값 반영
 *   <li>메서드 throw → {@code record} 호출 0회 (성공한 액션만 기록)
 *   <li>SpEL: {@code #fileId} (메서드 인자), {@code #result.id} (리턴 객체) 양쪽 평가
 * </ol>
 */
class AuditedAspectTest {

    private AuditService auditService;
    private TargetService proxy;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new TargetService());
        factory.addAspect(new AuditedAspect(auditService));
        proxy = factory.getProxy();
    }

    @Test
    void normalReturn_invokesRecord_withResolvedTargetId() {
        UUID fileId = UUID.randomUUID();

        proxy.deleteFile(fileId);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertEquals(AuditEventType.FILE_DELETED, ev.eventType());
        assertEquals(AuditTargetType.FILE, ev.targetType());
        assertEquals(fileId, ev.targetId(), "SpEL #fileId가 메서드 인자에서 평가되어야 함");
    }

    @Test
    void thrownException_doesNotRecord() {
        UUID fileId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> proxy.failingDelete(fileId));

        verify(auditService, never()).record(any());
    }

    @Test
    void spelOnResult_extractsTargetIdFromReturnedObject() {
        Created result = proxy.createFile("hello.txt");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertEquals(AuditEventType.FILE_UPLOADED, ev.eventType());
        assertEquals(result.id(), ev.targetId(), "SpEL #result.id가 리턴 객체에서 평가되어야 함");
    }

    /**
     * 테스트 전용 대상 — proxy로 감싸 advice 동작 검증.
     */
    static class TargetService {

        @Audited(event = AuditEventType.FILE_DELETED,
                 targetType = AuditTargetType.FILE,
                 target = "#fileId")
        public void deleteFile(UUID fileId) {
            // no-op
        }

        @Audited(event = AuditEventType.FILE_DELETED,
                 targetType = AuditTargetType.FILE,
                 target = "#fileId")
        public void failingDelete(UUID fileId) {
            throw new IllegalStateException("boom");
        }

        @Audited(event = AuditEventType.FILE_UPLOADED,
                 targetType = AuditTargetType.FILE,
                 target = "#result.id")
        public Created createFile(String name) {
            return new Created(UUID.randomUUID(), name);
        }
    }

    record Created(UUID id, String name) {}
}
