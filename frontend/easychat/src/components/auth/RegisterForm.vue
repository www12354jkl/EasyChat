<script setup lang="ts">
import { computed, ref } from 'vue'
import { register } from '@/api/auth'
import { useEmailCode } from '@/composables/useEmailCode'
import Captcha from '@/components/Captcha.vue'
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
  registered: [email: string]
}>()

const { email, emailCode, sending, countdown, emailValid, errorMsg, sendCode } = useEmailCode('register')

const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const captchaInput = ref('')
const captchaRef = ref<InstanceType<typeof Captcha> | null>(null)
const loading = ref(false)

const sendBtnText = computed(() =>
  countdown.value > 0 ? `${countdown.value}s 后重发` : sending.value ? '发送中…' : '发送验证码',
)

async function handleRegister() {
  errorMsg.value = ''
  if (!emailValid.value) {
    errorMsg.value = '请输入正确的邮箱地址'
    return
  }
  if (!emailCode.value.trim()) {
    errorMsg.value = '请输入邮箱验证码'
    return
  }
  if (!nickname.value.trim()) {
    errorMsg.value = '请输入昵称'
    return
  }
  if (password.value.length < 8 || password.value.length > 20) {
    errorMsg.value = '密码长度须在 8-20 位'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }

  const expected = captchaRef.value?.code
  if (!expected || captchaInput.value.trim().toUpperCase() !== expected) {
    errorMsg.value = '验证码错误'
    captchaRef.value?.refresh()
    return
  }

  loading.value = true
  try {
    await register({
      email: email.value,
      nickname: nickname.value.trim(),
      password: password.value,
      emailCode: emailCode.value.trim(),
    })
    emit('registered', email.value)
  } catch (err) {
    errorMsg.value = err instanceof Error ? err.message : '注册失败，请稍后重试'
    captchaRef.value?.refresh()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="space-y-4" @submit.prevent="handleRegister">
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
      v-model="nickname"
      label="昵称"
      type="text"
      placeholder="你的显示名称"
      maxlength="20"
    />

    <AuthField
      v-model="password"
      label="密码"
      type="password"
      placeholder="8-20 位"
      autocomplete="new-password"
    />

    <AuthField
      v-model="confirmPassword"
      label="确认密码"
      type="password"
      placeholder="再次输入密码"
      autocomplete="new-password"
    />

    <div>
      <label :class="authLabelClass">图形验证码</label>
      <div class="flex gap-2.5">
        <input
          v-model="captchaInput"
          type="text"
          placeholder="输入右侧字符"
          maxlength="4"
          autocomplete="off"
          class="auth-input w-full flex-1 uppercase tracking-wider placeholder:normal-case placeholder:tracking-normal"
        />
        <Captcha ref="captchaRef" class="h-[42px] shrink-0 overflow-hidden rounded-[var(--ec-radius)]" />
      </div>
    </div>

    <button type="submit" :disabled="loading" :class="authPrimaryBtnClass">
      {{ loading ? '注册中…' : '注册' }}
    </button>

    <div class="border-t border-[var(--ec-border)] pt-4 text-center">
      <button type="button" :class="authLinkClass" @click="emit('switchToLogin')">
        已有账号？去登录
      </button>
    </div>
  </form>
</template>

<style scoped></style>
