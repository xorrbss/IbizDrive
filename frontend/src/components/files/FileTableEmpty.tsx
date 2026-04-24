// src/components/files/FileTableEmpty.tsx
export function FileTableEmpty() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-gray-500">
      <svg
        className="w-16 h-16 mb-4 text-gray-300"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.5}
          d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"
        />
      </svg>
      <p className="text-lg font-medium">이 폴더는 비어 있습니다</p>
      <p className="mt-1 text-sm">파일을 드래그하거나 업로드 버튼을 눌러 추가하세요</p>
    </div>
  )
}
