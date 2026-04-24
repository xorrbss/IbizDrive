// src/components/files/FileTableError.tsx
type Props = {
  onRetry: () => void
}

export function FileTableError({ onRetry }: Props) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-gray-500">
      <p className="text-lg font-medium text-red-600">파일 목록을 불러올 수 없습니다</p>
      <p className="mt-1 text-sm">네트워크를 확인하고 다시 시도해주세요</p>
      <button
        onClick={onRetry}
        className="mt-4 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
      >
        다시 시도
      </button>
    </div>
  )
}
