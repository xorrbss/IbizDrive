/**
 * Frontend error code constants (docs/02 §8).
 * 값은 backend GlobalExceptionHandler에서 wire되는 문자열과 정확히 일치.
 *
 * 본 파일은 Plan C에서 신설 — share endpoint subject_type='team' + §4.2 cap.
 * 다른 트랙이 분기 처리가 필요해질 때 해당 PR에서 상수를 추가한다 (KISS).
 */

// 공유 (team-centric-pivot Plan C — §4.2 cap)
export const SHARE_EXCEEDS_MEMBER = 'SHARE_EXCEEDS_MEMBER'

export type ErrorCode = typeof SHARE_EXCEEDS_MEMBER
