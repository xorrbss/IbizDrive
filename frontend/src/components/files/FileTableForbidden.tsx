// src/components/files/FileTableForbidden.tsx
export function FileTableForbidden() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-gray-500">
      <p className="text-lg font-medium">접근 권한이 없습니다</p>
      <p className="mt-1 text-sm">이 폴더의 열람 권한이 필요합니다. 관리자에게 문의하세요.</p>
    </div>
  )
}
