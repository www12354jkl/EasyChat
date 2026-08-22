<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConversationList from '@/components/chat/ConversationList.vue'
import GroupDetailView from '@/views/GroupDetailView.vue'
import UserDetailView from '@/views/UserDetailView.vue'
import GroupInviteModal from '@/components/GroupInviteModal.vue'
import { useChatStore, formatTime, mapNewConversationEvent } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { getMessagePage, sendMessage, uploadChatFile, markConversationRead } from '@/api/chat'
import { fetchOnlineStatus, ws } from '@/api/ws'
import type { ChatMessage, ConversationRemovedEvent, ConversationUpdate, GroupInviteContent, GroupInviteInfo, NewConversationEvent, OnlineStatusEvent } from '@/types/chat'

const chatStore = useChatStore()
const auth = useAuthStore()
const toast = useToastStore()
const router = useRouter()
const route = useRoute()

const activeId = ref('')
const activeConversation = computed(() =>
  chatStore.conversations.find((c) => c.id === activeId.value),
)

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const plusOpen = ref(false)
const uploading = ref(false)
const chatFileInput = ref<HTMLInputElement | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const page = ref(1)
const pageSize = 30
const totalMessages = ref(0)
const loadingOlder = ref(false)
const hasMore = computed(() => messages.value.length < totalMessages.value)
/** 单聊对方是否在线（NEW_MESSAGE 之外的 ONLINE_STATUS 事件 + 打开会话时查询） */
const peerOnline = ref(false)
/** 右侧好友/群详情侧栏；null 表示关闭 */
const detailPanel = ref<{ type: 'group' | 'user'; id: string } | null>(null)
const previewIndex = ref(-1)
const imageMessages = computed(() => messages.value.filter((m) => m.contentType === 2))
const previewImage = computed(() => imageMessages.value[previewIndex.value]?.content ?? null)
const canPrev = computed(() => previewIndex.value > 0)
const canNext = computed(() => previewIndex.value < imageMessages.value.length - 1)

// 自己发的消息标识：mock 用 'me'，后端返回当前用户 userId
const isMine = (m: ChatMessage) => m.senderId === 'me' || m.senderId === auth.user?.userId

function avatarOf(m: ChatMessage) {
  return isMine(m) ? auth.user?.avatar ?? '' : m.senderAvatar ?? ''
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadMessages(convId: string) {
  try {
    page.value = 1
    const data = await getMessagePage(convId, 1, pageSize)
    totalMessages.value = data.total
    messages.value = data.records.slice().reverse().map(toChatMessage)
  } catch {
    messages.value = []
    totalMessages.value = 0
    toast.error('消息加载失败')
  }
  scrollToBottom()
}

async function loadOlder(convId: string) {
  if (loadingOlder.value || !hasMore.value) return
  loadingOlder.value = true
  try {
    const next = page.value + 1
    const data = await getMessagePage(convId, next, pageSize)
    page.value = next
    totalMessages.value = data.total
    const older = data.records.slice().reverse().map(toChatMessage)
    const el = messageListRef.value
    const prevHeight = el?.scrollHeight ?? 0
    messages.value = [...older, ...messages.value]
    await nextTick()
    if (el) {
      el.scrollTop = el.scrollHeight - prevHeight
    }
  } catch {
    // 加载更早消息失败时静默处理
  } finally {
    loadingOlder.value = false
  }
}

function onScroll() {
  const el = messageListRef.value
  if (el && el.scrollTop <= 20) {
    loadOlder(activeId.value)
  }
}

/** 后端 MessageDTO -> 前端 ChatMessage（time 复用 formatTime 的 ISO 解析） */
function toChatMessage(m: {
  id: string
  conversationId: string
  senderId: string
  senderName?: string | null
  senderAvatar?: string | null
  content: string
  contentType?: number | null
  createdAt?: string | null
}): ChatMessage {
  return {
    id: m.id,
    conversationId: m.conversationId,
    senderId: m.senderId,
    senderName: m.senderName ?? undefined,
    senderAvatar: m.senderAvatar ?? undefined,
    content: m.content,
    contentType: m.contentType ?? 1,
    time: formatTime(m.createdAt ?? null) || new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }),
  }
}

/** WS: 新消息。只有接收方会收到（发送方走乐观 push），当前活跃会话直接上屏，否则刷新列表 */
function handleNewMessage(m: {
  id: string
  conversationId: string
  senderId: string
  senderName?: string | null
  senderAvatar?: string | null
  content: string
  contentType?: number | null
  createdAt?: string | null
}) {
  const summary =
    m.contentType === 2
      ? '[图片]'
      : m.contentType === 3
        ? '[文件]'
        : m.contentType === 4
          ? inviteSummary(m.content)
          : m.content
  const isActive = m.conversationId === activeId.value
  let conv = chatStore.conversations.find((c) => c.id === m.conversationId)
  if (!conv) {
    chatStore.fetchConversations(true)
    conv = chatStore.conversations.find((c) => c.id === m.conversationId)
  }
  const unread = isActive ? 0 : (conv?.unread ?? 0) + 1
  // 更新会话列表摘要 + 置顶（含活跃会话，lastMessage 同步）
  chatStore.updateConversation(m.conversationId, {
    lastMessage: summary,
    lastTime: formatTime(m.createdAt ?? null),
    unread,
  })
  if (isActive) {
    // 防重复：乐观消息 id 是 local- 前缀，后端消息直接 push 不会撞
    messages.value.push(toChatMessage(m))
    scrollToBottom()
  }
}

/** WS: 消息内容更新（群邀请状态变更等） */
function handleMessageUpdate(m: {
  id: string
  conversationId: string
  senderId: string
  senderName?: string | null
  senderAvatar?: string | null
  content: string
  contentType?: number | null
  createdAt?: string | null
}) {
  if (m.conversationId !== activeId.value) return
  const idx = messages.value.findIndex((x) => x.id === m.id)
  if (idx >= 0) {
    messages.value[idx] = toChatMessage(m)
  }
}

/** WS: 会话被移除（删好友等） */
function handleConversationRemoved(e: ConversationRemovedEvent) {
  if (!e?.id) return
  chatStore.removeConversation(e.id)
  if (activeId.value === e.id) {
    activeId.value = ''
    messages.value = []
    detailPanel.value = null
  }
}

/** WS: 会话摘要更新（lastMessage / lastMessageAt / total） */
function handleConversationUpdate(c: ConversationUpdate) {
  chatStore.updateConversation(c.id, {
    lastMessage: c.lastMessage ?? '',
    lastTime: formatTime(c.lastMessageAt),
  })
}

/** WS: 新群会话（入群审批通过等） */
function handleNewConversation(c: NewConversationEvent) {
  chatStore.addConversation(mapNewConversationEvent(c))
}

/** WS: 在线状态事件 */
function handleOnlineStatus(e: OnlineStatusEvent) {
  if (activeConversation.value?.friendId === e.userId) {
    peerOnline.value = e.online
  }
}

/** 打开单聊会话时查询对方初始在线状态 */
async function refreshPeerOnline() {
  peerOnline.value = false
  const friendId = activeConversation.value?.friendId
  if (!friendId) return
  const map = await fetchOnlineStatus([friendId])
  peerOnline.value = !!map[friendId]
}

/** 群邀请弹窗 */
const inviteModalOpen = ref(false)
const activeInvite = ref<GroupInviteInfo | null>(null)

function inviteSummary(content: string) {
  const info = parseInviteContent(content)
  return info ? `[群邀请] ${info.groupName}` : '[群邀请]'
}

function parseInviteContent(content: string): GroupInviteContent | null {
  try {
    return JSON.parse(content) as GroupInviteContent
  } catch {
    return null
  }
}

function inviteInfo(m: ChatMessage): GroupInviteContent | null {
  if (m.contentType !== 4) return null
  return parseInviteContent(m.content)
}

function inviteCardLabel(m: ChatMessage) {
  const info = inviteInfo(m)
  if (!info) return '群聊邀请'
  if (info.inviteStatus === 'waiting') return '已申请，等待审批'
  if (info.inviteStatus === 'joined') return '已加入该群'
  if (info.inviteStatus === 'expired') return '邀请已失效'
  return isMine(m) ? '已发送群邀请' : '邀请你加入群聊'
}

function canOpenInvite(m: ChatMessage) {
  const info = inviteInfo(m)
  if (!info || isMine(m)) return false
  return info.inviteStatus === 'pending' || info.inviteStatus === 'waiting'
}

function openInviteFromMessage(m: ChatMessage) {
  const info = inviteInfo(m)
  if (!info || !canOpenInvite(m)) return
  activeInvite.value = {
    inviteId: info.inviteId,
    name: info.groupName,
    avatar: info.groupAvatar,
    joinVerify: info.joinVerify,
    inviterName: info.inviterName,
    inviteStatus: info.inviteStatus,
  }
  inviteModalOpen.value = true
}

function patchInviteMessage(inviteId: string, inviteStatus: GroupInviteContent['inviteStatus']) {
  messages.value = messages.value.map((m) => {
    const info = inviteInfo(m)
    if (info?.inviteId !== inviteId) return m
    return { ...m, content: JSON.stringify({ ...info, inviteStatus }) }
  })
}

function handleSelect(id: string) {
  activeId.value = id
  loadMessages(id)
  markRead(id)
}

async function markRead(id: string) {
  const conv = chatStore.conversations.find((c) => c.id === id)
  if (conv && conv.unread > 0) {
    chatStore.setUnread(id, 0)
  }
  try {
    await markConversationRead(id)
  } catch {
    // 标记已读失败不影响聊天
  }
}

async function handleSend() {
  const content = inputText.value.trim()
  const conv = activeConversation.value
  if (!content || !conv) return
  inputText.value = ''

  // 乐观更新：先本地显示，后端就绪后由接口返回真实消息
  const now = new Date()
  const localMsg: ChatMessage = {
    id: `local-${Date.now()}`,
    conversationId: conv.id,
    senderId: 'me',
    content,
    time: now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }),
  }
  messages.value.push(localMsg)
  chatStore.addConversation({
    ...conv,
    lastMessage: content,
    lastTime: localMsg.time,
  })
  scrollToBottom()

  try {
    await sendMessage({ conversationId: conv.id, content, contentType: 1 })
  } catch (e) {
    messages.value = messages.value.filter((m) => m.id !== localMsg.id)
    toast.error(e instanceof Error ? e.message : '发送失败')
  }
}

function fileInfo(m: ChatMessage) {
  if (m.contentType !== 3) return null
  try {
    return JSON.parse(m.content) as { url: string; name: string; size: number }
  } catch {
    return null
  }
}

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let n = bytes
  let i = 0
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024
    i++
  }
  return `${n.toFixed(1)} ${units[i]}`
}

function appendLocalMessage(content: string, contentType: number) {
  const conv = activeConversation.value
  if (!conv) return
  const now = new Date()
  const localMsg: ChatMessage = {
    id: `local-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    conversationId: conv.id,
    senderId: 'me',
    content,
    contentType,
    time: now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }),
  }
  messages.value.push(localMsg)
  chatStore.addConversation({
    ...conv,
    lastMessage: contentType === 2 ? '[图片]' : contentType === 3 ? '[文件]' : content,
    lastTime: localMsg.time,
  })
  scrollToBottom()
}

async function sendImage(file: File) {
  const conv = activeConversation.value
  if (!conv || uploading.value) return
  uploading.value = true
  try {
    const url = await uploadChatFile(file)
    await sendMessage({ conversationId: conv.id, content: url, contentType: 2 })
    appendLocalMessage(url, 2)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '图片发送失败')
  } finally {
    uploading.value = false
  }
}

async function sendFile(file: File) {
  const conv = activeConversation.value
  if (!conv || uploading.value) return
  uploading.value = true
  try {
    const url = await uploadChatFile(file)
    const content = JSON.stringify({ url, name: file.name, size: file.size })
    await sendMessage({ conversationId: conv.id, content, contentType: 3 })
    appendLocalMessage(content, 3)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '文件发送失败')
  } finally {
    uploading.value = false
  }
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  plusOpen.value = false
  if (file) sendFile(file)
}

function handlePaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        e.preventDefault()
        sendImage(file)
        return
      }
    }
  }
}

function togglePlus() {
  plusOpen.value = !plusOpen.value
}

function openPreview(m: ChatMessage) {
  const idx = imageMessages.value.findIndex((x) => x.id === m.id)
  if (idx >= 0) {
    previewIndex.value = idx
  }
}

function closePreview() {
  previewIndex.value = -1
}

function prevImage() {
  if (canPrev.value) {
    previewIndex.value--
  }
}

function nextImage() {
  if (canNext.value) {
    previewIndex.value++
  }
}

watch(activeId, () => {
  detailPanel.value = null
  scrollToBottom()
})

// 切换会话时刷新对方在线状态
watch(activeConversation, () => {
  refreshPeerOnline()
})

watch(() => chatStore.conversations, (list) => {
  if (activeId.value && !list.some((c) => c.id === activeId.value)) {
    activeId.value = ''
  }
})

// 支持从外部跳转选中会话：/chat?conversationId=xxx（如通讯录点群）
// 等会话列表加载完成后再判断，避免竞态误报"会话不存在"
function applyQueryConversation() {
  if (!chatStore.loaded) return
  const cid = route.query.conversationId
  if (typeof cid !== 'string' || !cid) return
  if (!chatStore.conversations.some((c) => c.id === cid)) {
    toast.error('会话不存在或已退出')
    router.replace('/chat')
    return
  }
  if (cid !== activeId.value) {
    handleSelect(cid)
  }
}

watch(() => route.query.conversationId, applyQueryConversation)
watch([() => chatStore.loaded, () => chatStore.conversations], applyQueryConversation, { immediate: true })

/** WS: 新会话（群邀请等） */
function onInviteJoined(groupConversationId: string) {
  if (activeInvite.value?.inviteId) {
    patchInviteMessage(activeInvite.value.inviteId, 'joined')
  }
  inviteModalOpen.value = false
  activeInvite.value = null
  chatStore.fetchConversations(true).then(() => {
    activeId.value = groupConversationId
    loadMessages(groupConversationId)
  })
}

function onInviteWaiting() {
  if (activeInvite.value?.inviteId) {
    patchInviteMessage(activeInvite.value.inviteId, 'waiting')
  }
  inviteModalOpen.value = false
  activeInvite.value = null
}

function onInviteDeclined() {
  if (activeInvite.value?.inviteId) {
    patchInviteMessage(activeInvite.value.inviteId, 'expired')
  }
  inviteModalOpen.value = false
  activeInvite.value = null
}

/** 三个点：群聊进群详情，单聊进对方个人信息 */
function openDetail() {
  const conv = activeConversation.value
  if (!conv) return
  if (conv.isGroup) {
    detailPanel.value = { type: 'group', id: conv.id }
  } else {
    detailPanel.value = { type: 'user', id: conv.friendId ?? conv.id }
  }
}

function closeDetail() {
  detailPanel.value = null
}

// ============ WebSocket 生命周期 ============

onMounted(() => {
  ws.connect()
  ws.on('NEW_MESSAGE', handleNewMessage)
  ws.on('MESSAGE_UPDATE', handleMessageUpdate)
  ws.on('CONVERSATION_REMOVED', handleConversationRemoved)
  ws.on('CONVERSATION_UPDATE', handleConversationUpdate)
  ws.on('NEW_CONVERSATION', handleNewConversation)
  ws.on('ONLINE_STATUS', handleOnlineStatus)
  refreshPeerOnline()
})

onUnmounted(() => {
  ws.off('NEW_MESSAGE', handleNewMessage)
  ws.off('MESSAGE_UPDATE', handleMessageUpdate)
  ws.off('CONVERSATION_REMOVED', handleConversationRemoved)
  ws.off('CONVERSATION_UPDATE', handleConversationUpdate)
  ws.off('NEW_CONVERSATION', handleNewConversation)
  ws.off('ONLINE_STATUS', handleOnlineStatus)
  // 不主动 close：登出由 auth 层统一断连，页面切换保持连接
})
</script>

<template>
  <div class="flex h-full min-w-[960px]">
    <ConversationList
      :conversations="chatStore.conversations"
      :active-id="activeId"
      @select="handleSelect"
    />

    <!-- 聊天区 -->
    <div class="flex min-w-[320px] flex-1 flex-col bg-[#F5F5F5]">
      <!-- 顶部：会话名 + 在线状态 + 三个点 -->
      <div class="flex h-[48px] shrink-0 items-center border-b border-[#E5E5E5] bg-white px-3">
        <h2 class="flex min-w-0 flex-1 items-center gap-1.5 text-base font-medium text-[#1A1A1A]">
          <span class="truncate">{{ activeConversation?.name ?? '' }}</span>
          <!-- 单聊对方在线绿点 -->
          <span
            v-if="activeConversation && !activeConversation.isGroup && activeConversation.friendId"
            class="h-2 w-2 shrink-0 rounded-full"
            :class="peerOnline ? 'bg-[#07C160]' : 'bg-[#C8C8C8]'"
            :title="peerOnline ? '在线' : '离线'"
          />
        </h2>
        <button
          v-if="activeConversation && !isInviteConversation"
          type="button"
          class="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-md text-[#1A1A1A] transition-colors hover:bg-[#F5F5F5]"
          title="群设置"
          @click="openDetail"
        >
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
            <circle cx="5" cy="12" r="1.8" />
            <circle cx="12" cy="12" r="1.8" />
            <circle cx="19" cy="12" r="1.8" />
          </svg>
        </button>
      </div>

      <!-- 未选中会话：空态 -->
      <div
        v-if="!activeConversation"
        class="flex flex-1 flex-col items-center justify-center gap-2 text-gray-400"
      >
        <svg
          class="h-12 w-12 opacity-40"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <path
            d="M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <p class="text-sm">选择会话开始聊天</p>
      </div>

      <!-- 消息区 -->
      <template v-if="activeConversation">
        <div ref="messageListRef" class="flex-1 overflow-y-auto px-6 py-4" @scroll="onScroll">
          <div class="space-y-4">
            <p v-if="loadingOlder" class="py-1 text-center text-xs text-[#B2B2B2]">加载中...</p>
            <!-- 时间分隔（简化：不显示） -->
            <div
              v-for="m in messages"
              :key="m.id"
              class="flex items-start gap-2"
              :class="isMine(m) ? 'flex-row-reverse' : ''"
            >
              <!-- 头像 -->
              <div
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded-[4px] text-sm font-medium text-white"
                :style="{ backgroundColor: isMine(m) ? '#07C160' : '#5B8FF9' }"
              >
                <img
                  v-if="avatarOf(m)"
                  :src="avatarOf(m)"
                  class="h-full w-full object-cover"
                  alt="头像"
                />
                <span v-else>{{ ((isMine(m) ? auth.user?.nickname : m.senderName) ?? '?').charAt(0) }}</span>
              </div>

              <!-- 气泡 -->
              <div class="flex min-w-0 max-w-[60%] flex-col" :class="isMine(m) ? 'items-end' : 'items-start'">
                <!-- 群聊他人消息显示昵称 -->
                <p v-if="activeConversation?.isGroup && !isMine(m)" class="mb-0.5 px-0.5 text-xs text-[#B2B2B2]">
                  {{ m.senderName }}
                </p>
                <div
                  class="overflow-hidden rounded-lg shadow-sm"
                  :class="
                    m.contentType === 4
                      ? 'rounded-lg bg-white text-[#1A1A1A]'
                      : isMine(m)
                        ? 'rounded-tr-sm bg-[#07C160] text-white'
                        : 'rounded-tl-sm bg-white text-[#1A1A1A]'
                  "
                >
                  <div
                    v-if="m.contentType === 4 && inviteInfo(m)"
                    class="w-[220px] border border-[#E5E5E5] px-3 py-2.5"
                    :class="canOpenInvite(m) ? 'cursor-pointer hover:bg-[#FAFAFA]' : ''"
                    @click="openInviteFromMessage(m)"
                  >
                    <p class="text-xs text-[#576B95]">群聊邀请</p>
                    <p class="mt-1 truncate text-sm font-medium">{{ inviteInfo(m)?.groupName }}</p>
                    <p class="mt-1 text-xs text-[#8A8A8A]">{{ inviteCardLabel(m) }}</p>
                  </div>
                  <img
                    v-else-if="m.contentType === 2"
                    :src="m.content"
                    class="max-h-[240px] max-w-[240px] cursor-zoom-in object-cover"
                    alt="图片"
                    @click="openPreview(m)"
                  />
                  <a
                    v-else-if="m.contentType === 3"
                    :href="fileInfo(m)?.url"
                    target="_blank"
                    rel="noopener"
                    class="flex items-center gap-2 px-3 py-2 text-sm"
                  >
                    <svg class="h-6 w-6 shrink-0 opacity-80" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <path d="M14 2v6h6" />
                    </svg>
                    <span class="min-w-0">
                      <span class="block truncate">{{ fileInfo(m)?.name }}</span>
                      <span class="block text-xs opacity-70">{{ formatSize(fileInfo(m)?.size ?? 0) }}</span>
                    </span>
                  </a>
                  <div v-else class="whitespace-pre-wrap break-words px-3 py-2 text-sm leading-relaxed">{{ m.content }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="shrink-0 border-t border-[#E5E5E5] bg-white px-4 py-3">
          <div class="flex items-end gap-3">
            <div class="relative shrink-0">
              <button
                type="button"
                class="flex h-[38px] w-10 cursor-pointer items-center justify-center rounded-md border border-[#E5E5E5] text-[#1A1A1A] transition-colors hover:bg-[#F5F5F5]"
                title="发送文件"
                @click="togglePlus"
              >
                <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </button>
              <div
                v-if="plusOpen"
                class="absolute bottom-full left-0 z-20 mb-2 w-28 rounded-md border border-[#E5E5E5] bg-white py-1 shadow-lg"
              >
                <button
                  type="button"
                  class="flex w-full cursor-pointer items-center gap-2 px-3 py-2 text-sm text-[#1A1A1A] transition-colors hover:bg-[#F5F5F5]"
                  @click="chatFileInput?.click()"
                >
                  <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <path d="M14 2v6h6" />
                  </svg>
                  文件
                </button>
              </div>
            </div>
            <textarea
              v-model="inputText"
              rows="1"
              placeholder="输入消息..."
              class="max-h-24 min-h-[38px] flex-1 resize-none rounded-md border border-[#E5E5E5] bg-[#F7F7F7] px-3 py-2 text-sm outline-none transition focus:border-[#07C160] focus:bg-white focus:ring-2 focus:ring-[#07C160]/20"
              @keydown.enter.exact.prevent="handleSend"
              @paste="handlePaste"
            ></textarea>
            <span v-if="uploading" class="shrink-0 text-xs text-[#B2B2B2]">发送中...</span>
            <button
              type="button"
              class="shrink-0 cursor-pointer rounded-md bg-[#07C160] px-5 py-2 text-sm font-medium text-white transition hover:bg-[#06ad56]"
              :disabled="!inputText.trim()"
              :class="{ 'cursor-not-allowed opacity-40': !inputText.trim() }"
              @click="handleSend"
            >
              发送
            </button>
          </div>
          <input ref="chatFileInput" type="file" class="hidden" @change="handleFileChange" />
        </div>
      </template>
    </div>

    <!-- 好友/群详情侧栏 -->
    <GroupDetailView
      v-if="detailPanel?.type === 'group'"
      :id="detailPanel?.id"
      class="w-[min(340px,32vw)] shrink-0 border-l border-[#E5E5E5]"
      @close="closeDetail"
    />
    <UserDetailView
      v-else-if="detailPanel?.type === 'user'"
      :id="detailPanel?.id"
      class="w-[min(340px,32vw)] shrink-0 border-l border-[#E5E5E5]"
      @close="closeDetail"
    />

    <!-- 图片放大预览 -->
    <div
      v-if="previewImage"
      class="fixed inset-0 z-[60] flex items-center justify-center gap-4 bg-black/80 p-6"
      @click.self="closePreview"
    >
      <button
        type="button"
        :disabled="!canPrev"
        class="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20 disabled:cursor-not-allowed disabled:opacity-30"
        title="上一张"
        @click="prevImage"
      >
        <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="m15 18-6-6 6-6" />
        </svg>
      </button>

      <img :src="previewImage" class="max-h-[70vh] max-w-[70vw] object-contain" alt="预览" />

      <button
        type="button"
        :disabled="!canNext"
        class="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20 disabled:cursor-not-allowed disabled:opacity-30"
        title="下一张"
        @click="nextImage"
      >
        <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="m9 18 6-6-6-6" />
        </svg>
      </button>

      <button
        type="button"
        class="absolute right-4 top-4 flex h-9 w-9 cursor-pointer items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20"
        title="关闭"
        @click="closePreview"
      >
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </div>

    <GroupInviteModal
      v-if="inviteModalOpen && activeInvite"
      :invite="activeInvite"
      @close="inviteModalOpen = false; activeInvite = null"
      @joined="onInviteJoined"
      @waiting="onInviteWaiting"
      @declined="onInviteDeclined"
    />
  </div>
</template>

<style scoped></style>
