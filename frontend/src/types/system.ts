/**
 * Wave 1 — T3 — `/admin/system` 페이지가 사용하는 cron 설정 스냅샷 타입.
 *
 * <p>backend `CronJobStatusResponse` mirror — 옵셔널 필드(`batchSize`/`maxPerRun`/`graceHours`)는
 * NON_NULL 직렬화되므로 미존재로도 응답 가능.
 */
export type CronJobKey =
  | 'purge.expired'
  | 'share.expire'
  | 'permission.expire'
  | 'storage.orphan.cleanup'
  | 'admin.approval.expire'
  | 'favorites.cleanup'

export interface CronJobStatus {
  /** application.yml 키와 동형. */
  key: CronJobKey
  /** 한국어 사람이 읽는 명칭 — 카드에 그대로 노출. */
  label: string
  /** 현재 profile에서의 활성화 여부. */
  enabled: boolean
  /** Spring cron 표현식 (6필드). */
  cron: string
  /** cron 평가 시간대 (java.time.ZoneId). */
  zone: string
  /** share/permission 단일 run 처리 상한. */
  batchSize?: number
  /** purge/storage-orphan 단일 run 처리 상한. */
  maxPerRun?: number
  /** storage-orphan 전용 — mtime grace. */
  graceHours?: number
}

export interface CronJobsResponse {
  jobs: CronJobStatus[]
}
