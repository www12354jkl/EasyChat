<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteFriend, getUserDetail, type UserProfile } from '@/api/user'
import { clearConversationMessages } from '@/api/chat'
import { clearLocalMessages } from '@/utils/messageDb'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useToastStore } from '@/stores/toast'
import { ecAvatarStyle } from '@/utils/ec-ui'
import ConfirmModal from '@/components/ConfirmModal.vue'
import ChatHistoryModal from '@/components/ChatHistoryModal.vue'

const props = defineProps<{ id?: string }>()
const emit = defineEmits<{ close: []; cleared: [conversationId: string] }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const chatStore = useChatStore()
const toast = useToastStore()

// 单聊会话 id 即对方用户 id（后端会话模型就绪后 friendId 为准）
const userId = props.id ?? String(route.params.id)
const chatHistoryOpen = ref(false)
const conversationId = computed(() => {
  const me = auth.user?.userId
  if (!me) return ''
  return me < userId ? `${me}_${userId}` : `${userId}_${me}`
})

function onBack() {
  if (props.id) {
    emit('close')
  } else {
    router.back()
  }
}

const profile = ref<UserProfile | null>(null)

onMounted(async () => {
  try {
    profile.value = await getUserDetail(userId)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '用户信息加载失败')
  }
})

function openChatHistory() {
  chatHistoryOpen.value = true
}

// ============ 确认弹窗（删除聊天记录 / 删除好友） ============
const confirmState = ref<{ type: 'clear' | 'deleteFriend' } | null>(null)

async function handleConfirm() {
  if (!confirmState.value) return
  const type = confirmState.value.type
  confirmState.value = null
  if (type === 'clear') {
    try {
      await clearConversationMessages(conversationId.value)
      const uid = auth.user?.userId
      if (uid) {
        try {
          await clearLocalMessages(uid, conversationId.value)
        } catch {
          // IndexedDB 不可用时忽略
        }
      }
      chatStore.updateConversation(conversationId.value, { lastMessage: '', lastTime: '' })
      chatHistoryOpen.value = false
      emit('cleared', conversationId.value)
      toast.success('聊天记录已清除')
    } catch (e) {
      toast.error((e as Error).message)
    }
  } else {
    deleteFriendAction()
  }
}

async function deleteFriendAction() {
  try {
    await deleteFriend(userId)
    chatStore.removeConversation(conversationId.value)
    toast.success('已删除好友')
    if (props.id) {
      emit('close')
    } else {
      router.push('/chat')
    }
  } catch (e) {
    toast.error((e as Error).message)
  }
}

function avatarStyle(): Record<string, string> {
  return ecAvatarStyle(profile.value?.avatar)
}
</script>

<template>
  <div class="ec-page">
    <header class="ec-page-header justify-between">
      <h1 class="ec-page-title truncate">个人信息</h1>
      <button
        type="button"
        class="ec-icon-btn"
        title="关闭"
        @click="onBack"
      >
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </header>

    <div class="flex-1 overflow-y-auto">
      <!-- 用户信息卡片 -->
      <div class="bg-[var(--ec-bg)] px-4 py-5">
        <div class="flex items-center gap-4">
          <div
            class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-full text-xl font-medium text-white"
            :style="avatarStyle()"
          >
            <img v-if="profile?.avatar" :src="profile.avatar" class="h-full w-full object-cover" alt="头像" />
            <span v-else>{{ profile?.nickname?.charAt(0) ?? '?' }}</span>
          </div>
          <div class="min-w-0">
            <p class="truncate text-base font-medium text-[var(--ec-text)]">{{ profile?.nickname }}</p>
            <p class="mt-1 truncate text-xs text-[var(--ec-text-muted)]">{{ profile?.signature || '暂无签名' }}</p>
            <p v-if="profile?.region" class="mt-0.5 truncate text-xs text-[var(--ec-text-faint)]">{{ profile.region }}</p>
          </div>
        </div>
      </div>

      <!-- 操作 -->
      <div class="mt-3 bg-[var(--ec-bg)]">
        <!-- 聊天记录 -->
        <div class="flex items-center justify-between px-4 py-3">
          <span class="text-sm text-[var(--ec-text)]">聊天记录</span>
          <button
            type="button"
            class="flex cursor-pointer items-center gap-1 text-sm text-[var(--ec-text-muted)] transition-colors hover:text-[var(--ec-text)]"
            @click="openChatHistory"
          >
            查看
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </button>
        </div>
        <div class="mx-4 border-t border-[var(--ec-border)]"></div>

        <!-- 删除聊天记录 -->
        <button
          type="button"
          class="w-full cursor-pointer px-4 py-3 text-left text-sm text-[var(--ec-danger)] transition-colors duration-200 hover:bg-red-50"
          @click="confirmState = { type: 'clear' }"
        >
          删除聊天记录
        </button>
        <div class="mx-4 border-t border-[var(--ec-border)]"></div>

        <!-- 删除好友 -->
        <button
          type="button"
          class="w-full cursor-pointer px-4 py-3 text-left text-sm text-[var(--ec-danger)] transition-colors duration-200 hover:bg-red-50"
          @click="confirmState = { type: 'deleteFriend' }"
        >
          删除好友
        </button>
      </div>
    </div>

    <!-- 确认弹窗 -->
    <ConfirmModal
      v-if="confirmState"
      :title="confirmState.type === 'clear' ? '删除聊天记录' : '删除好友'"
      :message="confirmState.type === 'clear' ? '确定删除与该好友的聊天记录吗？仅清除你本地可见的记录，对方不受影响。' : '删除后你们将不再是好友，且会移除相关聊天记录，确定删除吗？'"
      :confirm-text="confirmState.type === 'clear' ? '删除' : '删除好友'"
      danger
      @confirm="handleConfirm"
      @cancel="confirmState = null"
    />
    <ChatHistoryModal
      v-if="chatHistoryOpen"
      :conversation-id="conversationId"
      :name="profile?.nickname"
      @close="chatHistoryOpen = false"
    />
  </div>
</template>

<style scoped></style>
