<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Conversation } from '@/types/chat'

const props = defineProps<{
  conversations: Conversation[]
  activeId: string
}>()

const emit = defineEmits<{
  select: [id: string]
}>()

const keyword = ref('')
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return props.conversations
  return props.conversations.filter((c) => {
    return c.name.toLowerCase().includes(kw) || c.lastMessage.toLowerCase().includes(kw)
  })
})

function isImageAvatar(c: Conversation) {
  return !!c.avatar && !c.avatar.startsWith('#')
}
</script>

<template>
  <div class="flex h-full w-[260px] shrink-0 flex-col border-r border-[var(--ec-border)] bg-[var(--ec-bg)] xl:w-[300px]">
    <div class="border-b border-[var(--ec-border)] px-4 py-4">
      <h2 class="text-base font-semibold text-[var(--ec-text)]">消息</h2>
      <div class="mt-3 flex items-center gap-2 rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg-subtle)] px-3 py-2 transition-[border-color,box-shadow] duration-200 focus-within:border-[var(--ec-primary)] focus-within:ring-[3px] focus-within:ring-[var(--ec-primary-ring)]">
        <svg class="h-4 w-4 shrink-0 text-[var(--ec-text-faint)]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.3-4.3" />
        </svg>
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索会话"
          class="w-full bg-transparent py-0.5 text-sm text-[var(--ec-text)] outline-none placeholder:text-[var(--ec-text-faint)]"
        />
      </div>
    </div>

    <div class="ec-scroll flex-1 overflow-y-auto py-2">
      <button
        v-for="c in filtered"
        :key="c.id"
        type="button"
        class="group relative mx-2 flex w-[calc(100%-1rem)] cursor-pointer items-center gap-3 rounded-[var(--ec-radius)] px-3 py-2.5 text-left transition-colors duration-200"
        :class="
          c.id === activeId
            ? 'bg-[var(--ec-primary-soft)]'
            : 'hover:bg-[var(--ec-bg-muted)]'
        "
        @click="emit('select', c.id)"
      >
        <span
          v-if="c.id === activeId"
          class="absolute bottom-2 left-0 top-2 w-[3px] rounded-full bg-[var(--ec-primary)]"
          aria-hidden="true"
        />

        <div class="relative shrink-0">
          <div
            class="flex h-11 w-11 items-center justify-center overflow-hidden rounded-full text-sm font-medium text-white"
            :style="{ backgroundColor: c.avatarColor }"
          >
            <img v-if="isImageAvatar(c)" :src="c.avatar" class="h-full w-full object-cover" alt="头像" />
            <span v-else-if="c.isGroup">群</span>
            <span v-else>{{ c.name.charAt(0) }}</span>
          </div>
          <span
            v-if="c.unread > 0"
            class="absolute -right-0.5 -top-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[var(--ec-primary)] px-1 text-[10px] font-medium leading-none text-white"
          >
            {{ c.unread > 99 ? '99+' : c.unread }}
          </span>
        </div>

        <div class="min-w-0 flex-1">
          <div class="flex items-baseline justify-between gap-2">
            <span
              class="truncate text-[15px] font-medium"
              :class="c.id === activeId ? 'text-[var(--ec-text)]' : 'text-[var(--ec-text)]'"
            >
              {{ c.name }}
            </span>
            <span class="shrink-0 text-[11px] text-[var(--ec-text-faint)]">{{ c.lastTime }}</span>
          </div>
          <p
            class="mt-0.5 truncate text-[13px]"
            :class="c.unread > 0 ? 'font-medium text-[var(--ec-text-muted)]' : 'text-[var(--ec-text-faint)]'"
          >
            {{ c.lastMessage }}
          </p>
        </div>
      </button>

      <p
        v-if="!filtered.length"
        class="px-6 py-10 text-center text-sm text-[var(--ec-text-faint)]"
      >
        {{ keyword ? '没有匹配的会话' : '暂无会话' }}
      </p>
    </div>
  </div>
</template>
