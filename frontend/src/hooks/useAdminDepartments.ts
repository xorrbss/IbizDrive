'use client'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations, qk } from '@/lib/queryKeys'
import type {
  AdminDepartmentCreateBody,
  AdminDepartmentPage,
  AdminDepartmentPatchBody,
  AdminDepartmentSummary,
} from '@/types/department'

/**
 * Admin 부서 hooks (admin-department-crud, Wave 2 T4).
 *
 * <p>list 1개 + mutation 3종(create/update/deactivate)을 단일 파일에 모은다 — useAdminUsers
 * /useAdminInviteUser/useAdminUpdateUser와 다른 패턴이지만 본 트랙은 부서 lifecycle 4개 동작이
 * 의미적으로 한 묶음(create+rename+(de)activate)이고, page UX도 좁아서 분리 비용이 더 크다 (KISS).
 *
 * <p>모든 mutation은 성공 시 {@link invalidations.afterAdminDepartmentChanged}로 list prefix
 * 무효화 — 옵티미스틱 업데이트는 도입하지 않음 (rename/reactivate 충돌은 backend service에서만
 * 차단 가능, 원칙 #3: 비파괴 외에는 pending 정책).
 */

/**
 * page/size/q를 쿼리 키에 포함 — 입력 변경 시 자동 재요청.
 *
 * <p>q는 호출 측에서 trim+lowercase 후 전달. staleTime=0 (admin은 리얼타임 정확성 우선,
 * useAdminUsers와 동일 정책).
 */
export function useAdminDepartments(
  page = 0,
  size = 50,
  q = '',
) {
  const normalizedQ = q.trim().toLowerCase()
  return useQuery<AdminDepartmentPage>({
    queryKey: qk.adminDepartmentsList(page, size, normalizedQ),
    queryFn: () => api.adminListDepartments(page, size, normalizedQ || undefined),
    retry: false,
    staleTime: 0,
  })
}

/**
 * 부서 신규 생성. 409 DEPARTMENT_CONFLICT는 호출자(form)가 인라인 메시지로 분기.
 */
export function useAdminCreateDepartment() {
  const qc = useQueryClient()
  return useMutation<AdminDepartmentSummary, Error, AdminDepartmentCreateBody>({
    mutationFn: (body) => api.adminCreateDepartment(body),
    onSuccess: () => {
      void invalidations.afterAdminDepartmentChanged(qc)
    },
  })
}

/**
 * 부서 부분 수정 mutation — rename + (de)activate를 모두 흡수.
 *
 * <p>호출자가 body 형태로 의도를 결정한다:
 * <ul>
 *   <li>rename → {@code {name}}</li>
 *   <li>deactivate → {@code {isActive: false}}</li>
 *   <li>reactivate → {@code {isActive: true}}</li>
 * </ul>
 *
 * <p>의미별 wrapper({@link useAdminDeactivateDepartment})는 UX 의도 명시용 — 본 훅을 직접 호출해도
 * 동일 결과.
 */
export function useAdminUpdateDepartment() {
  const qc = useQueryClient()
  return useMutation<
    AdminDepartmentSummary,
    Error,
    { id: string; body: AdminDepartmentPatchBody }
  >({
    mutationFn: ({ id, body }) => api.adminUpdateDepartment(id, body),
    onSuccess: () => {
      void invalidations.afterAdminDepartmentChanged(qc)
    },
  })
}

/**
 * 비활성화 의미 wrapper — {@code body: { isActive: false }}만 보낸다.
 *
 * <p>{@link useAdminUpdateDepartment}와 동일 invalidation. 호출부가 의도("비활성화 버튼")와
 * 시그니처({@code mutate(id)}) 모두 명시되도록 분리한다 — admin-user-mgmt 패턴 답습.
 */
export function useAdminDeactivateDepartment() {
  const qc = useQueryClient()
  return useMutation<AdminDepartmentSummary, Error, { id: string }>({
    mutationFn: ({ id }) => api.adminUpdateDepartment(id, { isActive: false }),
    onSuccess: () => {
      void invalidations.afterAdminDepartmentChanged(qc)
    },
  })
}
