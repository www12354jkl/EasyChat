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

const props = defineProps<{
  /** 仅查看某个群的申请；不传则显示全部 */
  conversationId?: string
  /** 是否显示标题 */
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

function avatarStyle(avatar: string): Record<string, string> {
  return { backgroundColor: avatar?.startsWith('#') ? avatar : '#07C160' }
}

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
    <p v-if="showTitle !== false" class="mb-3 text-sm font-medium text-[#1A1A1A]">入群申请</p>

    <div v-if="loading" class="py-6 text-center text-sm text-[#B2B2B2]">加载中...</div>
    <div v-else-if="requests.length" class="space-y-3">
      <div
        v-for="r in requests"
        :key="r.id"
        class="flex items-center gap-3"
      >
        <div
          class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
          :style="avatarStyle(r.avatar)"
        >
          <img v-if="isImageAvatar(r.avatar)" :src="r.avatar" class="h-full w-full object-cover" alt="头像" />
          <span v-else>{{ r.nickname.charAt(0) }}</span>
        </div>
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm text-[#1A1A1A]">
            {{ r.nickname }}
            <span v-if="!conversationId" class="text-[#8A8A8A]"> · {{ r.groupName }}</span>
          </p>
          <p class="truncate text-xs text-[#8A8A8A]">{{ r.message || '申请加入群聊' }}</p>
        </div>
        <span class="shrink-0 text-xs text-[#B2B2B2]">{{ formatTime(r.createdAt) }}</span>
        <div class="flex shrink-0 gap-2">
          <button
            type="button"
            class="cursor-pointer rounded-md bg-[#07C160] px-3 py-1 text-xs text-white transition hover:bg-[#06ad56]"
            @click="handleAction(r, true)"
          >
            同意
          </button>
          <button
            type="button"
            class="cursor-pointer rounded-md border border-[#E5E5E5] px-3 py-1 text-xs text-[#8A8A8A] transition hover:bg-[#F5F5F5]"
            @click="handleAction(r, false)"
          >
            拒绝
          </button>
        </div>
      </div>
    </div>
    <p v-else class="py-6 text-center text-sm text-[#B2B2B2]">暂无入群申请</p>
  </div>
</template>
