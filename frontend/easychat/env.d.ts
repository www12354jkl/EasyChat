/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ELECTRON?: string
  readonly VITE_API_BASE?: string
  readonly VITE_WS_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

interface Window {
  electronAPI?: {
    isElectron: boolean
    platform: NodeJS.Platform
    setWindowMode?: (mode: 'auth' | 'main') => void
    windowMinimize?: () => Promise<void>
    windowToggleMaximize?: () => Promise<void>
    windowClose?: () => Promise<void>
    onMaximizedChanged?: (callback: (maximized: boolean) => void) => () => void
    showContextMenu?: (
      items: Array<{ id: string; label: string; enabled?: boolean; type?: 'normal' | 'separator' }>,
      x: number,
      y: number,
    ) => Promise<string | null>
  }
}
