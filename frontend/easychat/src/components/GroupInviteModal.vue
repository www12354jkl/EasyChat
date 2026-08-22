<script setup lang="ts">
import { computed, ref } from 'vue'
import { acceptGroupInvite, declineGroupInvite } from '@/api/chat'
import { useToastStore } from '@/stores/toast'
import type { GroupInviteInfo } from '@/types/chat'

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

function avatarStyle(url?: string): Record<string, string> {
  return { backgroundColor: url?.startsWith('#') ? url : '#07C160' }
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
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="emit('close')">
    <div class="w-[400px] max-w-full overflow-hidden rounded-lg bg-white shadow-xl">
      <div class="flex flex-col items-center px-6 pb-4 pt-8">
        <div
          class="flex h-16 w-16 items-center justify-center overflow-hidden rounded-[4px] text-xl font-medium text-white"
          :style="avatarStyle(invite.avatar)"
        >
          <img
            v-if="isImageAvatar(invite.avatar)"
            :src="invite.avatar"
            class="h-full w-full object-cover"
            alt="群头像"
          />
          <span v-else>群</span>
        </div>
        <h2 class="mt-4 text-lg font-medium text-[#1A1A1A]">{{ invite.name }}</h2>
        <p v-if="invite.inviterName" class="mt-2 text-sm text-[#8A8A8A]">
          {{ invite.inviterName }} 邀请你加入群聊
        </p>
        <p v-if="isWaiting" class="mt-3 text-sm text-[#576B95]">已提交验证信息，等待管理员审批</p>
        <p v-else-if="isExpired" class="mt-3 text-sm text-[#B2B2B2]">该邀请已失效</p>
      </div>

      <div v-if="!isWaiting && !isExpired && needVerify" class="px-6 pb-4">
        <label class="mb-2 block text-sm text-[#1A1A1A]">验证信息</label>
        <textarea
          v-model="message"
          rows="3"
          maxlength="100"
          placeholder="介绍一下自己，或说明入群原因"
          class="w-full resize-none rounded-md border border-[#E5E5E5] px-3 py-2 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
        />
        <p class="mt-1 text-right text-xs text-[#B2B2B2]">{{ message.length }}/100</p>
      </div>

      <div class="flex border-t border-[#F0F0F0]">
        <button
          v-if="!isWaiting && !isExpired"
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm text-[#8A8A8A] transition-colors hover:bg-[#F5F5F5] disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="submitting"
          @click="submitDecline"
        >
          忽略
        </button>
        <span v-if="!isWaiting && !isExpired" class="w-px bg-[#F0F0F0]"></span>
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm font-medium transition-colors hover:bg-[#F5F5F5] disabled:cursor-not-allowed disabled:opacity-50"
          :class="isWaiting || isExpired ? 'text-[#8A8A8A]' : 'text-[#07C160]'"
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
