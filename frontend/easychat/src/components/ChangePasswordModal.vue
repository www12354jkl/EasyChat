<script setup lang="ts">
import { computed, ref } from 'vue'
import { resetPassword, sendResetCode } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useEmailCode } from '@/composables/useEmailCode'
import {
  authAlertErrorClass,
  authInputClass,
  authPrimaryBtnClass,
  authGhostBtnClass,
} from '@/components/auth/auth-ui'

const emit = defineEmits<{
  close: []
}>()

const auth = useAuthStore()
const toast = useToastStore()

const { email, emailCode, sending, countdown, emailValid, errorMsg, sendCode } = useEmailCode(
  'forgot',
  () => sendResetCode(),
)
email.value = auth.user?.email ?? ''

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)

const sendBtnText = computed(() =>
  countdown.value > 0 ? `${countdown.value}s 后重发` : sending.value ? '发送中…' : '发送验证码',
)

async function handleSubmit() {
  if (!emailCode.value.trim()) {
    toast.error('请输入邮箱验证码')
    return
  }
  if (!oldPassword.value) {
    toast.error('请输入原密码')
    return
  }
  if (newPassword.value.length < 8 || newPassword.value.length > 20) {
    toast.error('新密码长度须在 8-20 位')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast.error('两次输入的新密码不一致')
    return
  }
  submitting.value = true
  try {
    await resetPassword({
      email: email.value,
      code: emailCode.value.trim(),
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    })
    toast.success('密码修改成功')
    emit('close')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '修改失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="ec-modal-overlay" @click.self="emit('close')">
      <div class="ec-modal-panel w-[380px] p-6">
        <h2 class="mb-6 text-center text-lg font-semibold text-[var(--ec-text)]">修改密码</h2>

        <form class="space-y-4" @submit.prevent="handleSubmit">
          <div class="flex items-center rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg-subtle)] px-3 py-2.5">
            <span class="w-16 shrink-0 text-xs text-[var(--ec-text-faint)]">邮箱</span>
            <span class="truncate text-sm text-[var(--ec-text-muted)]">{{ email }}</span>
          </div>

          <div class="flex gap-2">
            <input
              v-model="emailCode"
              type="text"
              placeholder="邮箱验证码"
              maxlength="6"
              autocomplete="one-time-code"
              :class="authInputClass"
            />
            <button
              type="button"
              :disabled="countdown > 0 || sending || !emailValid"
              :class="authGhostBtnClass + ' w-28 shrink-0 py-2.5'"
              @click="sendCode"
            >
              {{ sendBtnText }}
            </button>
          </div>

          <input
            v-model="oldPassword"
            type="password"
            placeholder="原密码"
            autocomplete="current-password"
            :class="authInputClass"
          />
          <input
            v-model="newPassword"
            type="password"
            placeholder="新密码（8-20 位）"
            autocomplete="new-password"
            :class="authInputClass"
          />
          <input
            v-model="confirmPassword"
            type="password"
            placeholder="确认新密码"
            autocomplete="new-password"
            :class="authInputClass"
          />

          <div v-if="errorMsg" :class="authAlertErrorClass">
            {{ errorMsg }}
          </div>

          <div class="flex gap-3 pt-2">
            <button type="button" class="ec-btn-secondary flex-1 py-2.5" @click="emit('close')">
              取消
            </button>
            <button type="submit" :disabled="submitting" :class="authPrimaryBtnClass + ' flex-1'">
              {{ submitting ? '提交中…' : '确认修改' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>
