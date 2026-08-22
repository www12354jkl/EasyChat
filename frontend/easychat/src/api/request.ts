import { useAuthStore } from '@/stores/auth'

export class ApiError extends Error {
  code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.code = code
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  /** 是否携带 Authorization header，默认 true */
  auth?: boolean
}

// ============ 401 自动刷新（单飞） ============

/** 刷新 token 的共享 Promise：并发 401 只发一次刷新请求 */
let refreshing: Promise<string> | null = null

/** 调后端 refresh 接口换新 accessToken（不走 request 封装，避免循环触发 401） */
async function doRefresh(): Promise<string> {
  const auth = useAuthStore()
  if (!auth.refreshToken) {
    throw new ApiError('未登录', 401)
  }
  // 后端 AuthController 是 @RequestBody String（JSON 字符串），需带引号序列化
  const res = await fetch('/api/auth/token/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(auth.refreshToken),
  })
  const result = (await res.json().catch(() => null)) as { code?: number; message?: string; data?: string } | null
  if (!res.ok || !result || result.code !== 200 || !result.data) {
    throw new ApiError(result?.message ?? '登录已过期', result?.code ?? 401)
  }
  auth.setAccessToken(result.data)
  return result.data
}

/** 单飞：并发请求共享同一次刷新，失败后允许下一次重试 */
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
  const headers: Record<string, string> = {}
  const body = options.body
  const isForm = body instanceof FormData
  if (body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.auth !== false && auth.accessToken) {
    headers['Authorization'] = `Bearer ${auth.accessToken}`
  }

  const doFetch = () =>
    fetch(url, {
      method: options.method ?? 'GET',
      headers,
      body: isForm ? body : body !== undefined ? JSON.stringify(body) : undefined,
    })

  let res: Response
  try {
    res = await doFetch()
  } catch {
    throw new ApiError('网络连接失败，请检查网络')
  }

  let result = (await res.json().catch(() => null)) as
    | { code?: number; message?: string; data?: T }
    | null

  // access token 过期：刷新后重放一次
  if (result?.code === 401 && options.auth !== false) {
    try {
      const newToken = await refreshOnce()
      headers['Authorization'] = `Bearer ${newToken}`
      res = await doFetch()
      result = (await res.json().catch(() => null)) as
        | { code?: number; message?: string; data?: T }
        | null
    } catch (e) {
      forceLogout()
      throw e instanceof ApiError ? e : new ApiError('登录已过期，请重新登录', 401)
    }
  }

  if (!res.ok || !result || result.code !== 200) {
    throw new ApiError(result?.message ?? `请求失败（${res.status}）`, result?.code)
  }

  return result.data as T
}
