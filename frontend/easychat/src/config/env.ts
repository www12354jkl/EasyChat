/** Electron 桌面端构建时为 true（见 .env.electron） */
export const IS_ELECTRON = import.meta.env.VITE_ELECTRON === 'true'

/** 运行时 preload 是否已注入（仅在真实 Electron 窗口内为 true） */
export function hasElectronBridge(): boolean {
  return typeof window !== 'undefined' && window.electronAPI?.isElectron === true
}

/** API 网关根地址；Web 开发留空走 Vite 代理，Electron 直连 8080 */
export function getApiBase(): string {
  const base = import.meta.env.VITE_API_BASE as string | undefined
  return base ? base.replace(/\/$/, '') : ''
}

/** 拼出完整 API URL */
export function resolveApiUrl(path: string): string {
  const normalized = path.startsWith('/') ? path : `/${path}`
  const base = getApiBase()
  return base ? `${base}${normalized}` : normalized
}

/** WebSocket 连接地址。ticket 是 30 秒一次性票据，不是登录令牌 */
export function resolveWsUrl(ticket: string): string {
  const wsBase = import.meta.env.VITE_WS_BASE as string | undefined
  if (wsBase) {
    const url = new URL(wsBase)
    if (!url.pathname.endsWith('/ws')) {
      url.pathname = `${url.pathname.replace(/\/$/, '')}/ws`
    }
    url.search = `ticket=${encodeURIComponent(ticket)}`
    return url.toString()
  }
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}/ws?ticket=${encodeURIComponent(ticket)}`
}
