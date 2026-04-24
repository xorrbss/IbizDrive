// src/components/files/FileTableEmpty.tsx
import { UploadButton } from '@/components/upload/UploadButton'

export function FileTableEmpty() {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 py-[60px] px-5 text-center">
      <div className="w-20 h-20 rounded-full bg-surface-2 flex items-center justify-center">
        <svg
          className="w-10 h-10 text-fg-subtle"
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
      </div>
      <p className="text-[15px] font-semibold text-fg">이 폴더는 비어 있습니다</p>
      <p className="text-[12.5px] text-fg-muted max-w-[320px]">
        파일을 이 영역에 끌어다 놓거나, 업로드 버튼을 눌러 추가하세요
      </p>
      <div className="mt-2">
        <UploadButton variant="primary" label="파일 업로드" />
      </div>
    </div>
  )
}
