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

// 本地搜索：按会话名过滤
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
  <div class="flex h-full w-[240px] shrink-0 flex-col bg-[#F7F7F7] xl:w-[280px]">
    <!-- 搜索框 -->
    <div class="px-3 pb-2 pt-3">
      <div class="flex items-center gap-2 rounded-md bg-white px-3 py-1.5">
        <svg class="h-4 w-4 shrink-0 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.3-4.3" stroke-linecap="round" />
        </svg>
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索"
          class="w-full bg-transparent py-0.5 text-sm outline-none placeholder:text-gray-400"
        />
      </div>
    </div>

    <!-- 会话列表 -->
    <div class="flex-1 overflow-y-auto pb-2">
      <button
        v-for="c in filtered"
        :key="c.id"
        type="button"
        class="flex w-full cursor-pointer items-center gap-3 px-3 py-2.5 text-left transition-colors duration-150"
        :class="c.id === activeId ? 'bg-[#D6D6D6]' : 'hover:bg-[#EBEBEB]'"
        @click="emit('select', c.id)"
      >
        <!-- 头像 -->
        <div class="relative shrink-0">
          <div
            class="flex h-10 w-10 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
            :style="{ backgroundColor: c.avatarColor }"
          >
            <img v-if="isImageAvatar(c)" :src="c.avatar" class="h-full w-full object-cover" alt="头像" />
            <span v-else-if="c.isGroup">{{ '群' }}</span>
            <span v-else>{{ c.name.charAt(0) }}</span>
          </div>
          <span
            v-if="c.unread > 0"
            class="absolute -right-1.5 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#FA5151] px-1 text-[10px] leading-none text-white"
          >
            {{ c.unread > 99 ? '99+' : c.unread }}
          </span>
        </div>

        <!-- 名称 + 最后消息 -->
        <div class="min-w-0 flex-1">
          <div class="flex items-baseline justify-between">
            <span class="truncate text-[15px] font-medium text-[#1A1A1A]">
              {{ c.name }}
            </span>
            <span class="ml-2 shrink-0 text-xs text-[#B2B2B2]">{{ c.lastTime }}</span>
          </div>
          <p class="mt-0.5 truncate text-[13px] text-[#8A8A8A]">{{ c.lastMessage }}</p>
        </div>
      </button>
    </div>
  </div>
</template>

<style scoped></style>
