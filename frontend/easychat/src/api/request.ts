import { useAuthStore } from '@/stores/auth'
import { resolveApiUrl } from '@/config/env'

export class ApiError extends Error {
  code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.code = code
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** 请求体；传函数时每次发送（含 401 刷新后重放）都会重新求值 */
  body?: unknown
  /** 是否携带 Authorization header，默认 true */
  auth?: boolean
  /** 401 自动刷新失败时是否自动清理会话并跳转登录页，默认 true */
  forceLogoutOnRefreshFail?: boolean
}

interface ApiResponseBody<T> {
  code?: number
  message?: string
  data?: T
}

async function fetchJson<T>(
  url: string,
  init: RequestInit,
): Promise<{ res: Response; result: ApiResponseBody<T> | null }> {
  let res: Response
  try {
    res = await fetch(url, init)
  } catch {
    throw new ApiError('网络连接失败，请检查网络')
  }
  const result = (await res.json().catch(() => null)) as ApiResponseBody<T> | null
  return { res, result }
}

// ============ 401 自动刷新（单飞） ============

/** 刷新 token 的共享 Promise：并发 401 只发一次刷新请求 */
let refreshing: Promise<string> | null = null

/** 调后端 refresh 接口换新 accessToken（不走 request 封装，避免循环触发 401） */
async function doRefresh(): Promise<string> {
  const auth = useAuthStore()
  const startingRefreshToken = auth.refreshToken
  if (!startingRefreshToken) {
    throw new ApiError('未登录', 401)
  }
  // 后端 AuthController 是 @RequestBody String（JSON 字符串），需带引号序列化
  const { res, result } = await fetchJson<{ accessToken?: string; refreshToken?: string }>(
    resolveApiUrl('/api/auth/token/refresh'),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(startingRefreshToken),
    },
  )
  if (!res.ok || !result || result.code !== 200 || !result.data?.accessToken || !result.data.refreshToken) {
    throw new ApiError(
      result?.message ?? (res.ok ? '登录已过期' : `请求失败（${res.status}）`),
      result?.code ?? (res.ok ? 401 : res.status),
    )
  }
  // 防止刷新请求在途期间已登出或切换账号，把旧会话写回存储
  if (auth.refreshToken !== startingRefreshToken) {
    throw new ApiError('登录状态已失效', 401)
  }
  auth.setAccessToken(result.data.accessToken)
  auth.setRefreshToken(result.data.refreshToken)
  return result.data.accessToken
}

/** 单飞：并发请求共享同一次刷新，失败后允许下一次重试 */
export function refreshSession(): Promise<string> {
  return refreshOnce()
}

function refreshOnce(): Promise<string> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

/** 刷新失败：清会话并回登录页 */
function forceLogout() {
  const auth = useAuthStore()
  auth.clearSession()
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

/**
 * 统一请求封装：JSON 序列化 + Result 解包 + 错误消息透传。
 * 后端约定：HTTP 状态码 + body { code, message, data }，code=200 为成功。
 * code=401 时自动用 refreshToken 换新 accessToken 并重放原请求；刷新失败则登出。
 */
export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const auth = useAuthStore()
  const useAuth = options.auth !== false

  const buildFetchInit = (tokenOverride?: string): RequestInit => {
    const headers: Record<string, string> = {}
    const resolvedBody =
      typeof options.body === 'function' ? (options.body as () => unknown)() : options.body
    const isForm = resolvedBody instanceof FormData
    if (resolvedBody !== undefined && !isForm) {
      headers['Content-Type'] = 'application/json'
    }
    const token = tokenOverride ?? auth.accessToken
    if (useAuth && token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    return {
      method: options.method ?? 'GET',
      headers,
      body: isForm ? resolvedBody : resolvedBody !== undefined ? JSON.stringify(resolvedBody) : undefined,
    }
  }

  const toRefreshError = (e: unknown): ApiError => {
    if (options.forceLogoutOnRefreshFail !== false) {
      forceLogout()
    }
    return e instanceof ApiError ? e : new ApiError('登录已过期，请重新登录', 401)
  }

  // 若已有刷新在进行，先等待其完成再读取令牌与请求体，避免并发下旧 refreshToken 与新 accessToken 混用
  if (useAuth && refreshing) {
    try {
      await refreshing
    } catch (e) {
      throw toRefreshError(e)
    }
  }

  const targetUrl = resolveApiUrl(url)
  let { res, result } = await fetchJson<T>(targetUrl, buildFetchInit())

  // access token 过期：刷新后重放一次（重放时重新求值 body，确保携带最新的 refreshToken）
  const unauthorized = result?.code === 401 || (res.status === 401 && result?.code === undefined)
  if (unauthorized && useAuth) {
    let newToken: string
    try {
      newToken = await refreshOnce()
    } catch (e) {
      throw toRefreshError(e)
    }
    ;({ res, result } = await fetchJson<T>(targetUrl, buildFetchInit(newToken)))
  }

  if (!res.ok || !result || result.code !== 200) {
    throw new ApiError(
      result?.message ?? `请求失败（${res.status}）`,
      result?.code ?? (res.ok ? undefined : res.status),
    )
  }

  return result.data as T
}
