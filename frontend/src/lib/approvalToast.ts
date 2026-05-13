/**
 * dual-approval framework (ADR #47, docs/02 §2.11) — 202 APPROVAL_REQUIRED 응답을 받은 mutation
 * hook이 호출하는 토스트 helper. {@link ApprovalRequiredError}와 actionLabel("사용자 역할 변경" /
 * "휴지통 영구 삭제" / "휴지통 보존 정책 변경" 등)을 받아 sonner toast로 노출한다.
 *
 * <p>UX:
 * <ul>
 *   <li>토스트 종류: {@code toast.info} — 실패도 성공도 아닌 "보류" 상태</li>
 *   <li>메시지: "승인 요청이 등록되었습니다 ({actionLabel}). 두 번째 관리자의 승인을 기다립니다."</li>
 *   <li>action 버튼: "승인 페이지" — `/admin/approvals/:approvalId`로 이동</li>
 *   <li>duration: 8000ms — 일반 toast(5초)보다 길게, 사용자가 링크 클릭할 시간 확보</li>
 * </ul>
 *
 * <p>호출자 책임:
 * <ol>
 *   <li>mutation hook `onError`에서 {@code err instanceof ApprovalRequiredError} 분기</li>
 *   <li>본 helper 호출 후 cache 무효화 / 옵티미스틱 업데이트 모두 skip — mutation은 실제로
 *       실행되지 않았으므로 (서버는 PendingApproval row만 생성)</li>
 *   <li>form 값 / 선택 상태 유지 — 사용자가 승인 페이지 확인 후 다시 시도할 수 있도록</li>
 * </ol>
 */
import { toast } from 'sonner'
import type { ApprovalRequiredError } from './errors'

/**
 * 승인 요청 등록 토스트 노출. {@code window.location.assign} 또는 라우터를 직접 호출하지 않고,
 * action 버튼 onClick에서 `window.location.href`를 설정 — Next.js router는 hook에서만
 * 접근 가능하므로 SSR-safe하게 location API를 사용한다 (admin 페이지 한정 운영 도구).
 *
 * @param err mutation hook이 받은 ApprovalRequiredError 인스턴스
 * @param actionLabel 한국어 동작 라벨 (예: "사용자 역할 변경")
 */
export function showApprovalRequiredToast(
  err: ApprovalRequiredError,
  actionLabel: string,
): void {
  toast.info(
    `승인 요청이 등록되었습니다 (${actionLabel}). 두 번째 관리자의 승인을 기다립니다.`,
    {
      duration: 8000,
      action: {
        label: '승인 페이지',
        onClick: () => {
          if (typeof window !== 'undefined') {
            window.location.href = `/admin/approvals/${err.approvalId}`
          }
        },
      },
    },
  )
}
