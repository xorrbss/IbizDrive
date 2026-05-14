'use client'
import { toast } from 'sonner'
import { Breadcrumb } from './Breadcrumb'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useToggleStar } from '@/hooks/useToggleStar'
import { messageForError } from '@/lib/errors'

/**
 * P2a — workspace 진입점(ClientFilesPage)에서 사용. {@link Breadcrumb}에
 * 현재 폴더 즐겨찾기 상태/토글 함수를 주입한 wrapper.
 *
 * <p>책임 분리: 워크스페이스 종류(d/t/shared)에 무관한 공통 토글 로직만 담당.
 * Breadcrumb 자체는 zip 디자인 충실 — onToggleStar 미전달 시 별 버튼 비표시 정책 유지.
 *
 * <p>folderId가 없는 상태(workspace landing 등) 또는 root('root' 가상) 시에도 Breadcrumb 컴포넌트가
 * 자체 가드(`current && onToggleStar`)로 처리 — 본 wrapper는 항상 동일 props 주입.
 * root folder 토글은 backend가 권한/정책에 따라 거절 가능 — 실패 시 useToggleStar onError가 rollback.
 */
export function BreadcrumbWithStar() {
  const { folderId, folder, starred } = useCurrentFolder()
  const toggle = useToggleStar()

  // folder 미로드 또는 folderId 부재 시 별 버튼 비주입 — Breadcrumb skeleton/loading 상태와 정합.
  if (!folder || !folderId) {
    return <Breadcrumb />
  }

  const parentId = folder.parentId ?? 'root'

  return (
    <Breadcrumb
      isStarred={starred}
      onToggleStar={() =>
        toggle.mutate(
          {
            resourceType: 'folder',
            id: folderId,
            parentId,
            currentStarred: starred,
          },
          {
            onError: (err) =>
              toast.error(messageForError(err, '즐겨찾기 변경에 실패했습니다.')),
          },
        )
      }
    />
  )
}
