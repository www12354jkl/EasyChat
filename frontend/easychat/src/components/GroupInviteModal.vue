<script setup lang="ts">
import { computed, ref } from 'vue'
import { acceptGroupInvite, declineGroupInvite } from '@/api/chat'
import { useToastStore } from '@/stores/toast'
import type { GroupInviteInfo } from '@/types/chat'
import { ecAvatarStyle } from '@/utils/ec-ui'

const props = defineProps<{
  invite: GroupInviteInfo
}>()

const emit = defineEmits<{
  close: []
  joined: [groupConversationId: string]
  waiting: []
  declined: []
}>()

const toast = useToastStore()
const message = ref('')
const submitting = ref(false)

const needVerify = computed(() => props.invite.joinVerify === 1)
const isWaiting = computed(() => props.invite.inviteStatus === 'waiting')
const isExpired = computed(
  () => props.invite.inviteStatus === 'expired' || props.invite.inviteStatus === 'joined',
)

function isImageAvatar(url?: string) {
  return !!url && !url.startsWith('#')
}

async function submitJoin() {
  if (!props.invite.inviteId || submitting.value || isWaiting.value || isExpired.value) return
  submitting.value = true
  try {
    const { status, groupConversationId } = await acceptGroupInvite(
      props.invite.inviteId,
      message.value.trim(),
    )
    if (status === 'joined' && groupConversationId) {
      toast.success(`已加入「${props.invite.name}」`)
      emit('joined', groupConversationId)
      emit('close')
    } else {
      toast.success('已提交入群申请，等待管理员审批')
      emit('waiting')
      emit('close')
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    submitting.value = false
  }
}

async function submitDecline() {
  if (!props.invite.inviteId || submitting.value) return
  submitting.value = true
  try {
    await declineGroupInvite(props.invite.inviteId)
    toast.success('已忽略该群邀请')
    emit('declined')
    emit('close')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="ec-modal-overlay" @click.self="emit('close')">
    <div class="ec-modal-panel w-[400px] max-w-full">
      <div class="flex flex-col items-center px-6 pb-4 pt-8">
        <div
          class="flex h-16 w-16 items-center justify-center overflow-hidden rounded-full text-xl font-medium text-white"
          :style="ecAvatarStyle(invite.avatar)"
        >
          <img
            v-if="isImageAvatar(invite.avatar)"
            :src="invite.avatar"
            class="h-full w-full object-cover"
            alt="群头像"
          />
          <span v-else>群</span>
        </div>
        <h2 class="mt-4 text-lg font-semibold text-[var(--ec-text)]">{{ invite.name }}</h2>
        <p v-if="invite.inviterName" class="mt-2 text-sm text-[var(--ec-text-muted)]">
          {{ invite.inviterName }} 邀请你加入群聊
        </p>
        <p v-if="isWaiting" class="mt-3 text-sm text-[var(--ec-primary)]">已提交验证信息，等待管理员审批</p>
        <p v-else-if="isExpired" class="mt-3 text-sm text-[var(--ec-text-faint)]">该邀请已失效</p>
      </div>

      <div v-if="!isWaiting && !isExpired && needVerify" class="px-6 pb-4">
        <label class="mb-2 block text-sm font-medium text-[var(--ec-text)]">验证信息</label>
        <textarea
          v-model="message"
          rows="3"
          maxlength="100"
          placeholder="介绍一下自己，或说明入群原因"
          class="ec-input resize-none"
        />
        <p class="mt-1 text-right text-xs text-[var(--ec-text-faint)]">{{ message.length }}/100</p>
      </div>

      <div class="flex border-t border-[var(--ec-border)]">
        <button
          v-if="!isWaiting && !isExpired"
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm text-[var(--ec-text-muted)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)] disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="submitting"
          @click="submitDecline"
        >
          忽略
        </button>
        <span v-if="!isWaiting && !isExpired" class="w-px bg-[var(--ec-border)]" />
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm font-medium transition-colors duration-200 hover:bg-[var(--ec-bg-muted)] disabled:cursor-not-allowed disabled:opacity-50"
          :class="isWaiting || isExpired ? 'text-[var(--ec-text-faint)]' : 'text-[var(--ec-primary)]'"
          :disabled="submitting || isWaiting || isExpired"
          @click="isWaiting || isExpired ? emit('close') : submitJoin()"
        >
          {{
            submitting
              ? '处理中...'
              : isWaiting
                ? '知道了'
                : isExpired
                  ? '关闭'
                  : needVerify
                    ? '发送申请'
                    : '加入群聊'
          }}
        </button>
      </div>
    </div>
  </div>
</template>
