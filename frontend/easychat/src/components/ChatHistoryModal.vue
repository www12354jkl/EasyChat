<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { getMessagePage, type MessageRecord } from '@/api/chat'
import { formatMessageTime } from '@/utils/messageTime'
import { useNowTicker } from '@/composables/useNowTicker'
import { useAuthStore } from '@/stores/auth'
import { inviteCardLabel, parseInviteContent } from '@/utils/groupInviteMessage'

const MESSAGE_STATUS_RECALLED = 2

const props = defineProps<{ conversationId: string; name?: string }>()
const emit = defineEmits<{ close: [] }>()

const auth = useAuthStore()

const keyword = ref('')
const records = ref<MessageRecord[]>([])
const page = ref(1)
const size = 30
const total = ref(0)
const loading = ref(false)
const listRef = ref<HTMLElement | null>(null)
const now = useNowTicker()

const hasMore = computed(() => records.value.length < total.value)

function isRecalled(m: MessageRecord) {
  return m.status === MESSAGE_STATUS_RECALLED
}

function isSender(m: MessageRecord) {
  return m.senderId === auth.user?.userId
}

function inviteInfo(m: MessageRecord) {
  if (m.contentType !== 4) return null
  return parseInviteContent(m.content)
}

function inviteLabel(m: MessageRecord) {
  return inviteCardLabel(inviteInfo(m), { isSender: isSender(m) })
}

function fileInfo(m: MessageRecord) {
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

async function load(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    const p = reset ? 1 : page.value + 1
    const data = await getMessagePage(props.conversationId, p, size, keyword.value.trim())
    page.value = p
    total.value = data.total
    const list = data.records.slice().reverse()
    records.value = reset ? list : [...list, ...records.value]
    if (reset) {
      await nextTick()
      listRef.value?.scrollTo({ top: listRef.value.scrollHeight })
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load(true)
}

function onScroll() {
  const el = listRef.value
  if (el && el.scrollTop <= 20 && hasMore.value) {
    load(false)
  }
}

onMounted(() => load(true))
</script>

<template>
  <div class="ec-modal-overlay" @click.self="emit('close')">
    <div class="ec-modal-panel flex h-[560px] w-[480px] max-w-full flex-col">
      <div class="flex h-12 shrink-0 items-center justify-between border-b border-[var(--ec-border)] px-4">
        <h2 class="truncate text-base font-semibold text-[var(--ec-text)]">{{ props.name ?? '聊天记录' }}</h2>
        <button type="button" class="ec-icon-btn" title="关闭" @click="emit('close')">
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>

      <div class="shrink-0 border-b border-[var(--ec-border)] px-4 py-2">
        <div class="ec-search-box">
          <svg class="h-4 w-4 shrink-0 text-[var(--ec-text-faint)]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" stroke-linecap="round" />
          </svg>
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索消息"
            class="w-full bg-transparent py-0.5 text-sm text-[var(--ec-text)] outline-none placeholder:text-[var(--ec-text-faint)]"
            @keyup.enter="onSearch"
          />
          <button
            type="button"
            class="shrink-0 cursor-pointer text-sm font-medium text-[var(--ec-primary)]"
            @click="onSearch"
          >
            搜索
          </button>
        </div>
      </div>

      <div ref="listRef" class="ec-scroll flex-1 overflow-y-auto px-4 py-3" @scroll="onScroll">
        <p v-if="loading" class="py-2 text-center text-xs text-[var(--ec-text-faint)]">加载中...</p>
        <div v-for="m in records" :key="m.id" class="py-1.5">
          <div class="flex items-baseline gap-2 text-xs text-[var(--ec-text-faint)]">
            <span class="truncate text-[var(--ec-text-muted)]">{{ m.senderName || m.senderId }}</span>
            <span>{{ formatMessageTime(m.createdAt ?? null, now) }}</span>
          </div>
          <p v-if="isRecalled(m)" class="mt-0.5 text-sm text-[var(--ec-text-faint)]">[已撤回]</p>
          <img
            v-else-if="m.contentType === 2"
            :src="m.content"
            class="my-1 max-h-[160px] max-w-[240px] rounded-[var(--ec-radius)] object-cover"
            alt="图片"
          />
          <a
            v-else-if="m.contentType === 3"
            :href="fileInfo(m)?.url"
            target="_blank"
            rel="noopener"
            class="mt-0.5 flex items-center gap-2 rounded-[var(--ec-radius)] bg-[var(--ec-bg-subtle)] px-3 py-2 text-sm text-[var(--ec-primary)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)]"
          >
            <svg class="h-5 w-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <path d="M14 2v6h6" />
            </svg>
            <span class="min-w-0 flex-1 truncate">{{ fileInfo(m)?.name || m.content }}</span>
            <span class="shrink-0 text-xs text-[var(--ec-text-faint)]">{{ formatSize(fileInfo(m)?.size ?? 0) }}</span>
          </a>
          <div
            v-else-if="m.contentType === 4 && inviteInfo(m)"
            class="mt-0.5 w-[220px] rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] px-3 py-2.5"
          >
            <p class="text-xs text-[var(--ec-primary)]">群聊邀请</p>
            <p class="mt-1 truncate text-sm font-medium text-[var(--ec-text)]">{{ inviteInfo(m)?.groupName }}</p>
            <p class="mt-1 text-xs text-[var(--ec-text-faint)]">{{ inviteLabel(m) }}</p>
          </div>
          <p v-else-if="m.contentType === 4" class="mt-0.5 text-sm text-[var(--ec-text-muted)]">[群邀请]</p>
          <p v-else class="mt-0.5 whitespace-pre-wrap break-words text-sm text-[var(--ec-text)]">{{ m.content }}</p>
        </div>
        <p v-if="!records.length && !loading" class="py-10 text-center text-sm text-[var(--ec-text-faint)]">暂无消息</p>
      </div>
    </div>
  </div>
</template>
