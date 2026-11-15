import { BrowserWindow, ipcMain } from 'electron'
import type { IpcMainInvokeEvent } from 'electron'

function windowFromEvent(event: IpcMainInvokeEvent): BrowserWindow | null {
  const fromSender = BrowserWindow.fromWebContents(event.sender)
  if (fromSender && !fromSender.isDestroyed()) return fromSender
  const focused = BrowserWindow.getFocusedWindow()
  if (focused && !focused.isDestroyed()) return focused
  const first = BrowserWindow.getAllWindows().find((w) => !w.isDestroyed())
  return first ?? null
}

export function registerWindowControls() {
  ipcMain.removeHandler('window:minimize')
  ipcMain.removeHandler('window:maximize')
  ipcMain.removeHandler('window:close')

  ipcMain.handle('window:minimize', (event) => {
    windowFromEvent(event)?.minimize()
  })

  ipcMain.handle('window:maximize', (event) => {
    const win = windowFromEvent(event)
    if (!win) return
    if (win.isMaximized()) win.unmaximize()
    else win.maximize()
  })

  ipcMain.handle('window:close', (event) => {
    windowFromEvent(event)?.close()
  })
}

export function bindWindowControlEvents(win: BrowserWindow) {
  const send = (maximized: boolean) => {
    if (!win.isDestroyed()) {
      win.webContents.send('window:maximized-changed', maximized)
    }
  }
  win.on('maximize', () => send(true))
  win.on('unmaximize', () => send(false))
}
