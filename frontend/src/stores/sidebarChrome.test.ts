import { describe, it, expect, beforeEach } from 'vitest'
import { useSidebarChromeStore } from './sidebarChrome'

describe('sidebarChrome store', () => {
  beforeEach(() => {
    useSidebarChromeStore.setState({ collapsed: false })
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('sidebar-chrome:v1')
    }
  })

  it('초기값 collapsed=false', () => {
    expect(useSidebarChromeStore.getState().collapsed).toBe(false)
  })

  it('toggle()이 boolean을 뒤집는다', () => {
    useSidebarChromeStore.getState().toggle()
    expect(useSidebarChromeStore.getState().collapsed).toBe(true)
    useSidebarChromeStore.getState().toggle()
    expect(useSidebarChromeStore.getState().collapsed).toBe(false)
  })

  it('setCollapsed(true)가 값을 강제 설정한다', () => {
    useSidebarChromeStore.getState().setCollapsed(true)
    expect(useSidebarChromeStore.getState().collapsed).toBe(true)
    useSidebarChromeStore.getState().setCollapsed(true)
    expect(useSidebarChromeStore.getState().collapsed).toBe(true) // 멱등
    useSidebarChromeStore.getState().setCollapsed(false)
    expect(useSidebarChromeStore.getState().collapsed).toBe(false)
  })
})
