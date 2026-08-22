import { useAuthStore } from '@/stores/auth'
import type { ConversationUpdate, ConversationRemovedEvent, NewConversationEvent, OnlineStatusEvent, WsMessage, FriendRequestEvent, FriendRequestHandledEvent, GroupJoinRequestEvent, GroupJoinRequestHandledEvent } from '@/types/chat'

/**
 * WebSocket 客户端单例。
 * - 连接同源 /ws?token=xxx（开发环境由 Vite 代理转发到 Netty WS 服务）
 * - 心跳：每 25s 发 PING，服务端回 PONG
 * - 断线重连：指数退避（1s → 2s → 4s … 上限 30s），重连时取最新 access token
 * - 事件分发：ws.on(type, handler) / ws.off(type, handler)
 */

type Handler<T = unknown> = (payload: T) => void

const WS_PATH = '/ws'
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

  /** 建立连接（幂等：已连接/连接中则忽略） */
  connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return
    this.manualClosed = false

    const auth = useAuthStore()
    const token = auth.accessToken
    if (!token) return

    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${window.location.host}${WS_PATH}?token=${encodeURIComponent(token)}`
    const ws = new WebSocket(url)
    this.ws = ws

    ws.onopen = () => {
      this.reconnectDelay = RECONNECT_BASE
      this.startHeartbeat()
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
      this.ws = null
      if (this.manualClosed) return
      // 指数退避重连
      this.reconnectTimer = setTimeout(() => this.connect(), this.reconnectDelay)
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, RECONNECT_MAX)
    }

    ws.onerror = () => {
      // 由 onclose 统一处理重连
      ws.close()
    }
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
    const res = await fetch(`/api/chat/online?userIds=${encodeURIComponent(userIds.join(','))}`, {
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
