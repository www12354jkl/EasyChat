<script setup lang="ts">
import { computed, ref } from 'vue'
import { register } from '@/api/auth'
import { useEmailCode } from '@/composables/useEmailCode'
import Captcha from '@/components/Captcha.vue'

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
    <!-- 错误提示 -->
    <div v-if="errorMsg" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-500">
      {{ errorMsg }}
    </div>

    <input
      v-model="email"
      type="email"
      placeholder="邮箱"
      autocomplete="email"
      class="w-full rounded-md border border-gray-300 px-3 py-2.5 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
    />

    <!-- 邮箱验证码 -->
    <div class="flex gap-2">
      <input
        v-model="emailCode"
        type="text"
        placeholder="邮箱验证码"
        maxlength="6"
        autocomplete="one-time-code"
        class="w-full flex-1 rounded-md border border-gray-300 px-3 py-2.5 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
      />
      <button
        type="button"
        :disabled="countdown > 0 || sending"
        @click="sendCode"
        class="w-28 shrink-0 rounded-md border border-[#07C160] py-2.5 text-sm text-[#07C160] transition hover:bg-[#07C160]/5 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400"
      >
        {{ sendBtnText }}
      </button>
    </div>

    <input
      v-model="nickname"
      type="text"
      placeholder="昵称"
      maxlength="20"
      class="w-full rounded-md border border-gray-300 px-3 py-2.5 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
    />
    <input
      v-model="password"
      type="password"
      placeholder="密码（8-20 位）"
      autocomplete="new-password"
      class="w-full rounded-md border border-gray-300 px-3 py-2.5 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
    />
    <input
      v-model="confirmPassword"
      type="password"
      placeholder="确认密码"
      autocomplete="new-password"
      class="w-full rounded-md border border-gray-300 px-3 py-2.5 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
    />

    <!-- 验证码 -->
    <div class="flex gap-2">
      <input
        v-model="captchaInput"
        type="text"
        placeholder="验证码"
        maxlength="4"
        autocomplete="off"
        class="w-full flex-1 rounded-md border border-gray-300 px-3 py-2.5 text-sm uppercase outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
      />
      <Captcha ref="captchaRef" class="h-10 shrink-0" />
    </div>

    <button
      type="submit"
      :disabled="loading"
      class="w-full rounded-md bg-[#07C160] py-2.5 text-sm font-medium text-white transition hover:bg-[#06ad56] disabled:cursor-not-allowed disabled:opacity-60"
    >
      {{ loading ? '注册中…' : '注册' }}
    </button>

    <!-- 底部链接 -->
    <div class="pt-2 text-center text-xs text-gray-500">
      <button type="button" class="hover:text-[#07C160]" @click="emit('switchToLogin')">
        已有账号？去登录
      </button>
    </div>
  </form>
</template>

<style scoped></style>
