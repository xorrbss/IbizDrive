// M5 MOCK — 실제 백엔드 도입 시 이 파일을 참조하는 api.uploadFile 내부만 교체.
// 매직 파일명은 개발자 전용 (프로덕션 배포 전 실제 XHR로 전환).

type ProgressEventLike = { loaded: number; total: number; lengthComputable: boolean }

export class FakeXHR {
  upload: { onprogress: ((e: ProgressEventLike) => void) | null } = { onprogress: null }
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  status = 0
  responseText = ''

  private intervalId: ReturnType<typeof setInterval> | null = null
  private aborted = false
  private filename = ''
  private totalBytes = 0
  private loaded = 0

  open(_method: string, _url: string): void {
    // no-op (실제 XHR로 교체 시 URL/method 보관)
    void _method
    void _url
  }

  send(form: FormData): void {
    const file = form.get('file')
    if (!(file instanceof File)) {
      this.status = 400
      this.onload?.()
      return
    }
    this.filename = file.name
    this.totalBytes = file.size || 1
    this.loaded = 0

    this.intervalId = setInterval(() => this.tick(), 50)
  }

  abort(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
    this.aborted = true
    this.onerror?.()
  }

  private finish(status: number, responseText = ''): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
    this.status = status
    this.responseText = responseText
    this.onload?.()
  }

  private fail(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
    this.onerror?.()
  }

  private tick(): void {
    if (this.aborted) return
    this.loaded = Math.min(this.totalBytes, this.loaded + Math.ceil(this.totalBytes * 0.04))
    this.upload.onprogress?.({
      loaded: this.loaded,
      total: this.totalBytes,
      lengthComputable: true,
    })

    const pct = this.loaded / this.totalBytes

    if (pct >= 0.4) {
      switch (this.filename) {
        case 'net_fail.any':
          this.fail()
          return
        case 'conflict.pdf':
          this.finish(
            409,
            JSON.stringify({
              existing: { fileId: 'f_conflict', fileName: 'conflict.pdf' },
            }),
          )
          return
        case 'huge.bin':
          this.finish(413)
          return
        case 'deny.txt':
          this.finish(403)
          return
        case 'srv_500.any':
          this.finish(500)
          return
      }
    }

    if (this.loaded >= this.totalBytes) {
      this.finish(200)
    }
  }
}
