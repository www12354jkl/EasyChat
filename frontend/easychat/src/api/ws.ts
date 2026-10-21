import { useAuthStore } from '@/stores/auth'
import { resolveApiUrl, resolveWsUrl } from '@/config/env'
import { refreshSession } from '@/api/request'
import type { ConversationUpdate, ConversationRemovedEvent, NewConversationEvent, OnlineStatusEvent, WsMessage, FriendRequestEvent, FriendRequestHandledEvent, GroupJoinRequestEvent, GroupJoinRequestHandledEvent } from '@/types/chat'

/**
 * WebSocket 客户端单例。
 * - 连接同源 /ws?ticket=xxx。票据由 accessToken 换取，30 秒一次性
 * - 心跳：每 25s 发 PING，服务端回 PONG
 * - 断线重连：指数退避（1s → 2s → 4s … 上限 30s），重连时重新申请票据
 * - 事件分发：ws.on(type, handler) / ws.off(type, handler)
 */

type Handler<T = unknown> = (payload: T) => void

const HEARTBEAT_INTERVAL = 25_000
const RECONNECT_BASE = 1_000
const RECONNECT_MAX = 30_000

class WsClient {
  private ws: WebSocket | null = null
  private handlers = new Map<string, Set<Handler>>()
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private reconnectDelay = RECONNECT_BASE
  /** 主动关闭标记：close() 后不再自动重连 */
  private manualClosed = false
  private connecting = false
  private openHandlers = new Set<() => void>()

  /** 当前是否已连接（供 UI 显示状态） */
  get isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN
  }

  /** 注册事件处理 */
  on<T>(type: WsMessage['type'], handler: Handler<T>): void {
    if (!this.handlers.has(type)) this.handlers.set(type, new Set())
    this.handlers.get(type)!.add(handler as Handler)
  }

  off<T>(type: WsMessage['type'], handler: Handler<T>): void {
    this.handlers.get(type)?.delete(handler as Handler)
  }

  /** 注册连接成功回调，含断线后的重连 */
  onOpen(handler: () => void): void {
    this.openHandlers.add(handler)
  }

  offOpen(handler: () => void): void {
    this.openHandlers.delete(handler)
  }

  /** 建立连接（幂等：已连接/连接中则忽略） */
  connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return
    if (this.connecting) return
    this.manualClosed = false
    void this.openSocket()
  }

  private async openSocket(): Promise<void> {
    this.connecting = true
    try {
      const ticket = await this.fetchTicket()
      if (!ticket || this.manualClosed) {
        this.scheduleReconnect()
        return
      }
      if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return
      const ws = new WebSocket(resolveWsUrl(ticket))
      this.ws = ws
      ws.onopen = () => {
        this.reconnectDelay = RECONNECT_BASE
        this.startHeartbeat()
        this.openHandlers.forEach((handler) => handler())
      }
      ws.onmessage = (ev) => {
        let msg: WsMessage
        try {
          msg = JSON.parse(ev.data as string) as WsMessage
        } catch {
          return
        }
        if (msg.type === 'PONG') return
        this.handlers.get(msg.type)?.forEach((h) => h(msg.payload))
      }
      ws.onclose = () => {
        this.stopHeartbeat()
        if (this.ws === ws) this.ws = null
        if (this.manualClosed) return
        this.scheduleReconnect()
      }
      ws.onerror = () => {
        ws.close()
      }
    } finally {
      this.connecting = false
    }
  }

  private scheduleReconnect(): void {
    if (this.manualClosed || this.reconnectTimer) return
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, this.reconnectDelay)
    this.reconnectDelay = Math.min(this.reconnectDelay * 2, RECONNECT_MAX)
  }

  private async fetchTicket(): Promise<string | null> {
    const auth = useAuthStore()
    if (!auth.accessToken) return null
    const load = async (token: string) => {
      const res = await fetch(resolveApiUrl('/api/chat/ws-ticket'), {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
      return (await res.json().catch(() => null)) as { code?: number; data?: { ticket?: string } } | null
    }
    let result = await load(auth.accessToken)
    if (result?.code === 401 && auth.refreshToken) {
      try {
        await refreshSession()
      } catch {
        return null
      }
      if (!auth.accessToken) return null
      result = await load(auth.accessToken)
    }
    if (result?.code !== 200 || !result.data?.ticket) return null
    return result.data.ticket
  }

  /** 主动关闭（登出时调用），不再自动重连 */
  close(): void {
    this.manualClosed = true
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
  }

  /** 发送原始文本帧（心跳用） */
  sendRaw(data: string): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(data)
    }
  }

  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.sendRaw(JSON.stringify({ type: 'PING' }))
    }, HEARTBEAT_INTERVAL)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }
}

export const ws = new WsClient()

/** 批量查询在线状态（后端 GET /api/chat/online?userIds=...），失败返回空对象不打扰用户 */
export async function fetchOnlineStatus(userIds: string[]): Promise<Record<string, boolean>> {
  if (!userIds.length) return {}
  try {
    const res = await fetch(resolveApiUrl(`/api/chat/online?userIds=${encodeURIComponent(userIds.join(','))}`), {
      headers: { Authorization: `Bearer ${useAuthStore().accessToken}` },
    })
    const result = (await res.json().catch(() => null)) as { code?: number; data?: Record<string, boolean> } | null
    if (!res.ok || !result || result.code !== 200 || !result.data) return {}
    return result.data
  } catch {
    return {}
  }
}

// 类型导出，方便消费方引用
export type { ConversationUpdate, ConversationRemovedEvent, NewConversationEvent, OnlineStatusEvent, WsMessage, FriendRequestEvent, FriendRequestHandledEvent, GroupJoinRequestEvent, GroupJoinRequestHandledEvent }
