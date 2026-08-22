import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getConversations, type ConversationItem } from '@/api/chat'
import type { Conversation, NewConversationEvent } from '@/types/chat'

/** 后端 ConversationResponse -> 前端 Conversation */
export function mapConversationItem(c: ConversationItem): Conversation {
  return {
    id: c.id,
    name: c.name,
    avatar: c.avatar ?? '',
    avatarColor: '#07C160',
    lastMessage: c.lastMessage ?? '',
    lastTime: formatTime(c.lastMessageAt),
    unread: c.unread,
    type: c.type,
    isGroup: c.isGroup,
    isGroupInvite: c.isGroupInvite ?? c.type === 3,
    friendId: c.friendId ?? undefined,
    groupId: c.groupId ?? undefined,
    inviteId: c.inviteId ?? undefined,
    joinVerify: c.joinVerify,
    inviterName: c.inviterName ?? undefined,
    inviterAvatar: c.inviterAvatar ?? undefined,
    inviteStatus: c.inviteStatus,
  }
}

/** WS NEW_CONVERSATION -> 前端 Conversation */
export function mapNewConversationEvent(c: NewConversationEvent): Conversation {
  return {
    id: c.id,
    name: c.name,
    avatar: c.avatar ?? '',
    avatarColor: '#07C160',
    lastMessage: c.lastMessage ?? '',
    lastTime: formatTime(c.lastMessageAt),
    unread: c.unread ?? 0,
    type: c.type,
    isGroup: c.isGroup,
    isGroupInvite: c.isGroupInvite ?? c.type === 3,
    friendId: c.friendId ?? undefined,
    groupId: c.groupId ?? undefined,
    inviteId: c.inviteId ?? undefined,
    joinVerify: c.joinVerify,
    inviterName: c.inviterName ?? undefined,
    inviterAvatar: c.inviterAvatar ?? undefined,
    inviteStatus: c.inviteStatus,
  }
}

/**
 * 聊天会话列表 store。
 * 首次拉取真实会话列表（GET /api/chat/conversations）。
 */
export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const loaded = ref(false)

  /** 拉取我的会话列表（幂等：已加载则跳过） */
  async function fetchConversations(force = false) {
    if (loaded.value && !force) return
    try {
      const list = await getConversations()
      conversations.value = list.map(mapConversationItem)
      loaded.value = true
    } catch {
      conversations.value = []
      loaded.value = true
    }
  }

  /** 添加/置顶会话（已存在则移到最前） */
  function addConversation(c: Conversation) {
    const idx = conversations.value.findIndex((x) => x.id === c.id)
    if (idx >= 0) {
      conversations.value.splice(idx, 1)
    }
    conversations.value.unshift(c)
  }

  /** 移除会话（退出群聊/删除好友/忽略邀请用） */
  function removeConversation(id: string) {
    conversations.value = conversations.value.filter((x) => x.id !== id)
  }

  /** 仅更新未读数，不触发置顶重排 */
  function setUnread(id: string, unread: number) {
    const idx = conversations.value.findIndex((x) => x.id === id)
    if (idx >= 0) {
      conversations.value[idx] = { ...conversations.value[idx], unread } as Conversation
    }
  }

  /** 更新会话摘要并置顶（WS CONVERSATION_UPDATE / 发送消息后调用） */
  function updateConversation(id: string, patch: Partial<Conversation>) {
    const idx = conversations.value.findIndex((x) => x.id === id)
    if (idx >= 0) {
      conversations.value[idx] = { ...conversations.value[idx], ...patch } as Conversation
    }
    const item = conversations.value.find((x) => x.id === id)
    if (item) {
      conversations.value.splice(conversations.value.indexOf(item), 1)
      conversations.value.unshift(item)
    }
  }

  function reset() {
    conversations.value = []
    loaded.value = false
  }

  return { conversations, loaded, fetchConversations, addConversation, removeConversation, setUnread, updateConversation, reset }
})

/** 后端 LocalDateTime -> 列表显示时间（今天显示 HH:mm，否则 MM-DD） */
export function formatTime(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  const pad = (n: number) => String(n).padStart(2, '0')
  if (sameDay) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
