import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { AuthResponse, UserInfo } from '@/api/auth'
import { ws } from '@/api/ws'
import { useChatStore } from '@/stores/chat'

const ACCESS_TOKEN_KEY = 'easychat_access_token'
const REFRESH_TOKEN_KEY = 'easychat_refresh_token'
const USER_KEY = 'easychat_user'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY))
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_TOKEN_KEY))
  const user = ref<UserInfo | null>(readStoredUser())

  const isLoggedIn = computed(() => !!accessToken.value)

  function readStoredUser(): UserInfo | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserInfo
    } catch {
      return null
    }
  }

  function setSession(result: AuthResponse) {
    accessToken.value = result.accessToken
    refreshToken.value = result.refreshToken
    user.value = result.userInfo
    localStorage.setItem(ACCESS_TOKEN_KEY, result.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, result.refreshToken)
    localStorage.setItem(USER_KEY, JSON.stringify(result.userInfo))
  }

  function setAccessToken(token: string) {
    accessToken.value = token
    localStorage.setItem(ACCESS_TOKEN_KEY, token)
  }

  function setRefreshToken(token: string) {
    refreshToken.value = token
    localStorage.setItem(REFRESH_TOKEN_KEY, token)
  }

  /** 更新用户信息（内存 + localStorage 同步，保证刷新后不丢） */
  function updateUser(partial: Partial<UserInfo>) {
    if (!user.value) return
    user.value = { ...user.value, ...partial }
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  function clearSession() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    // 登出时断开 WebSocket，避免残留连接
    ws.close()
    useChatStore().reset()
  }

  return { accessToken, refreshToken, user, isLoggedIn, setSession, setAccessToken, setRefreshToken, updateUser, clearSession }
})
