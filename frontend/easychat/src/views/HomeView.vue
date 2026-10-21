<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { logout } from '@/api/auth'
import { getSettings, updateFriendVerify } from '@/api/user'
import ChangePasswordModal from '@/components/ChangePasswordModal.vue'
import { formatTime, mapNewConversationEvent, useChatStore } from '@/stores/chat'
import { useContactsNotifyStore } from '@/stores/contactsNotify'
import { ws } from '@/api/ws'
import type {
  ConversationRemovedEvent,
  ConversationUpdate,
  FriendRequestEvent,
  FriendRequestHandledEvent,
  GroupJoinRequestEvent,
  NewConversationEvent,
} from '@/types/chat'

const auth = useAuthStore()
const toast = useToastStore()
const router = useRouter()
const chatStore = useChatStore()
const contactsNotify = useContactsNotifyStore()

const menuOpen = ref(false)
const pwdModalOpen = ref(false)
const friendVerify = ref(false)

function handleConversationRemoved(e: ConversationRemovedEvent) {
  if (e?.id) {
    chatStore.removeConversation(e.id)
  }
}

function handleConversationUpdate(c: ConversationUpdate) {
  if (!c?.id) return
  chatStore.updateConversation(c.id, {
    lastMessage: c.lastMessage ?? '',
    lastTime: formatTime(c.lastMessageAt),
  })
}

function handleFriendRequest(e: FriendRequestEvent) {
  contactsNotify.onFriendRequest()
  toast.info(`${e.fromName} 请求添加你为好友`)
}

function handleFriendRequestHandled(e: FriendRequestHandledEvent) {
  if (e.status === 1) {
    toast.success(`${e.nickname} 已同意你的好友申请`)
  } else {
    toast.info(`${e.nickname} 拒绝了你的好友申请`)
  }
}

function handleGroupJoinRequest(e: GroupJoinRequestEvent) {
  contactsNotify.onGroupJoinRequest()
  toast.info(`${e.nickname} 申请加入「${e.groupName}」`)
}

function handleGroupJoinRequestHandled() {
  contactsNotify.onGroupJoinRequestHandled()
}

function handleNewConversation(c: NewConversationEvent) {
  chatStore.addConversation(mapNewConversationEvent(c))
}

function syncAfterReconnect() {
  chatStore.fetchConversations(true)
}

onMounted(async () => {
  ws.onOpen(syncAfterReconnect)
  ws.on('CONVERSATION_REMOVED', handleConversationRemoved)
  ws.on('CONVERSATION_UPDATE', handleConversationUpdate)
  ws.on('NEW_CONVERSATION', handleNewConversation)
  ws.on('FRIEND_REQUEST', handleFriendRequest)
  ws.on('FRIEND_REQUEST_HANDLED', handleFriendRequestHandled)
  ws.on('GROUP_JOIN_REQUEST', handleGroupJoinRequest)
  ws.on('GROUP_JOIN_REQUEST_HANDLED', handleGroupJoinRequestHandled)
  contactsNotify.refresh()
  try {
    const s = await getSettings()
    friendVerify.value = s.friendVerify === 1
  } catch {
    // 拉取失败保持默认
  }
  chatStore.fetchConversations()
})

onUnmounted(() => {
  ws.offOpen(syncAfterReconnect)
  ws.off('CONVERSATION_REMOVED', handleConversationRemoved)
  ws.off('CONVERSATION_UPDATE', handleConversationUpdate)
  ws.off('NEW_CONVERSATION', handleNewConversation)
  ws.off('FRIEND_REQUEST', handleFriendRequest)
  ws.off('FRIEND_REQUEST_HANDLED', handleFriendRequestHandled)
  ws.off('GROUP_JOIN_REQUEST', handleGroupJoinRequest)
  ws.off('GROUP_JOIN_REQUEST_HANDLED', handleGroupJoinRequestHandled)
})

async function handleLogout() {
  try {
    await logout(auth.refreshToken ?? '')
  } catch {
    // 后端拉黑失败不阻塞登出
  } finally {
    auth.clearSession()
    toast.success('已退出登录')
    router.push('/login')
  }
}

async function toggleFriendVerify() {
  friendVerify.value = !friendVerify.value
  try {
    await updateFriendVerify({ friendVerify: friendVerify.value ? 1 : 0 })
  } catch (err) {
    friendVerify.value = !friendVerify.value
    toast.error(err instanceof Error ? err.message : '设置保存失败')
  }
}
</script>

<template>
  <div class="flex h-full overflow-hidden bg-[var(--ec-bg)]">
    <!-- 左侧导航：极简浅色栏 -->
    <aside
      class="relative flex w-[68px] shrink-0 flex-col items-center border-r border-[var(--ec-border)] bg-[var(--ec-bg)] py-4"
    >
      <RouterLink
        to="/profile"
        class="mb-5 flex h-10 w-10 items-center justify-center overflow-hidden rounded-full ring-2 ring-transparent transition-[ring-color,opacity] duration-200 hover:opacity-90 focus-visible:outline-none focus-visible:ring-[var(--ec-primary)]"
        title="个人信息"
      >
        <img
          v-if="auth.user?.avatar"
          :src="auth.user.avatar"
          class="h-full w-full object-cover"
          alt="头像"
        />
        <span
          v-else
          class="flex h-full w-full items-center justify-center bg-[var(--ec-primary-soft)] text-sm font-medium text-[var(--ec-primary)]"
        >
          {{ auth.user?.nickname?.charAt(0) ?? '?' }}
        </span>
      </RouterLink>

      <nav class="flex w-full flex-1 flex-col items-center gap-1">
        <RouterLink
          to="/chat"
          class="nav-item"
          active-class="nav-item-active"
          title="聊天"
        >
          <svg class="h-[22px] w-[22px]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
          </svg>
        </RouterLink>

        <RouterLink
          to="/contacts"
          class="nav-item relative"
          active-class="nav-item-active"
          title="通讯录"
        >
          <svg class="h-[22px] w-[22px]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
            <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
          <span
            v-if="contactsNotify.totalBadge > 0"
            class="absolute right-2 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-[var(--ec-danger)] px-1 text-[10px] font-medium text-white"
          >
            {{ contactsNotify.totalBadge > 99 ? '99+' : contactsNotify.totalBadge }}
          </span>
        </RouterLink>
      </nav>

      <div class="relative w-full">
        <button
          type="button"
          class="nav-item"
          title="设置"
          :class="{ 'nav-item-active': menuOpen }"
          @click="menuOpen = !menuOpen"
        >
          <svg class="h-[22px] w-[22px]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
          </svg>
        </button>

        <div v-if="menuOpen" class="fixed inset-0 z-20" @click="menuOpen = false" />

        <div
          v-if="menuOpen"
          class="absolute bottom-2 left-[76px] z-30 w-52 overflow-hidden rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] py-1 shadow-[0_8px_24px_rgba(26,46,36,0.08)]"
        >
          <button
            type="button"
            class="flex w-full cursor-pointer items-center px-4 py-2.5 text-sm text-[var(--ec-text)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)]"
            @click="menuOpen = false; pwdModalOpen = true"
          >
            修改密码
          </button>

          <div class="flex items-center justify-between px-4 py-2.5">
            <span class="text-sm text-[var(--ec-text)]">加好友需验证</span>
            <button
              type="button"
              class="relative h-6 w-10 cursor-pointer rounded-full transition-colors duration-200"
              :class="friendVerify ? 'bg-[var(--ec-primary)]' : 'bg-[var(--ec-border)]'"
              role="switch"
              :aria-checked="friendVerify"
              @click="toggleFriendVerify"
            >
              <span
                class="absolute top-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-all duration-200"
                :class="friendVerify ? 'left-[18px]' : 'left-0.5'"
              />
            </button>
          </div>

          <div class="my-1 border-t border-[var(--ec-border)]" />

          <button
            type="button"
            class="flex w-full cursor-pointer items-center px-4 py-2.5 text-sm text-[var(--ec-danger)] transition-colors duration-200 hover:bg-red-50"
            @click="menuOpen = false; handleLogout()"
          >
            退出登录
          </button>
        </div>
      </div>
    </aside>

    <main class="min-w-0 flex-1 overflow-x-auto bg-[var(--ec-bg-subtle)]">
      <RouterView />
    </main>

    <ChangePasswordModal v-if="pwdModalOpen" @close="pwdModalOpen = false" />
  </div>
</template>

<style scoped>
.nav-item {
  display: flex;
  width: 44px;
  height: 44px;
  margin: 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: var(--ec-radius);
  color: var(--ec-text-faint);
  transition:
    color var(--ec-transition),
    background-color var(--ec-transition);
  cursor: pointer;
}

.nav-item:hover {
  color: var(--ec-text-muted);
  background: var(--ec-bg-muted);
}

.nav-item-active {
  color: var(--ec-primary);
  background: var(--ec-primary-soft);
}
</style>
