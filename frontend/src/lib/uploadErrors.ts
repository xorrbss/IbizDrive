export type UploadErrorKind =
  | 'network' | 'permission' | 'quota' | 'server' | 'conflict' | 'too_large'

/**
 * backend `spring.servlet.multipart.max-file-size: 100MB`(application.yml)와 동기화.
 * enqueue 시점 사전 검증(stores/upload.ts)이 서버 왕복 없이 즉시 거부하는 기준.
 */
export const MAX_UPLOAD_SIZE_BYTES = 100 * 1024 * 1024

export function oversizeError(): { kind: UploadErrorKind; message: string } {
  return {
    kind: 'too_large',
    message: '파일이 최대 업로드 크기(100MB)를 초과합니다',
  }
}

export function classifyError(xhr: { status: number }): {
  kind: UploadErrorKind
  message: string
} {
  if (xhr.status === 0)   return { kind: 'network',    message: '네트워크 연결을 확인하세요' }
  if (xhr.status === 403) return { kind: 'permission', message: '업로드 권한이 없습니다' }
  if (xhr.status === 413) return { kind: 'quota',      message: '용량 한도를 초과했습니다' }
  if (xhr.status === 409) return { kind: 'conflict',   message: '이미 존재하는 이름입니다' }
  if (xhr.status >= 500)  return { kind: 'server',     message: '서버 오류가 발생했습니다' }
  return { kind: 'server', message: `오류 (${xhr.status})` }
}
