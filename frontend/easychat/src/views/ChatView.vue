<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConversationList from '@/components/chat/ConversationList.vue'
import GroupDetailView from '@/views/GroupDetailView.vue'
import UserDetailView from '@/views/UserDetailView.vue'
import GroupInviteModal from '@/components/GroupInviteModal.vue'
import { useChatStore, formatTime, mapNewConversationEvent } from '@/stores/chat'
import { formatMessageTime, shouldShowTimeDivider } from '@/utils/messageTime'
import { useNowTicker } from '@/composables/useNowTicker'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { getMessagePage, recallMessage, sendMessage, uploadChatFile, markConversationRead, type MessageRecord } from '@/api/chat'
import { fetchOnlineStatus, ws } from '@/api/ws'
import {
  clearLocalMessages,
  getLocalMessages,
  putLocalMessages,
  replaceLocalMessages,
} from '@/utils/messageDb'
import { hasElectronBridge } from '@/config/env'
import { showElectronContextMenu } from '@/utils/electronContextMenu'
import type { ChatMessage, ConversationRemovedEvent, ConversationUpdate, GroupInviteContent, GroupInviteInfo, NewConversationEvent, OnlineStatusEvent } from '@/types/chat'
import { inviteCardLabel as inviteCardLabelFor, inviteSummary, parseInviteContent } from '@/utils/groupInviteMessage'

const chatStore = useChatStore()
const auth = useAuthStore()
const toast = useToastStore()
const router = useRouter()
const route = useRoute()

const activeId = ref('')
const activeConversation = computed(() =>
  chatStore.conversations.find((c) => c.id === activeId.value),
)
const isInviteConversation = computed(
  () => !!activeConversation.value?.isGroupInvite || activeConversation.value?.type === 3,
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

const RECALL_WINDOW_MS = 2 * 60 * 1000
const MESSAGE_STATUS_RECALLED = 2

type MessageMenuItem = { id: string; label: string; type?: 'separator' }
const webMessageMenu = ref<{ x: number; y: number; message: ChatMessage; items: MessageMenuItem[] } | null>(null)
const now = useNowTicker()
const showBackToBottom = ref(false)

const SCROLL_NEAR_BOTTOM = 120
const SCROLL_LOAD_OLDER = 48

// 自己发的消息标识：mock 用 'me'，后端返回当前用户 userId
const isMine = (m: ChatMessage) => m.senderId === 'me' || m.senderId === auth.user?.userId

function avatarOf(m: ChatMessage) {
  return isMine(m) ? auth.user?.avatar ?? '' : m.senderAvatar ?? ''
}

function isNearBottom(el: HTMLElement | null, threshold = SCROLL_NEAR_BOTTOM) {
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

function updateScrollState() {
  const el = messageListRef.value
  if (!el) return
  showBackToBottom.value = !isNearBottom(el)
}

function scrollToBottom(smooth = false) {
  nextTick(() => {
    const el = messageListRef.value
    if (!el) return
    if (smooth) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    } else {
      el.scrollTop = el.scrollHeight
    }
    showBackToBottom.value = false
  })
}

function goToLatest() {
  scrollToBottom(true)
}

function currentUserId() {
  return auth.user?.userId
}

async function cacheReplace(convId: string, records: MessageRecord[]) {
  const uid = currentUserId()
  if (!uid) return
  try {
    await replaceLocalMessages(uid, convId, records)
  } catch {
    // IndexedDB 不可用时忽略
  }
}

async function cachePut(records: MessageRecord[]) {
  const uid = currentUserId()
  if (!uid || !records.length) return
  try {
    await putLocalMessages(uid, records)
  } catch {
    // IndexedDB 不可用时忽略
  }
}

async function loadMessages(convId: string) {
  const uid = currentUserId()
  page.value = 1
  if (uid) {
    try {
      const cached = await getLocalMessages(uid, convId)
      if (cached.length && convId === activeId.value) {
        messages.value = cached.slice(-pageSize).map(toChatMessage)
        scrollToBottom()
      }
    } catch {
      // 本地缓存失败则等服务端
    }
  }
  try {
    const data = await getMessagePage(convId, 1, pageSize)
    if (convId !== activeId.value) return
    totalMessages.value = data.total
    messages.value = data.records.slice().reverse().map(toChatMessage)
    await cacheReplace(convId, data.records)
  } catch {
    if (convId !== activeId.value) return
    if (!messages.value.length) {
      messages.value = []
      totalMessages.value = 0
      toast.error('消息加载失败')
    }
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
    await cachePut(data.records)
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
  if (!el) return
  updateScrollState()
  if (el.scrollTop <= SCROLL_LOAD_OLDER && hasMore.value && !loadingOlder.value) {
    loadOlder(activeId.value)
  }
}

/** 后端 MessageDTO -> 前端 ChatMessage */
function toChatMessage(m: {
  id: string
  conversationId: string
  senderId: string
  senderName?: string | null
  senderAvatar?: string | null
  content: string
  contentType?: number | null
  status?: number | null
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
    status: m.status ?? 1,
    createdAt: m.createdAt ?? undefined,
  }
}

function isRecalled(m: ChatMessage) {
  return m.status === MESSAGE_STATUS_RECALLED
}

function canRecall(m: ChatMessage) {
  if (!isMine(m) || isRecalled(m)) return false
  const ct = m.contentType ?? 1
  if (ct < 1 || ct > 3) return false
  if (!m.createdAt) return false
  return Date.now() - new Date(m.createdAt).getTime() <= RECALL_WINDOW_MS
}

function recallHint(m: ChatMessage) {
  if (isMine(m)) return '你撤回了一条消息'
  if (activeConversation.value?.isGroup) {
    return `${m.senderName ?? '对方'} 撤回了一条消息`
  }
  return '对方撤回了一条消息'
}

function isLatestMessageInView(m: ChatMessage) {
  return !messages.value.some(
    (other) => other.id !== m.id && (other.createdAt ?? '') > (m.createdAt ?? ''),
  )
}

function applyRecallSideEffects(m: MessageRecord) {
  if (m.status !== MESSAGE_STATUS_RECALLED) return
  if (m.conversationId === activeId.value) {
    if (isLatestMessageInView(toChatMessage(m))) {
      chatStore.updateConversation(m.conversationId, {
        lastMessage: '[已撤回]',
        lastTime: formatTime(m.createdAt ?? null),
      })
    }
  } else {
    chatStore.fetchConversations(true)
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
          ? inviteSummaryContent(m.content)
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
    const stick = isNearBottom(messageListRef.value)
    messages.value.push(toChatMessage(m))
    if (stick) {
      scrollToBottom()
    } else {
      updateScrollState()
    }
  }
  cachePut([m])
}

/** WS: 消息内容更新（群邀请状态变更、撤回等） */
function handleMessageUpdate(m: {
  id: string
  conversationId: string
  senderId: string
  senderName?: string | null
  senderAvatar?: string | null
  content: string
  contentType?: number | null
  status?: number | null
  createdAt?: string | null
}) {
  cachePut([m])
  if (m.conversationId === activeId.value) {
    const idx = messages.value.findIndex((x) => x.id === m.id)
    if (idx >= 0) {
      messages.value[idx] = toChatMessage(m)
    }
  }
  applyRecallSideEffects(m)
}

/** WS: 会话被移除（删好友等） */
function handleConversationRemoved(e: ConversationRemovedEvent) {
  if (!e?.id) return
  const uid = currentUserId()
  if (uid) {
    clearLocalMessages(uid, e.id).catch(() => {})
  }
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

function inviteSummaryContent(content: string) {
  return inviteSummary(content)
}

function inviteInfo(m: ChatMessage): GroupInviteContent | null {
  if (m.contentType !== 4) return null
  return parseInviteContent(m.content)
}

function inviteCardLabel(m: ChatMessage) {
  return inviteCardLabelFor(inviteInfo(m), { isSender: isMine(m) })
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
  showBackToBottom.value = false
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
  const nowIso = new Date().toISOString()
  const localMsg: ChatMessage = {
    id: `local-${Date.now()}`,
    conversationId: conv.id,
    senderId: 'me',
    content,
    createdAt: nowIso,
  }
  messages.value.push(localMsg)
  chatStore.addConversation({
    ...conv,
    lastMessage: content,
    lastTime: formatTime(nowIso),
  })
  scrollToBottom()

  try {
    const saved = await sendMessage({ conversationId: conv.id, content, contentType: 1 })
    const idx = messages.value.findIndex((m) => m.id === localMsg.id)
    if (idx >= 0) messages.value[idx] = toChatMessage(saved)
    await cachePut([saved])
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

function buildMessageMenuItems(m: ChatMessage): MessageMenuItem[] {
  const items: MessageMenuItem[] = []
  const copyItems: MessageMenuItem[] = []

  if (canRecall(m)) {
    items.push({ id: 'recall', label: '撤回' })
  }

  if (!isRecalled(m)) {
    if (m.contentType === 2) {
      copyItems.push({ id: 'copy-link', label: '复制图片链接' })
    } else if (m.contentType === 3) {
      if (fileInfo(m)?.url) copyItems.push({ id: 'copy-link', label: '复制文件链接' })
    } else if (m.contentType === 4) {
      const name = inviteInfo(m)?.groupName
      if (name) copyItems.push({ id: 'copy-text', label: '复制群名' })
    } else {
      copyItems.push({ id: 'copy-text', label: '复制' })
    }
  }

  if (copyItems.length && items.length) {
    items.push({ id: 'sep', label: '', type: 'separator' })
  }
  items.push(...copyItems)
  return items
}

async function handleMessageMenuAction(action: string | null, m: ChatMessage) {
  if (!action) return

  if (action === 'recall') {
    try {
      const updated = await recallMessage(m.id)
      handleMessageUpdate(updated)
      toast.success('已撤回')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '撤回失败')
    }
    return
  }

  if (action === 'copy-text') {
    const text = m.contentType === 4 ? inviteInfo(m)?.groupName ?? '' : m.content
    if (!text) return
    await navigator.clipboard.writeText(text)
    toast.success('已复制')
    return
  }

  if (action === 'copy-link') {
    const link = m.contentType === 2 ? m.content : fileInfo(m)?.url ?? ''
    if (!link) return
    await navigator.clipboard.writeText(link)
    toast.success('已复制链接')
  }
}

function closeWebMessageMenu() {
  webMessageMenu.value = null
}

async function onWebMessageMenuSelect(action: string) {
  const m = webMessageMenu.value?.message
  closeWebMessageMenu()
  if (!m) return
  await handleMessageMenuAction(action, m)
}

async function onMessageContextMenu(event: MouseEvent, m: ChatMessage) {
  const items = buildMessageMenuItems(m)
  if (!items.length) return

  event.preventDefault()
  event.stopPropagation()

  if (hasElectronBridge()) {
    const action = await showElectronContextMenu(event, items)
    await handleMessageMenuAction(action, m)
    return
  }

  webMessageMenu.value = { x: event.clientX, y: event.clientY, message: m, items }
}

function applySent(saved: MessageRecord) {
  const conv = activeConversation.value
  messages.value.push(toChatMessage(saved))
  if (conv) {
    chatStore.addConversation({
      ...conv,
      lastMessage: saved.contentType === 2 ? '[图片]' : saved.contentType === 3 ? '[文件]' : saved.content,
      lastTime: formatTime(saved.createdAt ?? null),
    })
  }
  cachePut([saved])
  scrollToBottom()
}

async function sendImage(file: File) {
  const conv = activeConversation.value
  if (!conv || uploading.value) return
  uploading.value = true
  try {
    const url = await uploadChatFile(file)
    const saved = await sendMessage({ conversationId: conv.id, content: url, contentType: 2 })
    applySent(saved)
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
    const saved = await sendMessage({ conversationId: conv.id, content, contentType: 3 })
    applySent(saved)
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

async function onHistoryCleared(conversationId: string) {
  if (activeId.value === conversationId) {
    messages.value = []
    totalMessages.value = 0
    page.value = 1
  }
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
  document.addEventListener('click', closeWebMessageMenu)
})

onUnmounted(() => {
  ws.off('NEW_MESSAGE', handleNewMessage)
  ws.off('MESSAGE_UPDATE', handleMessageUpdate)
  ws.off('CONVERSATION_REMOVED', handleConversationRemoved)
  ws.off('CONVERSATION_UPDATE', handleConversationUpdate)
  ws.off('NEW_CONVERSATION', handleNewConversation)
  ws.off('ONLINE_STATUS', handleOnlineStatus)
  document.removeEventListener('click', closeWebMessageMenu)
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
    <div class="flex min-w-[320px] flex-1 flex-col bg-[var(--ec-bg-subtle)]">
      <!-- 顶部：会话名 + 在线状态 + 三个点 -->
      <div class="flex h-[52px] shrink-0 items-center border-b border-[var(--ec-border)] bg-[var(--ec-bg)] px-4">
        <h2 class="flex min-w-0 flex-1 items-center gap-2 text-[15px] font-semibold text-[var(--ec-text)]">
          <span class="truncate">{{ activeConversation?.name ?? '' }}</span>
          <span
            v-if="activeConversation && !activeConversation.isGroup && activeConversation.friendId"
            class="h-2 w-2 shrink-0 rounded-full transition-colors duration-200"
            :class="peerOnline ? 'bg-[var(--ec-primary)]' : 'bg-[var(--ec-border)]'"
            :title="peerOnline ? '在线' : '离线'"
          />
        </h2>
        <button
          v-if="activeConversation && !isInviteConversation"
          type="button"
          class="flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-[var(--ec-radius)] text-[var(--ec-text-muted)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)] hover:text-[var(--ec-text)]"
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
        class="flex flex-1 flex-col items-center justify-center gap-3 text-[var(--ec-text-faint)]"
      >
        <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-[var(--ec-primary-soft)] text-[var(--ec-primary)]">
          <svg
            class="h-8 w-8"
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
        </div>
        <p class="text-sm text-[var(--ec-text-muted)]">选择会话开始聊天</p>
      </div>

      <!-- 消息区 -->
      <template v-if="activeConversation">
        <div class="relative min-h-0 flex-1">
          <div
            ref="messageListRef"
            class="ec-scroll h-full overflow-y-auto px-6 py-4"
            @scroll="onScroll"
          >
            <div class="space-y-4">
              <p
                v-if="loadingOlder"
                class="py-1 text-center text-xs text-[var(--ec-text-faint)]"
              >
                加载更早的消息…
              </p>
              <p
                v-else-if="hasMore"
                class="py-1 text-center text-[11px] text-[var(--ec-text-faint)]"
              >
                向上滚动加载更早消息
              </p>
            <template v-for="(m, index) in messages" :key="m.id">
              <div
                v-if="!isRecalled(m) && shouldShowTimeDivider(messages[index - 1], m)"
                class="flex justify-center py-1"
              >
                <span class="rounded-full bg-[var(--ec-bg-muted)] px-3 py-1 text-[11px] text-[var(--ec-text-faint)]">
                  {{ formatMessageTime(m.createdAt, now) }}
                </span>
              </div>
              <div
                class="flex w-full"
                :class="isRecalled(m) ? 'justify-center' : ['items-start gap-2', isMine(m) ? 'flex-row-reverse' : '']"
                @contextmenu="onMessageContextMenu($event, m)"
              >
              <div
                v-if="isRecalled(m)"
                class="rounded-full bg-[var(--ec-bg-muted)] px-3 py-1 text-xs text-[var(--ec-text-faint)]"
              >
                {{ recallHint(m) }}
              </div>
              <template v-else>
              <!-- 头像 -->
              <div
                class="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-medium text-white"
                :style="{ backgroundColor: isMine(m) ? 'var(--ec-primary)' : 'var(--ec-text-muted)' }"
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
                <p v-if="activeConversation?.isGroup && !isMine(m)" class="mb-0.5 px-0.5 text-xs text-[var(--ec-text-faint)]">
                  {{ m.senderName }}
                </p>
                <div
                  class="overflow-hidden rounded-2xl"
                  :class="
                    m.contentType === 4
                      ? 'bg-[var(--ec-bg)] text-[var(--ec-text)]'
                      : isMine(m)
                        ? 'bg-[var(--ec-primary)] text-white'
                        : 'border border-[var(--ec-border)] bg-[var(--ec-bg)] text-[var(--ec-text)]'
                  "
                >
                  <div
                    v-if="m.contentType === 4 && inviteInfo(m)"
                    class="w-[220px] border border-[var(--ec-border)] px-3 py-2.5"
                    :class="canOpenInvite(m) ? 'cursor-pointer hover:bg-[var(--ec-bg-subtle)]' : ''"
                    @click="openInviteFromMessage(m)"
                  >
                    <p class="text-xs text-[var(--ec-primary)]">群聊邀请</p>
                    <p class="mt-1 truncate text-sm font-medium">{{ inviteInfo(m)?.groupName }}</p>
                    <p class="mt-1 text-xs text-[var(--ec-text-faint)]">{{ inviteCardLabel(m) }}</p>
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
              </template>
              </div>
            </template>
          </div>
        </div>

          <Transition name="scroll-fab">
            <button
              v-if="showBackToBottom"
              type="button"
              class="absolute bottom-4 right-5 z-10 flex h-10 cursor-pointer items-center gap-1.5 rounded-full border border-[var(--ec-border)] bg-[var(--ec-bg)] px-3.5 text-sm font-medium text-[var(--ec-primary)] shadow-[0_4px_16px_rgba(26,46,36,0.12)] transition-[background-color,transform] duration-200 hover:bg-[var(--ec-primary-soft)] active:scale-[0.98]"
              title="回到最新消息"
              @click="goToLatest"
            >
              <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="m6 9 6 6 6-6" />
              </svg>
              最新消息
            </button>
          </Transition>
        </div>

        <!-- 输入区 -->
        <div class="shrink-0 border-t border-[var(--ec-border)] bg-[var(--ec-bg)] px-4 py-3">
          <div class="flex items-end gap-3">
            <div class="relative shrink-0">
              <button
                type="button"
                class="flex h-[40px] w-10 cursor-pointer items-center justify-center rounded-[var(--ec-radius)] border border-[var(--ec-border)] text-[var(--ec-text-muted)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)] hover:text-[var(--ec-text)]"
                title="发送文件"
                @click="togglePlus"
              >
                <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </button>
              <div
                v-if="plusOpen"
                class="absolute bottom-full left-0 z-20 mb-2 w-28 overflow-hidden rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] py-1 shadow-[0_8px_24px_rgba(26,46,36,0.08)]"
              >
                <button
                  type="button"
                  class="flex w-full cursor-pointer items-center gap-2 px-3 py-2 text-sm text-[var(--ec-text)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)]"
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
              class="max-h-24 min-h-[40px] flex-1 resize-none rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg-subtle)] px-3 py-2 text-sm text-[var(--ec-text)] outline-none transition-[border-color,box-shadow,background-color] duration-200 placeholder:text-[var(--ec-text-faint)] focus:border-[var(--ec-primary)] focus:bg-[var(--ec-bg)] focus:ring-[3px] focus:ring-[var(--ec-primary-ring)]"
              @keydown.enter.exact.prevent="handleSend"
              @paste="handlePaste"
            ></textarea>
            <span v-if="uploading" class="shrink-0 text-xs text-[var(--ec-text-faint)]">发送中...</span>
            <button
              type="button"
              class="shrink-0 cursor-pointer rounded-[var(--ec-radius)] bg-[var(--ec-primary)] px-5 py-2 text-sm font-medium text-white transition-colors duration-200 hover:bg-[var(--ec-primary-hover)]"
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
      class="w-[min(340px,32vw)] shrink-0 border-l border-[var(--ec-border)]"
      @close="closeDetail"
      @cleared="onHistoryCleared"
    />
    <UserDetailView
      v-else-if="detailPanel?.type === 'user'"
      :id="detailPanel?.id"
      class="w-[min(340px,32vw)] shrink-0 border-l border-[var(--ec-border)]"
      @close="closeDetail"
      @cleared="onHistoryCleared"
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

    <Teleport to="body">
      <div
        v-if="webMessageMenu"
        class="fixed z-[100] min-w-[140px] overflow-hidden rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] py-1 shadow-[0_8px_24px_rgba(26,46,36,0.08)]"
        :style="{ left: `${webMessageMenu.x}px`, top: `${webMessageMenu.y}px` }"
        @click.stop
        @contextmenu.stop.prevent
      >
        <template v-for="item in webMessageMenu.items" :key="item.id">
          <div v-if="item.type === 'separator'" class="my-1 border-t border-[var(--ec-border)]" />
          <button
            v-else
            type="button"
            class="flex w-full cursor-pointer px-4 py-2 text-left text-sm text-[var(--ec-text)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)]"
            :class="item.id === 'recall' ? 'text-[var(--ec-danger)]' : ''"
            @click="onWebMessageMenuSelect(item.id)"
          >
            {{ item.label }}
          </button>
        </template>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.scroll-fab-enter-active,
.scroll-fab-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.scroll-fab-enter-from,
.scroll-fab-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
