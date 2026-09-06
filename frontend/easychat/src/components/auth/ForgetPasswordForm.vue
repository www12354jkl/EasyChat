<script setup lang="ts">
import { computed, ref } from 'vue'
import { forgetPassword } from '@/api/auth'
import { useEmailCode } from '@/composables/useEmailCode'
import AuthField from '@/components/auth/AuthField.vue'
import {
  authAlertErrorClass,
  authGhostBtnClass,
  authLabelClass,
  authLinkClass,
  authPrimaryBtnClass,
} from './auth-ui'

const emit = defineEmits<{
  switchToLogin: []
  resetSuccess: []
}>()

const { email, emailCode, sending, countdown, emailValid, errorMsg, sendCode } = useEmailCode('forgot')

const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)

const sendBtnText = computed(() =>
  countdown.value > 0 ? `${countdown.value}s 后重发` : sending.value ? '发送中…' : '发送验证码',
)

async function handleReset() {
  errorMsg.value = ''
  if (!emailValid.value) {
    errorMsg.value = '请输入正确的邮箱地址'
    return
  }
  if (!emailCode.value.trim()) {
    errorMsg.value = '请输入邮箱验证码'
    return
  }
  if (newPassword.value.length < 8 || newPassword.value.length > 20) {
    errorMsg.value = '密码长度须在 8-20 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    await forgetPassword({
      email: email.value,
      code: emailCode.value.trim(),
      newPassword: newPassword.value,
    })
    emit('resetSuccess')
  } catch (err) {
    errorMsg.value = err instanceof Error ? err.message : '重置失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="space-y-4" @submit.prevent="handleReset">
    <div v-if="errorMsg" :class="authAlertErrorClass">
      {{ errorMsg }}
    </div>

    <AuthField
      v-model="email"
      label="邮箱"
      type="email"
      placeholder="name@example.com"
      autocomplete="email"
    />

    <div>
      <label :class="authLabelClass">邮箱验证码</label>
      <div class="flex gap-2.5">
        <input
          v-model="emailCode"
          type="text"
          placeholder="6 位验证码"
          maxlength="6"
          autocomplete="one-time-code"
          class="auth-input w-full flex-1"
        />
        <button
          type="button"
          :disabled="countdown > 0 || sending"
          :class="[authGhostBtnClass, 'w-[7.5rem] shrink-0']"
          @click="sendCode"
        >
          {{ sendBtnText }}
        </button>
      </div>
    </div>

    <AuthField
      v-model="newPassword"
      label="新密码"
      type="password"
      placeholder="8-20 位"
      autocomplete="new-password"
    />

    <AuthField
      v-model="confirmPassword"
      label="确认新密码"
      type="password"
      placeholder="再次输入密码"
      autocomplete="new-password"
    />

    <button type="submit" :disabled="loading" :class="authPrimaryBtnClass">
      {{ loading ? '提交中…' : '重置密码' }}
    </button>

    <div class="border-t border-[var(--ec-border)] pt-4 text-center">
      <button type="button" :class="authLinkClass" @click="emit('switchToLogin')">
        返回登录
      </button>
    </div>
  </form>
</template>

<style scoped></style>
