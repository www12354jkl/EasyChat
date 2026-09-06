import { hasElectronBridge } from '@/config/env'

export type ElectronContextMenuItem = {
  id: string
  label: string
  enabled?: boolean
  type?: 'normal' | 'separator'
}

/** 在 Electron 中弹出原生右键菜单，返回被点击项的 id */
export async function showElectronContextMenu(
  event: MouseEvent,
  items: ElectronContextMenuItem[],
): Promise<string | null> {
  if (!hasElectronBridge() || !items.length) return null
  event.preventDefault()
  event.stopPropagation()
  return window.electronAPI!.showContextMenu!(items, event.clientX, event.clientY)
}
