export type ElectronWindowMode = 'auth' | 'main'

export function syncElectronWindowMode(mode: ElectronWindowMode) {
  window.electronAPI?.setWindowMode?.(mode)
}
