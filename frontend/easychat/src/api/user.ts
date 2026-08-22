import { request } from './request'

export interface UserProfile {
  id: string
  email: string
  nickname: string
  avatar: string
  gender: number
  region: string
  regionCode: string
  signature: string
}

// 后端从 UserContext 取当前用户，无参
export function getProfile(): Promise<UserProfile> {
  return request<UserProfile>('/api/user/profile')
}

/** 查看他人信息（后端 GET /api/user/{id}） */
export function getUserDetail(id: string): Promise<UserProfile> {
  return request<UserProfile>(`/api/user/${id}`)
}

/** 删除好友（后端 DELETE /api/user/friend/{id}） */
export function deleteFriend(id: string): Promise<void> {
  return request<void>(`/api/user/friend/${id}`, { method: 'DELETE' })
}

// ============ 好友系统 ============

/** 搜索用户（按昵称/邮箱模糊） */
export function searchUser(keyword: string): Promise<FriendItem[]> {
  return request<FriendItem[]>(`/api/user/search?keyword=${encodeURIComponent(keyword)}`)
}

/** 发好友申请；返回 status: added=直接加成功 / pending=已发送等待同意 */
export function sendFriendRequest(
  toUserId: string,
  message: string,
): Promise<{ status: 'added' | 'pending' }> {
  return request<{ status: 'added' | 'pending' }>('/api/user/friend/request', {
    method: 'POST',
    body: { toUserId, message },
  })
}

/** 我收到的好友申请 */
export interface FriendRequestItem {
  id: string
  fromUserId: string
  fromName: string
  fromAvatar: string
  message: string
  createdAt: string
}

export function getFriendRequests(): Promise<FriendRequestItem[]> {
  return request<FriendRequestItem[]>('/api/user/friend/requests')
}

/** 处理申请：status 1=同意 2=拒绝 */
export function handleFriendRequest(id: string, status: number): Promise<void> {
  return request<void>(`/api/user/friend/request/${id}`, { method: 'PUT', body: { status } })
}

/** 我的好友列表 */
export interface FriendItem {
  userId: string
  nickname: string
  avatar: string
  signature?: string
}

export function getFriends(): Promise<FriendItem[]> {
  return request<FriendItem[]>('/api/user/friends')
}

export function updateProfile(params: Partial<UserProfile>): Promise<UserProfile> {
  return request<UserProfile>('/api/user/profile', { method: 'PUT', body: params })
}

// 头像上传（multipart），返回 COS URL
export function uploadAvatar(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  return request<string>('/api/user/avatar', { method: 'PUT', body: formData })
}

// ============ 修改密码（登录态） ============

/** 发送改密码验证码到当前登录账号的邮箱（后端从 UserContext 取 userId） */
export function sendResetCode(): Promise<void> {
  return request<void>('/api/user/send-reset-code', { method: 'POST' })
}

export interface ResetPasswordParams {
  email: string
  code: string
  oldPassword: string
  newPassword: string
}

export function resetPassword(params: ResetPasswordParams): Promise<void> {
  return request<void>('/api/user/password', { method: 'PUT', body: params })
}

// ============ 用户设置 ============

export interface FriendVerifyParams {
  /** 1=需要验证 0=不需要 */
  friendVerify: number
}

export function updateFriendVerify(params: FriendVerifyParams): Promise<void> {
  return request<void>('/api/user/friend-verify', { method: 'PUT', body: params })
}

export interface SettingsResult {
  friendVerify: number
}

export function getSettings(): Promise<SettingsResult> {
  return request<SettingsResult>('/api/user/settings')
}
