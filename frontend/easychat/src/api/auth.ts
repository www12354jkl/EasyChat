import { request } from './request'

export interface LoginParams {
  email: string
  password: string
}

export interface RegisterParams {
  email: string
  nickname: string
  password: string
  emailCode: string
}

export interface ForgetPasswordParams {
  email: string
  code: string
  newPassword: string
}

export interface UserInfo {
  userId: string
  email: string
  nickname: string
  /** 头像 URL，个人资料拉取后同步 */
  avatar?: string
}

/** 后端 AuthResponse：accessToken + refreshToken + userInfo */
export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userInfo: UserInfo
}

export function login(params: LoginParams): Promise<AuthResponse> {
  return request<AuthResponse>('/api/auth/login', { method: 'POST', body: params, auth: false })
}

export function register(params: RegisterParams): Promise<AuthResponse> {
  // 后端字段名是 code，前端是 emailCode，此处映射
  return request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: {
      email: params.email,
      nickname: params.nickname,
      password: params.password,
      code: params.emailCode,
    },
    auth: false,
  })
}

export type VerifyCodeScene = 'register' | 'forgot'

// 后端是 @RequestParam，走 query；type 区分场景（register 查重/forgot 查存在）
export function sendEmailCode(email: string, type: VerifyCodeScene): Promise<void> {
  return request<void>(
    `/api/auth/send-verify-code?email=${encodeURIComponent(email)}&type=${type}`,
    { method: 'POST', auth: false },
  )
}

export function forgetPassword(params: ForgetPasswordParams): Promise<void> {
  return request<void>('/api/auth/forget-password', { method: 'PUT', body: params, auth: false })
}

// 登出：后端将 token 拉黑
export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' })
}
