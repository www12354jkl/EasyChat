<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { inviteGroupMembers } from '@/api/chat'
import { getFriends, type FriendItem } from '@/api/user'
import { useToastStore } from '@/stores/toast'

const props = defineProps<{
  groupId: string
  /** 已在群内的成员 userId，展示为已勾选且不可选 */
  memberIds?: string[]
}>()
const emit = defineEmits<{ close: []; invited: [] }>()

const toast = useToastStore()

const friends = ref<FriendItem[]>([])
const selected = ref<Set<string>>(new Set())
const loading = ref(false)
const submitting = ref(false)

const memberIdSet = computed(() => new Set(props.memberIds ?? []))
const selectedCount = computed(() => selected.value.size)

function isInGroup(userId: string) {
  return memberIdSet.value.has(userId)
}

onMounted(async () => {
  loading.value = true
  try {
    friends.value = await getFriends()
  } finally {
    loading.value = false
  }
})

function toggle(id: string) {
  if (isInGroup(id)) return
  const next = new Set(selected.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selected.value = next
}

async function confirm() {
  if (!selectedCount.value) return
  submitting.value = true
  try {
    await inviteGroupMembers(props.groupId, Array.from(selected.value))
    toast.success(`已向 ${selectedCount.value} 位好友发送群邀请`)
    emit('invited')
    emit('close')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '邀请失败')
  } finally {
    submitting.value = false
  }
}

function avatarStyle(avatar: string): Record<string, string> {
  return { backgroundColor: avatar?.startsWith('#') ? avatar : '#07C160' }
}

function isImageAvatar(avatar: string): boolean {
  return !!avatar && !avatar.startsWith('#')
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
    @click.self="emit('close')"
  >
    <div class="flex max-h-[80vh] w-[420px] max-w-full flex-col rounded-lg bg-white shadow-xl">
      <div class="flex h-12 shrink-0 items-center justify-between border-b border-[#E5E5E5] px-4">
        <span class="text-base font-medium text-[#1A1A1A]">邀请成员</span>
        <button
          type="button"
          class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md text-[#8A8A8A] transition-colors hover:bg-[#F5F5F5] hover:text-[#1A1A1A]"
          @click="emit('close')"
        >
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-4">
        <p v-if="loading" class="py-6 text-center text-sm text-[#B2B2B2]">加载中...</p>
        <p v-else-if="!friends.length" class="py-6 text-center text-sm text-[#B2B2B2]">暂无可添加的好友</p>
        <div v-else class="space-y-1">
          <button
            v-for="f in friends"
            :key="f.userId"
            type="button"
            class="flex w-full items-center gap-3 rounded-md px-2 py-2 text-left transition-colors duration-150"
            :class="
              isInGroup(f.userId)
                ? 'cursor-not-allowed opacity-70'
                : selected.has(f.userId)
                  ? 'cursor-pointer bg-[#07C160]/10'
                  : 'cursor-pointer hover:bg-[#F5F5F5]'
            "
            :disabled="isInGroup(f.userId)"
            @click="toggle(f.userId)"
          >
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
              :style="avatarStyle(f.avatar)"
            >
              <img v-if="isImageAvatar(f.avatar)" :src="f.avatar" class="h-full w-full object-cover" alt="头像" />
              <span v-else>{{ f.nickname.charAt(0) }}</span>
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm text-[#1A1A1A]">
                {{ f.nickname }}
                <span v-if="isInGroup(f.userId)" class="text-xs text-[#8A8A8A]">（已在群内）</span>
              </p>
              <p class="truncate text-xs text-[#B2B2B2]">{{ f.signature || '暂无签名' }}</p>
            </div>
            <span
              class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border"
              :class="
                isInGroup(f.userId) || selected.has(f.userId)
                  ? 'border-[#07C160] bg-[#07C160] text-white'
                  : 'border-[#D6D6D6] text-transparent'
              "
            >
              <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </span>
          </button>
        </div>
      </div>

      <div class="flex shrink-0 gap-3 border-t border-[#E5E5E5] p-4">
        <button
          type="button"
          class="flex-1 cursor-pointer rounded-md border border-[#E5E5E5] py-2 text-sm text-[#8A8A8A] transition-colors hover:bg-[#F5F5F5]"
          @click="emit('close')"
        >
          取消
        </button>
        <button
          type="button"
          :disabled="!selectedCount || submitting"
          class="flex-1 cursor-pointer rounded-md bg-[#07C160] py-2 text-sm font-medium text-white transition-colors hover:bg-[#06ad56] disabled:cursor-not-allowed disabled:opacity-60"
          @click="confirm"
        >
          {{ submitting ? '发送中...' : `发送邀请（${selectedCount} 人）` }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
