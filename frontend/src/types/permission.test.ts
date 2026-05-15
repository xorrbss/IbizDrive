import { describe, expect, it } from 'vitest'
import { PERMISSIONS, PRESETS, PRESET_PERMISSIONS, type Permission, type Preset } from './permission'

/**
 * Permission/Preset mirror 회귀 테스트 (docs/03 §3.1~§3.2).
 *
 * 백엔드 `com.ibizdrive.permission.Permission` / `Preset`과 1:1 동기.
 * 백엔드 `PermissionEnumTest` / `PresetMappingTest`와 동일한 케이스를 프론트 측에서도 보증.
 */

describe('Permission enum mirror', () => {
  it('has exactly 10 values', () => {
    expect(PERMISSIONS).toHaveLength(10)
  })

  it('contains all 10 expected values in UPPER_SNAKE_CASE', () => {
    expect(new Set(PERMISSIONS)).toEqual(
      new Set<Permission>([
        'READ',
        'UPLOAD',
        'EDIT',
        'MOVE',
        'DOWNLOAD',
        'DELETE',
        'SHARE',
        'PERMISSION_ADMIN',
        'PURGE',
        'APPROVE_ADMIN_ACTION',
      ]),
    )
  })

  it('values match SpEL string literals (UPPER_SNAKE_CASE)', () => {
    PERMISSIONS.forEach((p) => {
      expect(p).toBe(p.toUpperCase())
      expect(p).not.toContain(' ')
    })
  })
})

describe('Preset enum mirror', () => {
  it('has exactly 5 values', () => {
    expect(PRESETS).toHaveLength(5)
  })

  it('contains all 5 expected lowercase values', () => {
    expect(new Set(PRESETS)).toEqual(new Set<Preset>(['read', 'upload', 'edit', 'share', 'admin']))
  })
})

describe('Preset → Permission mapping (docs/03 §3.2)', () => {
  it('read preset = { READ, DOWNLOAD }', () => {
    expect(PRESET_PERMISSIONS.read).toEqual(new Set<Permission>(['READ', 'DOWNLOAD']))
  })

  it('upload preset = { READ, UPLOAD, DOWNLOAD }', () => {
    expect(PRESET_PERMISSIONS.upload).toEqual(new Set<Permission>(['READ', 'UPLOAD', 'DOWNLOAD']))
  })

  it('edit preset = 6 permissions excluding SHARE/PERMISSION_ADMIN/PURGE', () => {
    expect(PRESET_PERMISSIONS.edit).toEqual(
      new Set<Permission>(['READ', 'UPLOAD', 'EDIT', 'MOVE', 'DOWNLOAD', 'DELETE']),
    )
  })

  it('share preset = 7 permissions excluding PERMISSION_ADMIN/PURGE', () => {
    expect(PRESET_PERMISSIONS.share).toEqual(
      new Set<Permission>(['READ', 'UPLOAD', 'EDIT', 'MOVE', 'DOWNLOAD', 'DELETE', 'SHARE']),
    )
  })

  it('admin preset = 8 permissions excluding PURGE', () => {
    expect(PRESET_PERMISSIONS.admin.size).toBe(8)
    expect(PRESET_PERMISSIONS.admin.has('PURGE')).toBe(false)
    expect(PRESET_PERMISSIONS.admin).toEqual(
      new Set<Permission>([
        'READ',
        'UPLOAD',
        'EDIT',
        'MOVE',
        'DOWNLOAD',
        'DELETE',
        'SHARE',
        'PERMISSION_ADMIN',
      ]),
    )
  })

  it('no preset grants PURGE (system ROLE ADMIN only — docs/03 line 334)', () => {
    PRESETS.forEach((p) => {
      expect(PRESET_PERMISSIONS[p].has('PURGE')).toBe(false)
    })
  })
})
