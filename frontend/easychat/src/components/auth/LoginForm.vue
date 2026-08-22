<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import Captcha from '@/components/Captcha.vue'

const emit = defineEmits<{
  switchToRegister: []
  switchToForgot: []
}>()

defineProps<{
  notice?: string
}>()

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const captchaInput = ref('')
const captchaRef = ref<InstanceType<typeof Captcha> | null>(null)
const loading = ref(false)
const errorMsg = ref('')

const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value))

async function handleLogin() {
  errorMsg.value = ''
  if (!emailValid.value) {
    errorMsg.value = '请输入正确的邮箱地址'
    return
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
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
    const result = await login({ email: email.value, password: password.value })
    authStore.setSession(result)
    router.push('/')
  } catch (err) {
    errorMsg.value = err instanceof Error ? err.message : '登录失败，请稍后重试'
    captchaRef.value?.refresh()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="space-y-4" @submit.prevent="handleLogin">
    <!-- 提示 / 错误 -->
    <div v-if="notice" class="rounded-md bg-green-50 px-3 py-2 text-sm text-green-600">
      {{ notice }}
    </div>
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
    <input
      v-model="password"
      type="password"
      placeholder="密码"
      autocomplete="current-password"
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
      {{ loading ? '登录中…' : '登录' }}
    </button>

    <!-- 底部链接 -->
    <div class="flex items-center justify-between pt-2 text-xs text-gray-500">
      <button type="button" class="hover:text-[#07C160]" @click="emit('switchToRegister')">
        注册新账号
      </button>
      <button type="button" class="hover:text-[#07C160]" @click="emit('switchToForgot')">
        忘记密码
      </button>
    </div>
  </form>
</template>

<style scoped></style>
