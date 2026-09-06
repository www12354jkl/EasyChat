<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { createGroup, uploadGroupAvatar } from '@/api/chat'
import { getFriends, type FriendItem } from '@/api/user'
import { useToastStore } from '@/stores/toast'
import { ecAvatarStyle } from '@/utils/ec-ui'
import { authInputClass, authPrimaryBtnClass } from '@/components/auth/auth-ui'

const emit = defineEmits<{
  close: []
  created: [group: { id: string; name: string; avatar: string; announcement: string; joinVerify: number; memberIds: string[] }]
}>()

const toast = useToastStore()

type SelectableFriend = FriendItem & { selected: boolean }

// ============ 步骤 ============
const step = ref<1 | 2>(1)

// ============ 步骤 1：基本信息 ============
const groupName = ref('')
const avatarColor = ref('#07C160')
const announcement = ref('')
const joinVerify = ref(1)
const avatarFile = ref<File | null>(null)
const avatarPreview = ref('')
const avatarInput = ref<HTMLInputElement | null>(null)

function triggerAvatarSelect() {
  avatarInput.value?.click()
}

const avatarColors = ['#07C160', '#5B8FF9', '#F6BD16', '#E8684A', '#8A8A8A', '#9B59B6']

function pickColor(c: string) {
  avatarColor.value = c
  avatarPreview.value = ''
}

function handleAvatarFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) return
  if (file.size > 2 * 1024 * 1024) return
  avatarFile.value = file
  avatarPreview.value = URL.createObjectURL(file)
}

function canNext1() {
  return groupName.value.trim().length > 0 && groupName.value.trim().length <= 20
}

// ============ 步骤 2：拉人进群 ============
const friends = ref<SelectableFriend[]>([])
const friendsLoading = ref(false)
const submitting = ref(false)
const selectedCount = computed(() => friends.value.filter((f) => f.selected).length)

async function loadFriends() {
  friendsLoading.value = true
  try {
    friends.value = (await getFriends()).map((f) => ({ ...f, selected: false }))
  } catch {
    friends.value = []
    toast.error('好友列表加载失败')
  } finally {
    friendsLoading.value = false
  }
}

watch(step, (s) => {
  if (s === 2) loadFriends()
})

function toggleSelect(userId: string) {
  friends.value = friends.value.map((f) =>
    f.userId === userId ? { ...f, selected: !f.selected } : f,
  )
}

function avatarStyle(avatar: string): Record<string, string> {
  return ecAvatarStyle(avatar)
}

function isImageAvatar(avatar: string): boolean {
  return !!avatar && !avatar.startsWith('#')
}

async function handleCreate() {
  if (submitting.value) return
  submitting.value = true
  try {
    const memberIds = friends.value.filter((f) => f.selected).map((f) => f.userId)
    const group = await createGroup({
      name: groupName.value.trim(),
      avatar: avatarFile.value ? '' : avatarColor.value,
      announcement: announcement.value.trim(),
      joinVerify: joinVerify.value,
      memberIds,
    })

    if (avatarFile.value) {
      try {
        await uploadGroupAvatar(group.id, avatarFile.value)
      } catch {
        toast.error('群已创建，但头像上传失败')
      }
    }

    toast.success('群聊创建成功')
    emit('created', {
      id: group.id,
      name: group.name,
      avatar: group.avatar,
      announcement: group.announcement,
      joinVerify: group.joinVerify,
      memberIds,
    })
    emit('close')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '创建失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="ec-modal-overlay" @click.self="emit('close')">
      <div class="ec-modal-panel w-[480px]">
        <div class="flex items-center justify-between border-b border-[var(--ec-border)] px-6 py-4">
          <h2 class="text-base font-semibold text-[var(--ec-text)]">
            {{ step === 1 ? '创建群聊' : '选择成员' }}
          </h2>
          <button
            type="button"
            class="ec-icon-btn"
            @click="emit('close')"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div v-if="step === 1" class="p-6">
          <div class="mb-6 flex flex-col items-center gap-2">
            <div
              class="flex h-20 w-20 cursor-pointer items-center justify-center overflow-hidden rounded-xl text-3xl font-medium text-white transition hover:opacity-80"
              :style="{ backgroundColor: avatarColor }"
              @click="triggerAvatarSelect"
            >
              <img v-if="avatarPreview" :src="avatarPreview" class="h-full w-full object-cover" alt="群头像" />
              <span v-else>群</span>
            </div>
            <span class="text-xs text-[var(--ec-text-muted)]">点击设置群头像</span>
            <input
              ref="avatarInput"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              class="hidden"
              @change="handleAvatarFile"
            />
            <div v-if="!avatarPreview" class="mt-1 flex gap-2">
              <button
                v-for="c in avatarColors"
                :key="c"
                type="button"
                class="h-6 w-6 cursor-pointer rounded-full transition hover:scale-110"
                :style="{ backgroundColor: c }"
                :class="avatarColor === c ? 'ring-2 ring-[var(--ec-primary)] ring-offset-2' : ''"
                @click="pickColor(c)"
              />
            </div>
          </div>

          <div class="space-y-4">
            <div>
              <label class="mb-1 block text-sm text-[var(--ec-text-muted)]" for="group-name">群名称</label>
              <input
                id="group-name"
                v-model="groupName"
                type="text"
                maxlength="20"
                placeholder="给群聊起个名字"
                :class="authInputClass"
              />
            </div>

            <div>
              <label class="mb-1 block text-sm text-[var(--ec-text-muted)]" for="group-announcement">群公告（可选）</label>
              <textarea
                id="group-announcement"
                v-model="announcement"
                rows="2"
                maxlength="200"
                placeholder="写点什么告诉大家"
                class="ec-input resize-none"
              />
            </div>

            <div>
              <span class="mb-1 block text-sm text-[var(--ec-text-muted)]">入群验证方式</span>
              <div class="space-y-2">
                <label class="flex cursor-pointer items-center gap-2.5 rounded-[var(--ec-radius)] border border-[var(--ec-border)] px-3 py-2.5 transition hover:bg-[var(--ec-bg-subtle)]" :class="joinVerify === 1 ? 'border-[var(--ec-primary)] bg-[var(--ec-primary-soft)]' : ''">
                  <input v-model="joinVerify" type="radio" name="join-verify" :value="1" class="h-4 w-4 accent-[var(--ec-primary)]" />
                  <span class="flex-1">
                    <span class="block text-sm text-[var(--ec-text)]">需要管理员同意</span>
                    <span class="block text-xs text-[var(--ec-text-faint)]">群外的人申请入群时，需群主或管理员批准</span>
                  </span>
                </label>
                <label class="flex cursor-pointer items-center gap-2.5 rounded-[var(--ec-radius)] border border-[var(--ec-border)] px-3 py-2.5 transition hover:bg-[var(--ec-bg-subtle)]" :class="joinVerify === 0 ? 'border-[var(--ec-primary)] bg-[var(--ec-primary-soft)]' : ''">
                  <input v-model="joinVerify" type="radio" name="join-verify" :value="0" class="h-4 w-4 accent-[var(--ec-primary)]" />
                  <span class="flex-1">
                    <span class="block text-sm text-[var(--ec-text)]">允许直接加入</span>
                    <span class="block text-xs text-[var(--ec-text-faint)]">任何人无需批准即可进群</span>
                  </span>
                </label>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="p-6">
          <p class="mb-4 text-sm text-[var(--ec-text-muted)]">邀请好友进群（已选择 {{ selectedCount }} 人）</p>
          <p v-if="friendsLoading" class="py-6 text-center text-sm text-[var(--ec-text-faint)]">加载中...</p>
          <p v-else-if="!friends.length" class="py-6 text-center text-sm text-[var(--ec-text-faint)]">暂无好友可邀请</p>
          <div v-else class="max-h-[320px] space-y-1 overflow-y-auto">
            <button
              v-for="f in friends"
              :key="f.userId"
              type="button"
              class="flex w-full cursor-pointer items-center gap-3 rounded-md px-2 py-2 text-left transition-colors duration-150"
              :class="f.selected ? 'bg-[var(--ec-primary-soft)]' : 'hover:bg-[var(--ec-bg-muted)]'"
              @click="toggleSelect(f.userId)"
            >
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
                :style="avatarStyle(f.avatar)"
              >
                <img v-if="isImageAvatar(f.avatar)" :src="f.avatar" class="h-full w-full object-cover" alt="头像" />
                <span v-else>{{ f.nickname.charAt(0) }}</span>
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm text-[var(--ec-text)]">{{ f.nickname }}</p>
                <p class="truncate text-xs text-[var(--ec-text-faint)]">{{ f.signature || '暂无签名' }}</p>
              </div>
              <span
                class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border transition"
                :class="f.selected ? 'border-[var(--ec-primary)] bg-[var(--ec-primary)] text-white' : 'border-[var(--ec-border)] text-transparent'"
              >
                <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20 6 9 17l-5-5" />
                </svg>
              </span>
            </button>
          </div>
        </div>

        <div class="flex gap-3 border-t border-[var(--ec-border)] px-6 py-4">
          <button
            v-if="step === 2"
            type="button"
            class="ec-btn-secondary flex-1 py-2.5"
            @click="step = 1"
          >
            上一步
          </button>
          <button
            v-if="step === 1"
            type="button"
            class="ec-btn-secondary flex-1 py-2.5"
            @click="emit('close')"
          >
            取消
          </button>
          <button
            v-if="step === 1"
            type="button"
            :disabled="!canNext1()"
            :class="authPrimaryBtnClass + ' flex-1'"
            @click="step = 2"
          >
            下一步
          </button>
          <button
            v-else
            type="button"
            :disabled="submitting"
            :class="authPrimaryBtnClass + ' flex-1'"
            @click="handleCreate"
          >
            {{ submitting ? '创建中…' : `创建（${selectedCount} 人）` }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped></style>
