<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { syncElectronWindowMode } from '@/utils/electronWindow'
import Captcha from '@/components/Captcha.vue'
import AuthField from '@/components/auth/AuthField.vue'
import {
  authAlertErrorClass,
  authAlertSuccessClass,
  authLabelClass,
  authLinkClass,
  authPrimaryBtnClass,
} from './auth-ui'

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
    await router.push('/')
    syncElectronWindowMode('main')
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
    <div v-if="notice" :class="authAlertSuccessClass">
      {{ notice }}
    </div>
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

    <AuthField
      v-model="password"
      label="密码"
      type="password"
      placeholder="输入密码"
      autocomplete="current-password"
    />

    <div>
      <label :class="authLabelClass">验证码</label>
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
      {{ loading ? '登录中…' : '登录' }}
    </button>

    <div class="flex items-center justify-between border-t border-[var(--ec-border)] pt-4">
      <button type="button" :class="authLinkClass" @click="emit('switchToRegister')">
        注册新账号
      </button>
      <button type="button" :class="authLinkClass" @click="emit('switchToForgot')">
        忘记密码
      </button>
    </div>
  </form>
</template>
