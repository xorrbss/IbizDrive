export type UploadErrorKind =
  | 'network' | 'permission' | 'quota' | 'server' | 'conflict'

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
