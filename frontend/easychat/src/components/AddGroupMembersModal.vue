<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { inviteGroupMembers } from '@/api/chat'
import { getFriends, type FriendItem } from '@/api/user'
import { useToastStore } from '@/stores/toast'
import { ecAvatarStyle } from '@/utils/ec-ui'
import { authPrimaryBtnClass } from '@/components/auth/auth-ui'

const props = defineProps<{
  groupId: string
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

function isImageAvatar(avatar: string): boolean {
  return !!avatar && !avatar.startsWith('#')
}
</script>

<template>
  <div class="ec-modal-overlay" @click.self="emit('close')">
    <div class="ec-modal-panel flex max-h-[80vh] w-[420px] max-w-full flex-col">
      <div class="flex h-12 shrink-0 items-center justify-between border-b border-[var(--ec-border)] px-4">
        <span class="text-base font-semibold text-[var(--ec-text)]">邀请成员</span>
        <button type="button" class="ec-icon-btn" @click="emit('close')">
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-4">
        <p v-if="loading" class="py-6 text-center text-sm text-[var(--ec-text-faint)]">加载中...</p>
        <p v-else-if="!friends.length" class="py-6 text-center text-sm text-[var(--ec-text-faint)]">暂无可添加的好友</p>
        <div v-else class="space-y-1">
          <button
            v-for="f in friends"
            :key="f.userId"
            type="button"
            class="flex w-full items-center gap-3 rounded-[var(--ec-radius)] px-2 py-2 text-left transition-colors duration-200"
            :class="
              isInGroup(f.userId)
                ? 'cursor-not-allowed opacity-70'
                : selected.has(f.userId)
                  ? 'cursor-pointer bg-[var(--ec-primary-soft)]'
                  : 'cursor-pointer hover:bg-[var(--ec-bg-muted)]'
            "
            :disabled="isInGroup(f.userId)"
            @click="toggle(f.userId)"
          >
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-medium text-white"
              :style="ecAvatarStyle(f.avatar)"
            >
              <img v-if="isImageAvatar(f.avatar)" :src="f.avatar" class="h-full w-full object-cover" alt="头像" />
              <span v-else>{{ f.nickname.charAt(0) }}</span>
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm text-[var(--ec-text)]">
                {{ f.nickname }}
                <span v-if="isInGroup(f.userId)" class="text-xs text-[var(--ec-text-muted)]">（已在群内）</span>
              </p>
              <p class="truncate text-xs text-[var(--ec-text-faint)]">{{ f.signature || '暂无签名' }}</p>
            </div>
            <span
              class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border"
              :class="
                isInGroup(f.userId) || selected.has(f.userId)
                  ? 'border-[var(--ec-primary)] bg-[var(--ec-primary)] text-white'
                  : 'border-[var(--ec-border)] text-transparent'
              "
            >
              <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </span>
          </button>
        </div>
      </div>

      <div class="flex shrink-0 gap-3 border-t border-[var(--ec-border)] p-4">
        <button type="button" class="ec-btn-secondary flex-1 py-2" @click="emit('close')">
          取消
        </button>
        <button
          type="button"
          :disabled="!selectedCount || submitting"
          :class="authPrimaryBtnClass + ' flex-1'"
          @click="confirm"
        >
          {{ submitting ? '发送中...' : `发送邀请（${selectedCount} 人）` }}
        </button>
      </div>
    </div>
  </div>
</template>
