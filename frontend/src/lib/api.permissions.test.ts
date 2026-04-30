import { describe, it, expect } from 'vitest'
import { api } from './api'
import { qk } from './queryKeys'

describe('api.getEffectivePermissions (M8)', () => {
  it('admin preset 8 권한 반환 (PURGE 제외)', async () => {
    const perms = await api.getEffectivePermissions()
    expect(new Set(perms)).toEqual(
      new Set([
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
    expect(perms).not.toContain('PURGE')
  })

  it('nodeId 인자도 동일 응답 (mock 한정)', async () => {
    const a = await api.getEffectivePermissions('folder_sales')
    const b = await api.getEffectivePermissions()
    expect(a).toEqual(b)
  })
})

describe('qk.permissions (M8)', () => {
  it('nodeId 있으면 node-scoped key', () => {
    expect(qk.permissions('folder_sales')).toEqual([
      'explorer',
      'permissions',
      'node',
      'folder_sales',
    ])
  })

  it('nodeId 없으면 effectivePermissions와 동일', () => {
    expect(qk.permissions()).toEqual(qk.effectivePermissions())
  })
})
