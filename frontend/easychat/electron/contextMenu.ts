import { BrowserWindow, Menu, ipcMain } from 'electron'
import type { ContextMenuParams, IpcMainInvokeEvent, MenuItemConstructorOptions } from 'electron'

export type ContextMenuItemPayload = {
  id: string
  label: string
  enabled?: boolean
  type?: 'normal' | 'separator'
}

function windowFromEvent(event: IpcMainInvokeEvent): BrowserWindow | null {
  const fromSender = BrowserWindow.fromWebContents(event.sender)
  if (fromSender && !fromSender.isDestroyed()) return fromSender
  return BrowserWindow.getFocusedWindow()
}

/** 输入框 / 选中文本：系统原生编辑菜单 */
export function bindDefaultContextMenu(win: BrowserWindow) {
  win.webContents.on('context-menu', (event, params) => {
    const template = buildEditMenuTemplate(params)
    if (!template.length) return

    event.preventDefault()
    Menu.buildFromTemplate(template).popup({ window: win })
  })
}

function buildEditMenuTemplate(params: ContextMenuParams): MenuItemConstructorOptions[] {
  if (!params.isEditable) return []

  const template: MenuItemConstructorOptions[] = []
  const hasSelection = Boolean(params.selectionText.trim())

  if (hasSelection) {
    template.push({ label: '复制', role: 'copy' })
    template.push({ label: '剪切', role: 'cut' })
  }
  template.push({ label: '粘贴', role: 'paste' })
  template.push({ type: 'separator' })
  template.push({ label: '全选', role: 'selectAll' })

  return template
}

export function registerContextMenuHandlers() {
  ipcMain.removeHandler('context-menu:show')

  ipcMain.handle(
    'context-menu:show',
    (event, payload: { items: ContextMenuItemPayload[]; x: number; y: number }) => {
      return new Promise<string | null>((resolve) => {
        const win = windowFromEvent(event)
        if (!win) {
          resolve(null)
          return
        }

        let picked: string | null = null
        const template: MenuItemConstructorOptions[] = payload.items.map((item) => {
          if (item.type === 'separator') {
            return { type: 'separator' as const }
          }
          return {
            label: item.label,
            enabled: item.enabled !== false,
            click: () => {
              picked = item.id
            },
          }
        })

        const menu = Menu.buildFromTemplate(template)
        menu.popup({
          window: win,
          x: Math.round(payload.x),
          y: Math.round(payload.y),
          callback: () => resolve(picked),
        })
      })
    },
  )
}
