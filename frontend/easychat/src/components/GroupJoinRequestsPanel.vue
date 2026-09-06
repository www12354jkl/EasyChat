<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import {
  getGroupJoinRequests,
  handleGroupJoinRequest,
  type GroupJoinRequestItem,
} from '@/api/chat'
import { ws } from '@/api/ws'
import { formatTime } from '@/stores/chat'
import { useToastStore } from '@/stores/toast'
import type { GroupJoinRequestEvent, GroupJoinRequestHandledEvent } from '@/types/chat'
import { ecAvatarStyle } from '@/utils/ec-ui'

const props = defineProps<{
  conversationId?: string
  showTitle?: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const toast = useToastStore()
const requests = ref<GroupJoinRequestItem[]>([])
const loading = ref(false)

function matchesScope(conversationId: string) {
  return !props.conversationId || props.conversationId === conversationId
}

async function load() {
  loading.value = true
  try {
    requests.value = await getGroupJoinRequests(props.conversationId)
  } catch {
    requests.value = []
  } finally {
    loading.value = false
  }
}

function addRequest(e: GroupJoinRequestEvent) {
  if (!matchesScope(e.conversationId)) return
  if (requests.value.some((r) => r.id === e.id)) return
  requests.value.unshift({
    id: e.id,
    conversationId: e.conversationId,
    groupName: e.groupName,
    groupAvatar: e.groupAvatar,
    userId: e.userId,
    nickname: e.nickname,
    avatar: e.avatar,
    message: e.message,
    createdAt: e.createdAt,
  })
}

function removeRequest(id: string, conversationId: string) {
  if (!matchesScope(conversationId)) return
  requests.value = requests.value.filter((r) => r.id !== id)
}

function handleWsJoinRequest(e: GroupJoinRequestEvent) {
  const before = requests.value.length
  addRequest(e)
  if (requests.value.length > before) emit('changed')
}

function handleWsJoinRequestHandled(e: GroupJoinRequestHandledEvent) {
  const before = requests.value.length
  removeRequest(e.id, e.conversationId)
  if (requests.value.length !== before) emit('changed')
}

onMounted(() => {
  load()
  ws.on('GROUP_JOIN_REQUEST', handleWsJoinRequest)
  ws.on('GROUP_JOIN_REQUEST_HANDLED', handleWsJoinRequestHandled)
})

onUnmounted(() => {
  ws.off('GROUP_JOIN_REQUEST', handleWsJoinRequest)
  ws.off('GROUP_JOIN_REQUEST_HANDLED', handleWsJoinRequestHandled)
})

defineExpose({ reload: load, count: () => requests.value.length, addRequest, removeRequest })

function isImageAvatar(avatar: string): boolean {
  return !!avatar && !avatar.startsWith('#')
}

async function handleAction(req: GroupJoinRequestItem, accept: boolean) {
  try {
    await handleGroupJoinRequest(req.id, accept ? 1 : 2)
    toast.success(accept ? `已同意 ${req.nickname} 加入「${req.groupName}」` : '已拒绝入群申请')
    await load()
    emit('changed')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  }
}
</script>

<template>
  <div>
    <p v-if="showTitle !== false" class="mb-3 text-sm font-semibold text-[var(--ec-text)]">入群申请</p>

    <div v-if="loading" class="py-6 text-center text-sm text-[var(--ec-text-faint)]">加载中...</div>
    <div v-else-if="requests.length" class="space-y-3">
      <div v-for="r in requests" :key="r.id" class="flex items-center gap-3">
        <div
          class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-medium text-white"
          :style="ecAvatarStyle(r.avatar)"
        >
          <img v-if="isImageAvatar(r.avatar)" :src="r.avatar" class="h-full w-full object-cover" alt="头像" />
          <span v-else>{{ r.nickname.charAt(0) }}</span>
        </div>
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm text-[var(--ec-text)]">
            {{ r.nickname }}
            <span v-if="!conversationId" class="text-[var(--ec-text-muted)]"> · {{ r.groupName }}</span>
          </p>
          <p class="truncate text-xs text-[var(--ec-text-muted)]">{{ r.message || '申请加入群聊' }}</p>
        </div>
        <span class="shrink-0 text-xs text-[var(--ec-text-faint)]">{{ formatTime(r.createdAt) }}</span>
        <div class="flex shrink-0 gap-2">
          <button type="button" class="ec-btn-primary px-3 py-1 text-xs" @click="handleAction(r, true)">
            同意
          </button>
          <button type="button" class="ec-btn-secondary px-3 py-1 text-xs" @click="handleAction(r, false)">
            拒绝
          </button>
        </div>
      </div>
    </div>
    <p v-else class="py-6 text-center text-sm text-[var(--ec-text-faint)]">暂无入群申请</p>
  </div>
</template>
