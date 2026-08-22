<script setup lang="ts">
import { computed, ref } from 'vue'
import { forgetPassword } from '@/api/auth'
import { useEmailCode } from '@/composables/useEmailCode'

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
      v-model="newPassword"
      type="password"
      placeholder="新密码（8-20 位）"
      autocomplete="new-password"
      class="w-full rounded-md border border-gray-300 px-3 py-2.5 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
    />
    <input
      v-model="confirmPassword"
      type="password"
      placeholder="确认新密码"
      autocomplete="new-password"
      class="w-full rounded-md border border-gray-300 px-3 py-2.5 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
    />

    <button
      type="submit"
      :disabled="loading"
      class="w-full rounded-md bg-[#07C160] py-2.5 text-sm font-medium text-white transition hover:bg-[#06ad56] disabled:cursor-not-allowed disabled:opacity-60"
    >
      {{ loading ? '提交中…' : '重置密码' }}
    </button>

    <!-- 底部链接 -->
    <div class="pt-2 text-center text-xs text-gray-500">
      <button type="button" class="hover:text-[#07C160]" @click="emit('switchToLogin')">
        返回登录
      </button>
    </div>
  </form>
</template>

<style scoped></style>
