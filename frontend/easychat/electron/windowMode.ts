import { BrowserWindow, ipcMain, screen } from 'electron'
import type { IpcMainEvent, WebContents } from 'electron'

export type ElectronWindowMode = 'auth' | 'main'

const AUTH_SIZE = { width: 400, height: 720 }
const MAIN_SIZE = { width: 1280, height: 840, minWidth: 960, minHeight: 640 }

function screenMax() {
  const { width, height } = screen.getPrimaryDisplay().workAreaSize
  return { width, height }
}

export function modeFromUrl(url: string): ElectronWindowMode {
  try {
    const hash = new URL(url).hash
    if (!hash || hash === '#') return 'auth'
    if (hash === '#/login' || hash.startsWith('#/login?')) return 'auth'
    return 'main'
  } catch {
    return 'auth'
  }
}

export function applyWindowMode(win: BrowserWindow, mode: ElectronWindowMode): void {
  if (win.isDestroyed()) return

  if (mode === 'auth') {
    win.setResizable(true)
    win.setMinimumSize(AUTH_SIZE.width, AUTH_SIZE.height)
    win.setMaximumSize(AUTH_SIZE.width, AUTH_SIZE.height)
    win.setSize(AUTH_SIZE.width, AUTH_SIZE.height, true)
    win.setResizable(false)
    win.center()
    return
  }

  const max = screenMax()
  win.setResizable(true)
  win.setMaximumSize(max.width, max.height)
  win.setMinimumSize(MAIN_SIZE.minWidth, MAIN_SIZE.minHeight)
  win.setSize(MAIN_SIZE.width, MAIN_SIZE.height, true)
  win.center()
}

function syncModeFromUrl(win: BrowserWindow, url: string) {
  applyWindowMode(win, modeFromUrl(url))
}

/** 主进程监听 hash 路由变化，不依赖渲染进程 IPC */
export function bindWindowModeByNavigation(win: BrowserWindow) {
  const sync = (contents: WebContents) => {
    if (contents !== win.webContents || win.isDestroyed()) return
    syncModeFromUrl(win, contents.getURL())
  }

  win.webContents.on('did-finish-load', () => sync(win.webContents))
  win.webContents.on('did-navigate-in-page', (_event, url) => {
    if (!win.isDestroyed()) syncModeFromUrl(win, url)
  })
  win.webContents.on('did-navigate', (_event, url) => {
    if (!win.isDestroyed()) syncModeFromUrl(win, url)
  })
}

function windowFromEvent(event: IpcMainEvent): BrowserWindow | null {
  const fromSender = BrowserWindow.fromWebContents(event.sender)
  if (fromSender && !fromSender.isDestroyed()) return fromSender
  const focused = BrowserWindow.getFocusedWindow()
  if (focused && !focused.isDestroyed()) return focused
  const first = BrowserWindow.getAllWindows().find((w) => !w.isDestroyed())
  return first ?? null
}

export function registerWindowModeHandlers() {
  ipcMain.removeAllListeners('window:set-mode')
  ipcMain.on('window:set-mode', (event, mode: ElectronWindowMode) => {
    const win = windowFromEvent(event)
    if (!win || (mode !== 'auth' && mode !== 'main')) return
    applyWindowMode(win, mode)
  })
}
