import { contextBridge, ipcRenderer } from 'electron'

function syncWindowFromHash() {
  const hash = window.location.hash
  const isAuth =
    !hash ||
    hash === '#' ||
    hash === '#/login' ||
    hash.startsWith('#/login?')
  ipcRenderer.send('window:set-mode', isAuth ? 'auth' : 'main')
}

window.addEventListener('hashchange', syncWindowFromHash)
window.addEventListener('popstate', syncWindowFromHash)
window.addEventListener('load', syncWindowFromHash)

let lastHash = window.location.hash
setInterval(() => {
  const hash = window.location.hash
  if (hash === lastHash) return
  lastHash = hash
  syncWindowFromHash()
}, 300)

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  platform: process.platform,
  setWindowMode: (mode: 'auth' | 'main') => {
    ipcRenderer.send('window:set-mode', mode)
  },
  windowMinimize: () => ipcRenderer.invoke('window:minimize'),
  windowToggleMaximize: () => ipcRenderer.invoke('window:maximize'),
  windowClose: () => ipcRenderer.invoke('window:close'),
  onMaximizedChanged: (callback: (maximized: boolean) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, value: boolean) => callback(value)
    ipcRenderer.on('window:maximized-changed', listener)
    return () => {
      ipcRenderer.removeListener('window:maximized-changed', listener)
    }
  },
  showContextMenu: (
    items: Array<{ id: string; label: string; enabled?: boolean; type?: 'normal' | 'separator' }>,
    x: number,
    y: number,
  ) => ipcRenderer.invoke('context-menu:show', { items, x, y }) as Promise<string | null>,
})
