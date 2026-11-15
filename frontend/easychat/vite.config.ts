import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'
import electron from 'vite-plugin-electron/simple'

// https://vite.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const isElectron = mode === 'electron' || env.VITE_ELECTRON === 'true'
  const isDevServer = command === 'serve'

  return {
    base: isElectron ? './' : '/',
    plugins: [
      vue(),
      tailwindcss(),
      !isElectron && vueDevTools(),
      isElectron &&
        electron({
          main: {
            entry: 'electron/main.ts',
          },
          preload: {
            input: 'electron/preload.ts',
            vite: {
              build: {
                rollupOptions: {
                  output: {
                    format: 'cjs',
                    entryFileNames: 'preload.cjs',
                    inlineDynamicImports: true,
                  },
                },
              },
            },
          },
        }),
    ].filter(Boolean),
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: isDevServer
        ? {
            '/api': {
              target: 'http://localhost:8080',
              changeOrigin: true,
            },
            '/ws': {
              target: 'http://localhost:8080',
              changeOrigin: true,
              ws: true,
            },
          }
        : undefined,
    },
  }
})
