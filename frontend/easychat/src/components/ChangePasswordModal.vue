<script setup lang="ts">
import { computed, ref } from 'vue'
import { resetPassword, sendResetCode } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useEmailCode } from '@/composables/useEmailCode'

const emit = defineEmits<{
  close: []
}>()

const auth = useAuthStore()
const toast = useToastStore()

// 改密码发码走登录态接口 /api/user/send-reset-code（自定义 sendFn 覆盖默认）
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
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/30 px-4" @click.self="emit('close')">
      <div class="w-[380px] rounded-xl bg-white p-8 shadow-xl">
        <h2 class="mb-6 text-center text-lg font-semibold text-[#1A1A1A]">修改密码</h2>

        <form class="space-y-4" @submit.prevent="handleSubmit">
          <!-- 当前邮箱（只读） -->
          <div class="flex items-center rounded-md border border-gray-200 bg-gray-50 px-3 py-2.5">
            <span class="w-16 shrink-0 text-xs text-gray-400">邮箱</span>
            <span class="truncate text-sm text-gray-500">{{ email }}</span>
          </div>

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
              :disabled="countdown > 0 || sending || !emailValid"
              @click="sendCode"
              class="w-28 shrink-0 cursor-pointer rounded-md border border-[#07C160] py-2.5 text-sm text-[#07C160] transition hover:bg-[#07C160]/5 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400"
            >
              {{ sendBtnText }}
            </button>
          </div>

          <input
            v-model="oldPassword"
            type="password"
            placeholder="原密码"
            autocomplete="current-password"
            class="w-full rounded-md border border-gray-300 px-3 py-2.5 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
          />
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

          <!-- 发送失败的错误提示 -->
          <div v-if="errorMsg" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-500">
            {{ errorMsg }}
          </div>

          <div class="flex gap-3 pt-2">
            <button
              type="button"
              class="flex-1 cursor-pointer rounded-md border border-gray-300 py-2.5 text-sm text-gray-500 transition hover:bg-gray-50"
              @click="emit('close')"
            >
              取消
            </button>
            <button
              type="submit"
              :disabled="submitting"
              class="flex-1 cursor-pointer rounded-md bg-[#07C160] py-2.5 text-sm font-medium text-white transition hover:bg-[#06ad56] disabled:opacity-60"
            >
              {{ submitting ? '提交中…' : '确认修改' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<style scoped></style>
