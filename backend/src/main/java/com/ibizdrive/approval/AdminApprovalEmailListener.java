package com.ibizdrive.approval;

import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * dual-approval framework email notification listener — ADR #47 Phase 4, docs/04 §16.4.4.
 *
 * <p>{@link AdminApprovalDecidedEvent}를 AFTER_COMMIT phase에서 수신해 4 transition별 메일 발송.
 * audit listener({@link com.ibizdrive.audit.AdminApprovalAuditListener})와 동형 패턴 —
 * 동일 이벤트를 두 listener가 독립 구독.
 *
 * <p>4 transition 매트릭스:
 * <ul>
 *   <li>{@code REQUESTED}: requesting admin을 제외한 모든 활성 ADMIN에게 `[승인 요청]` 메일</li>
 *   <li>{@code APPROVED}: requested_by에 `[승인됨]` 메일 (secondary + decisionReason 포함)</li>
 *   <li>{@code REJECTED}: requested_by에 `[거부됨]` 메일 (secondary + decisionReason 포함)</li>
 *   <li>{@code EXPIRED}: requested_by에 `[만료]` 메일 (재요청 안내)</li>
 *   <li>{@code CANCELLED}: emit 없음 (audit listener와 동형 — KISS, requested_by 본인 액션)</li>
 * </ul>
 *
 * <p><b>fire-and-forget</b>: {@link EmailService#send}는 {@code @Async("emailExecutor")}라 호출 시점에
 * 즉시 반환된다. 발송 실패는 impl 내부 ERROR 로그로 흡수되며 listener에 예외 도달 없음 (ADR #45).
 * 그래도 본 listener의 try/catch는 EmailService 호출 자체가 던질 가능성(빈 주소 등 정합 위반)에
 * 대비한 방어. 한 수신자 발송 실패가 다음 수신자 발송을 차단하지 않는다.
 *
 * <p><b>AFTER_COMMIT 보장</b>: outer transaction이 rollback되면 본 listener는 호출되지 않는다 —
 * 실패한 mutation에 대한 알림 발송 방지.
 *
 * <p><b>게이트 OFF 시 동작</b>: {@code app.admin-approval.email.enabled=false}면 listener는 메서드
 * 진입 직후 early-return — UserRepository / EmailService 호출 0 (테스트로 가드).
 */
@Component
public class AdminApprovalEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AdminApprovalEmailListener.class);

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final AdminApprovalEmailProperties properties;

    public AdminApprovalEmailListener(EmailService emailService,
                                      UserRepository userRepository,
                                      AdminApprovalEmailProperties properties) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalDecided(AdminApprovalDecidedEvent event) {
        if (!properties.enabled()) return;
        switch (event.status()) {
            case REQUESTED -> notifySecondaries(event);
            case APPROVED, REJECTED, EXPIRED -> notifyRequester(event);
            case CANCELLED -> { /* no-op — audit listener와 동형 */ }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // REQUESTED — multi-admin 발송
    // ──────────────────────────────────────────────────────────────────

    private void notifySecondaries(AdminApprovalDecidedEvent event) {
        // event.actorId() == requested_by (REQUESTED transition은 primary 본인이 actor).
        // primary 제외 활성 ADMIN 후보 collect.
        List<User> admins = userRepository.findActiveAdmins();
        String subject = subjectFor(event.status(), event.actionType());
        String body = bodyForRequested(event);
        for (User admin : admins) {
            if (admin.getId().equals(event.actorId())) continue;
            sendSafely(admin.getEmail(), subject, body, event);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // APPROVED / REJECTED / EXPIRED — requested_by 단일 발송
    // ──────────────────────────────────────────────────────────────────

    private void notifyRequester(AdminApprovalDecidedEvent event) {
        // APPROVED/REJECTED/EXPIRED 시 primaryApproverId가 requested_by와 동일하다 (이벤트 javadoc 참조).
        if (event.primaryApproverId() == null) {
            log.debug("admin approval result emit skipped — primaryApproverId null (status={}, approvalId={})",
                event.status(), event.approvalId());
            return;
        }
        Optional<User> requester = userRepository.findById(event.primaryApproverId());
        if (requester.isEmpty()) {
            log.debug("admin approval result emit skipped — requester not found (id={}, approvalId={})",
                event.primaryApproverId(), event.approvalId());
            return;
        }
        // soft-deleted user는 발송 대상 아님 — 정책 위반 회피.
        if (requester.get().getDeletedAt() != null) {
            log.debug("admin approval result emit skipped — requester soft-deleted (id={}, approvalId={})",
                event.primaryApproverId(), event.approvalId());
            return;
        }
        String subject = subjectFor(event.status(), event.actionType());
        String body = bodyForResult(event);
        sendSafely(requester.get().getEmail(), subject, body, event);
    }

    // ──────────────────────────────────────────────────────────────────
    // 발송 wrapper + 한국어 템플릿
    // ──────────────────────────────────────────────────────────────────

    private void sendSafely(String to, String subject, String body, AdminApprovalDecidedEvent event) {
        try {
            emailService.send(to, subject, body);
        } catch (RuntimeException ex) {
            // 한 수신자 실패가 다음 발송을 막지 않음. EmailService 자체는 fire-and-forget이지만
            // 동기 검증 단계(빈 주소 등)에서 throw 가능 — 방어.
            log.error("admin approval email send failed (to={}, status={}, approvalId={})",
                to, event.status(), event.approvalId(), ex);
        }
    }

    private String subjectFor(PendingApprovalStatus status, String actionType) {
        String prefix = switch (status) {
            case REQUESTED -> "[승인 요청]";
            case APPROVED -> "[승인됨]";
            case REJECTED -> "[거부됨]";
            case EXPIRED -> "[만료]";
            case CANCELLED -> "[취소]"; // 본 listener는 CANCELLED 분기 안 함, 안전망.
        };
        return prefix + " " + actionLabel(actionType);
    }

    /** action_type wire → 한국어 라벨 (KISS — 인라인 매핑, 별도 enum 도입 미루기). */
    private static String actionLabel(String actionType) {
        if (actionType == null) return "관리자 작업";
        return switch (actionType) {
            case "role_change" -> "사용자 역할 변경";
            case "retention_change" -> "휴지통 보존 정책 변경";
            case "trash_purge" -> "휴지통 영구 삭제";
            default -> actionType;
        };
    }

    private String bodyForRequested(AdminApprovalDecidedEvent event) {
        return String.join("\n",
            "새 2인 승인 요청이 접수되었습니다.",
            "",
            "- 작업: " + actionLabel(event.actionType()),
            "- 요청 ID: " + event.approvalId(),
            "- 요청자 ID: " + event.actorId(),
            "",
            "승인 또는 거부는 아래 페이지에서 진행해 주세요:",
            properties.baseUrl() + "/admin/approvals/" + event.approvalId(),
            "",
            "* 본 메일은 시스템 자동 발송입니다. 회신은 처리되지 않습니다."
        );
    }

    private String bodyForResult(AdminApprovalDecidedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("요청하신 2인 승인의 처리 결과를 안내드립니다.\n\n");
        sb.append("- 작업: ").append(actionLabel(event.actionType())).append('\n');
        sb.append("- 요청 ID: ").append(event.approvalId()).append('\n');
        sb.append("- 상태: ").append(statusLabel(event.status())).append('\n');
        if (event.secondaryApproverId() != null) {
            sb.append("- 결정 관리자 ID: ").append(event.secondaryApproverId()).append('\n');
        }
        if (event.decisionReason() != null && !event.decisionReason().isBlank()) {
            sb.append("- 결정 사유: ").append(event.decisionReason()).append('\n');
        }
        if (event.status() == PendingApprovalStatus.EXPIRED) {
            sb.append('\n').append("만료된 요청은 자동 처리되지 않습니다. 필요 시 동일 작업을 다시 요청해 주세요.").append('\n');
        }
        sb.append('\n').append("상세 보기: ").append(properties.baseUrl()).append("/admin/approvals/").append(event.approvalId()).append('\n');
        sb.append('\n').append("* 본 메일은 시스템 자동 발송입니다. 회신은 처리되지 않습니다.").append('\n');
        return sb.toString();
    }

    private static String statusLabel(PendingApprovalStatus status) {
        return switch (status) {
            case REQUESTED -> "요청됨";
            case APPROVED -> "승인됨";
            case REJECTED -> "거부됨";
            case EXPIRED -> "만료";
            case CANCELLED -> "취소";
        };
    }
}
