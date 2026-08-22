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

onMounted(async () => {
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
    await logout()
  } catch {
    // 后端拉黑失败不阻塞登出，本地会话照常清理
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
    friendVerify.value = !friendVerify.value // 失败回滚
    toast.error(err instanceof Error ? err.message : '设置保存失败')
  }
}
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-white">
    <!-- 左侧功能导航栏（PC 微信风格：深色窄栏） -->
    <aside class="relative flex w-[60px] shrink-0 flex-col items-center bg-[#2E2E2E] py-3">
      <!-- 顶部头像 → 个人资料 -->
      <RouterLink
        to="/profile"
        class="mb-6 flex h-10 w-10 items-center justify-center overflow-hidden rounded-[4px] transition-opacity duration-150 hover:opacity-80"
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
          class="flex h-full w-full items-center justify-center text-sm font-medium text-white"
          :style="{ backgroundColor: '#07C160' }"
        >
          {{ auth.user?.nickname?.charAt(0) ?? '?' }}
        </span>
      </RouterLink>

      <!-- 功能导航 -->
      <nav class="flex w-full flex-1 flex-col items-center">
        <RouterLink
          to="/chat"
          class="nav-item"
          active-class="nav-item-active"
          title="聊天"
        >
          <svg class="h-6 w-6" viewBox="0 0 24 24" fill="currentColor">
            <path
              d="M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z"
            />
          </svg>
        </RouterLink>

        <RouterLink
          to="/contacts"
          class="nav-item relative"
          active-class="nav-item-active"
          title="通讯录"
        >
          <svg class="h-6 w-6" viewBox="0 0 24 24" fill="currentColor">
            <path
              d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-3.3 0-8 1.7-8 5v1h16v-1c0-3.3-4.7-5-8-5zm7-1.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zm.5 1.7c1.7.5 5.5 2 5.5 4.8v1h-5v-1c0-1.9-.9-3.4-2.1-4.5.5-.2 1-.3 1.6-.3z"
            />
          </svg>
          <span
            v-if="contactsNotify.totalBadge > 0"
            class="absolute right-2 top-2 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#FA5151] px-1 text-[10px] font-medium text-white"
          >
            {{ contactsNotify.totalBadge > 99 ? '99+' : contactsNotify.totalBadge }}
          </span>
        </RouterLink>

      </nav>

      <!-- 底部设置按钮 + 弹出菜单 -->
      <div class="relative w-full">
        <button
          type="button"
          class="nav-item"
          title="设置"
          :class="{ 'nav-item-active': menuOpen }"
          @click="menuOpen = !menuOpen"
        >
          <svg
            class="h-6 w-6"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="12" cy="12" r="3" />
            <path
              d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
            />
          </svg>
        </button>

        <!-- 点击外部关闭 -->
        <div
          v-if="menuOpen"
          class="fixed inset-0 z-20"
          @click="menuOpen = false"
        ></div>

        <!-- 设置菜单 -->
        <div
          v-if="menuOpen"
          class="absolute bottom-2 left-[64px] z-30 w-52 rounded-lg border border-[#E5E5E5] bg-white py-1 shadow-lg"
        >
          <button
            type="button"
            class="flex w-full cursor-pointer items-center px-4 py-2.5 text-sm text-[#1A1A1A] transition-colors duration-150 hover:bg-[#F5F5F5]"
            @click="menuOpen = false; pwdModalOpen = true"
          >
            修改密码
          </button>

          <div class="flex items-center justify-between px-4 py-2.5">
            <span class="text-sm text-[#1A1A1A]">加好友需验证</span>
            <button
              type="button"
              class="relative h-6 w-10 cursor-pointer rounded-full transition-colors duration-200"
              :class="friendVerify ? 'bg-[#07C160]' : 'bg-[#D6D6D6]'"
              role="switch"
              :aria-checked="friendVerify"
              @click="toggleFriendVerify"
            >
              <span
                class="absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all duration-200"
                :class="friendVerify ? 'left-[18px]' : 'left-0.5'"
              ></span>
            </button>
          </div>

          <div class="my-1 border-t border-[#F0F0F0]"></div>

          <button
            type="button"
            class="flex w-full cursor-pointer items-center px-4 py-2.5 text-sm text-[#FA5151] transition-colors duration-150 hover:bg-[#F5F5F5]"
            @click="menuOpen = false; handleLogout()"
          >
            退出登录
          </button>
        </div>
      </div>
    </aside>

    <!-- 内容区 -->
    <main class="min-w-0 flex-1 overflow-x-auto">
      <RouterView />
    </main>

    <!-- 修改密码弹窗 -->
    <ChangePasswordModal v-if="pwdModalOpen" @close="pwdModalOpen = false" />
  </div>
</template>

<style scoped>
.nav-item {
  display: flex;
  width: 100%;
  padding: 14px 0;
  align-items: center;
  justify-content: center;
  color: #9a9a9a;
  transition: color 0.15s ease;
  cursor: pointer;
}
.nav-item:hover {
  color: #ffffff;
}
.nav-item-active {
  color: #07c160;
}
</style>
