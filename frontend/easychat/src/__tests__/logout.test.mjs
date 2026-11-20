import assert from 'node:assert/strict'
import fsSync from 'node:fs'
import fs from 'node:fs/promises'
import path from 'node:path'
import { after, before, beforeEach, describe, it } from 'node:test'
import { fileURLToPath } from 'node:url'
import { parse, compileScript } from 'vue/compiler-sfc'
import { createServer } from 'vite'

function createStorageMock() {
  const map = new Map()
  return {
    getItem(k) {
      return map.has(k) ? map.get(k) : null
    },
    setItem(k, v) {
      map.set(k, String(v))
    },
    removeItem(k) {
      map.delete(k)
    },
    clear() {
      map.clear()
    },
  }
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('frontend logout & token-blacklisting flow', () => {
  let viteServer
  let createSSRApp
  let h
  let renderToString
  let createPinia
  let setActivePinia
  let createMemoryHistory
  let createRouter
  let useAuthStore
  let useToastStore
  let requestMod
  let authApi
  let HomeView
  let assignCalls
  let originalFetch
  let originalLocalStorage
  let originalWindow
  let originalSetTimeout

  before(async () => {
    originalFetch = globalThis.fetch
    originalLocalStorage = globalThis.localStorage
    originalWindow = globalThis.window
    originalSetTimeout = globalThis.setTimeout

    // 让 toast 自动消失定时器不阻塞 Node 测试进程退出
    globalThis.setTimeout = (fn, ms, ...args) => {
      const timer = originalSetTimeout(fn, ms, ...args)
      timer?.unref?.()
      return timer
    }

    assignCalls = []
    globalThis.localStorage = createStorageMock()
    globalThis.window = {
      location: {
        pathname: '/chat',
        protocol: 'http:',
        host: 'localhost:5173',
        assign(url) {
          assignCalls.push(url)
        },
      },
    }

    // 规范化 Windows 盘符大小写（如 d: vs D:），避免 Node ESM 与 Vite SSR 加载两份 vue/pinia 实例
    const rootDir = fsSync.realpathSync.native(fileURLToPath(new URL('../..', import.meta.url)))
    viteServer = await createServer({
      root: rootDir,
      server: { middlewareMode: true },
      appType: 'custom',
      logLevel: 'silent',
      ssr: { external: ['vue', 'vue/server-renderer', 'pinia', 'vue-router'] },
      plugins: [
        {
          name: 'test-homeview-setup-bindings',
          resolveId(id) {
            if (id === '/@homeview-test.ts' || id === '/@test-deps.ts') return id
          },
          async load(id) {
            if (id === '/@test-deps.ts') {
              return {
                code: [
                  "export { createSSRApp, h } from 'vue'",
                  "export { renderToString } from 'vue/server-renderer'",
                  "export { createPinia, setActivePinia } from 'pinia'",
                  "export { createMemoryHistory, createRouter } from 'vue-router'",
                ].join('\n'),
                map: null,
              }
            }
            if (id === '/@homeview-test.ts') {
              const homePath = path.join(rootDir, 'src/views/HomeView.vue')
              const src = await fs.readFile(homePath, 'utf8')
              const { descriptor } = parse(src, { filename: homePath })
              const compiled = compileScript(descriptor, { id: 'home-view-test', inlineTemplate: false })
              return {
                code: `${compiled.content.replace('export default', 'const _sfc =')}\nexport default { ..._sfc, render: () => null }`,
                map: null,
              }
            }
          },
        },
      ],
    })

    // 统一经由 Vite SSR 模块图加载运行时依赖，确保与被测模块共享同一份 Pinia / Vue 实例
    ;({ createSSRApp, h, renderToString, createPinia, setActivePinia, createMemoryHistory, createRouter } =
      await viteServer.ssrLoadModule('/@test-deps.ts'))
    ;({ useAuthStore } = await viteServer.ssrLoadModule('/src/stores/auth.ts'))
    ;({ useToastStore } = await viteServer.ssrLoadModule('/src/stores/toast.ts'))
    requestMod = await viteServer.ssrLoadModule('/src/api/request.ts')
    authApi = await viteServer.ssrLoadModule('/src/api/auth.ts')
    HomeView = (await viteServer.ssrLoadModule('/@homeview-test.ts')).default
  })

  after(async () => {
    await viteServer?.close()
    globalThis.fetch = originalFetch
    globalThis.localStorage = originalLocalStorage
    globalThis.window = originalWindow
    globalThis.setTimeout = originalSetTimeout
  })

  beforeEach(() => {
    globalThis.localStorage.clear()
    assignCalls.length = 0
  })

  async function setupHomeHarness() {
    const pinia = createPinia()
    setActivePinia(pinia)

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/chat', name: 'chat', component: { template: '<div />' } },
        { path: '/contacts', name: 'contacts', component: { template: '<div />' } },
        { path: '/profile', name: 'profile', component: { template: '<div />' } },
        { path: '/login', name: 'login', component: { template: '<div />' } },
      ],
    })
    await router.push('/')
    await router.isReady()

    let setupBindings
    const WrappedHome = {
      ...HomeView,
      setup(props, ctx) {
        setupBindings = HomeView.setup(props, ctx)
        return setupBindings
      },
    }

    const app = createSSRApp({ render: () => h(WrappedHome) })
    app.use(pinia)
    app.use(router)
    await renderToString(app)

    const auth = useAuthStore()
    const toast = useToastStore()
    auth.setSession({
      accessToken: 'at-initial',
      refreshToken: 'rt-initial',
      userInfo: { userId: 'u1', email: 'u1@example.com', nickname: 'Alice' },
    })

    return {
      auth,
      toast,
      router,
      handleLogout: () => setupBindings.handleLogout(),
    }
  }

  it('sends current refreshToken on normal logout, clears session, and shows success toast', async () => {
    const calls = []
    globalThis.fetch = async (url, init) => {
      calls.push({ url, headers: init.headers, body: init.body })
      return jsonResponse({ code: 200, message: 'success', data: null })
    }

    const { auth, toast, router, handleLogout } = await setupHomeHarness()
    await handleLogout()

    assert.equal(calls.length, 1)
    assert.equal(calls[0].url, '/api/auth/logout')
    assert.equal(calls[0].headers.Authorization, 'Bearer at-initial')
    assert.deepEqual(JSON.parse(calls[0].body), { refreshToken: 'rt-initial' })

    assert.equal(auth.isLoggedIn, false)
    assert.equal(auth.accessToken, null)
    assert.equal(auth.refreshToken, null)
    assert.equal(router.currentRoute.value.path, '/login')
    assert.deepEqual(
      toast.toasts.map((t) => ({ type: t.type, message: t.message })),
      [{ type: 'success', message: '已退出登录' }],
    )
    assert.equal(assignCalls.length, 0)
  })

  it('replays /api/auth/logout with the newly rotated refreshToken after 401 auto-refresh', async () => {
    const calls = []
    globalThis.fetch = async (url, init) => {
      const parsedBody = init?.body ? JSON.parse(init.body) : undefined
      calls.push({ url, authHeader: init?.headers?.Authorization, body: parsedBody })

      if (url === '/api/auth/logout' && init?.headers?.Authorization === 'Bearer at-initial') {
        return jsonResponse({ code: 401, message: 'token无效或已过期' }, 200)
      }
      if (url === '/api/auth/token/refresh') {
        assert.equal(parsedBody, 'rt-initial')
        return jsonResponse({
          code: 200,
          data: { accessToken: 'at-rotated', refreshToken: 'rt-rotated' },
        })
      }
      if (url === '/api/auth/logout' && init?.headers?.Authorization === 'Bearer at-rotated') {
        return jsonResponse({ code: 200, message: 'success', data: null })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }

    const { auth, toast, router, handleLogout } = await setupHomeHarness()
    await handleLogout()

    assert.equal(calls.length, 3)
    assert.deepEqual(calls[0], {
      url: '/api/auth/logout',
      authHeader: 'Bearer at-initial',
      body: { refreshToken: 'rt-initial' },
    })
    assert.deepEqual(calls[1], {
      url: '/api/auth/token/refresh',
      authHeader: undefined,
      body: 'rt-initial',
    })
    assert.deepEqual(calls[2], {
      url: '/api/auth/logout',
      authHeader: 'Bearer at-rotated',
      body: { refreshToken: 'rt-rotated' },
    })

    assert.equal(auth.isLoggedIn, false)
    assert.equal(auth.refreshToken, null)
    assert.equal(router.currentRoute.value.path, '/login')
    assert.deepEqual(
      toast.toasts.map((t) => ({ type: t.type, message: t.message })),
      [{ type: 'success', message: '已退出登录' }],
    )
  })

  it('waits for an in-flight refresh before sending logout so rotated refreshToken is sent', async () => {
    let releaseRefresh
    const refreshGate = new Promise((resolve) => {
      releaseRefresh = resolve
    })
    const calls = []

    globalThis.fetch = async (url, init) => {
      const parsedBody = init?.body ? JSON.parse(init.body) : undefined
      calls.push({ url, authHeader: init?.headers?.Authorization, body: parsedBody })

      if (url === '/api/user/settings') {
        if (init?.headers?.Authorization === 'Bearer at-initial') {
          return jsonResponse({ code: 401, message: 'expired' }, 200)
        }
        return jsonResponse({ code: 200, data: { friendVerify: 1 } })
      }
      if (url === '/api/auth/token/refresh') {
        await refreshGate
        return jsonResponse({
          code: 200,
          data: { accessToken: 'at-fresh', refreshToken: 'rt-fresh' },
        })
      }
      if (url === '/api/auth/logout') {
        return jsonResponse({ code: 200, data: null })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }

    const { auth } = await setupHomeHarness()

    const bgRequest = requestMod.request('/api/user/settings')
    // 等待并发请求进入 /api/auth/token/refresh 挂起状态
    await new Promise((r) => originalSetTimeout(r, 15))
    const logoutPromise = authApi.logout()
    releaseRefresh()

    await Promise.all([bgRequest, logoutPromise])

    const logoutCalls = calls.filter((c) => c.url === '/api/auth/logout')
    assert.equal(logoutCalls.length, 1)
    assert.equal(logoutCalls[0].authHeader, 'Bearer at-fresh')
    assert.deepEqual(logoutCalls[0].body, { refreshToken: 'rt-fresh' })
    assert.equal(auth.refreshToken, 'rt-fresh')
  })

  it('ignores duplicate concurrent handleLogout invocations while logout is in flight', async () => {
    let releaseLogout
    const gate = new Promise((resolve) => {
      releaseLogout = resolve
    })
    let logoutCount = 0
    globalThis.fetch = async (url) => {
      if (url === '/api/auth/logout') {
        logoutCount += 1
        await gate
        return jsonResponse({ code: 200, data: null })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }

    const { handleLogout } = await setupHomeHarness()
    const first = handleLogout()
    const second = handleLogout()
    releaseLogout()
    await Promise.all([first, second])

    assert.equal(logoutCount, 1)
  })

  for (const scenario of [
    {
      name: 'HTTP 500 server error',
      responder: () => jsonResponse({ code: 500, message: 'Redis 写入失败' }, 500),
      expectedReason: 'Redis 写入失败',
    },
    {
      name: 'HTTP 503 service unavailable',
      responder: () => jsonResponse({ code: 503, message: '认证服务暂不可用' }, 503),
      expectedReason: '认证服务暂不可用',
    },
    {
      name: 'HTTP 429 rate limited',
      responder: () => jsonResponse({ code: 429, message: '请求过于频繁' }, 429),
      expectedReason: '请求过于频繁',
    },
    {
      name: 'network failure',
      responder: () => {
        throw new TypeError('Failed to fetch')
      },
      expectedReason: '网络连接失败，请检查网络',
    },
  ]) {
    it(`clears local session and shows error toast (never success) on ${scenario.name}`, async () => {
      globalThis.fetch = async () => scenario.responder()

      const { auth, toast, router, handleLogout } = await setupHomeHarness()
      await handleLogout()

      assert.equal(auth.isLoggedIn, false)
      assert.equal(auth.accessToken, null)
      assert.equal(auth.refreshToken, null)
      assert.equal(router.currentRoute.value.path, '/login')
      assert.equal(assignCalls.length, 0)
      assert.equal(toast.toasts.some((t) => t.type === 'success'), false)
      assert.equal(toast.toasts.length, 1)
      assert.equal(toast.toasts[0].type, 'error')
      assert.ok(toast.toasts[0].message.includes(scenario.expectedReason))
    })
  }

  it('does not trigger hard redirect when 401 refresh fails during logout and surfaces error toast', async () => {
    globalThis.fetch = async (url) => {
      if (url === '/api/auth/logout') {
        return jsonResponse({ code: 401, message: 'expired' }, 200)
      }
      if (url === '/api/auth/token/refresh') {
        return jsonResponse({ code: 503, message: '认证服务暂不可用' }, 503)
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }

    const { auth, toast, router, handleLogout } = await setupHomeHarness()
    await handleLogout()

    assert.equal(assignCalls.length, 0)
    assert.equal(auth.isLoggedIn, false)
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(toast.toasts.some((t) => t.type === 'success'), false)
    assert.equal(toast.toasts[0].type, 'error')
    assert.ok(toast.toasts[0].message.includes('认证服务暂不可用'))
  })

  it('surfaces network error toast instead of forced login redirect if replayed logout fails over network', async () => {
    let logoutAttempt = 0
    globalThis.fetch = async (url) => {
      if (url === '/api/auth/logout') {
        logoutAttempt += 1
        if (logoutAttempt === 1) {
          return jsonResponse({ code: 401, message: 'expired' }, 200)
        }
        throw new TypeError('Network lost during replay')
      }
      if (url === '/api/auth/token/refresh') {
        return jsonResponse({
          code: 200,
          data: { accessToken: 'at-2', refreshToken: 'rt-2' },
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }

    const { auth, toast, router, handleLogout } = await setupHomeHarness()
    await handleLogout()

    assert.equal(assignCalls.length, 0)
    assert.equal(auth.isLoggedIn, false)
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(toast.toasts.some((t) => t.type === 'success'), false)
    assert.equal(toast.toasts[0].type, 'error')
    assert.ok(toast.toasts[0].message.includes('网络连接失败，请检查网络'))
  })

  it('does not resurrect cleared session if clearSession runs mid-refresh', async () => {
    let releaseRefresh
    const gate = new Promise((resolve) => {
      releaseRefresh = resolve
    })
    globalThis.fetch = async (url) => {
      if (url === '/api/auth/token/refresh') {
        await gate
        return jsonResponse({
          code: 200,
          data: { accessToken: 'at-resurrected', refreshToken: 'rt-resurrected' },
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }

    const { auth } = await setupHomeHarness()
    const refreshPromise = requestMod.refreshSession()
    await new Promise((r) => originalSetTimeout(r, 10))
    auth.clearSession()
    releaseRefresh()

    await assert.rejects(refreshPromise, { message: '登录状态已失效' })
    assert.equal(auth.isLoggedIn, false)
    assert.equal(auth.accessToken, null)
    assert.equal(auth.refreshToken, null)
    assert.equal(globalThis.localStorage.getItem('easychat_refresh_token'), null)
  })

  it('still calls forceLogout when refresh fails for non-logout authenticated requests', async () => {
    globalThis.fetch = async (url) => {
      if (url === '/api/user/settings') {
        return jsonResponse({ code: 401, message: 'expired' }, 200)
      }
      if (url === '/api/auth/token/refresh') {
        return jsonResponse({ code: 1007, message: '登录已过期' }, 401)
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }

    const { auth } = await setupHomeHarness()
    await assert.rejects(requestMod.request('/api/user/settings'), { message: '登录已过期' })
    assert.equal(auth.isLoggedIn, false)
    assert.deepEqual(assignCalls, ['/login'])
  })

  it('preserves static JSON body serialization for ordinary requests', async () => {
    let capturedInit
    globalThis.fetch = async (_url, init) => {
      capturedInit = init
      return jsonResponse({ code: 200, data: { ok: true } })
    }

    await setupHomeHarness()
    const res = await requestMod.request('/api/auth/login', {
      method: 'POST',
      body: { email: 'a@b.com', password: 'secret' },
      auth: false,
    })

    assert.deepEqual(res, { ok: true })
    assert.equal(capturedInit.headers['Content-Type'], 'application/json')
    assert.equal(capturedInit.headers.Authorization, undefined)
    assert.deepEqual(JSON.parse(capturedInit.body), { email: 'a@b.com', password: 'secret' })
  })
})
