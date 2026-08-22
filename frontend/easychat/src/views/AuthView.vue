<script setup lang="ts">
import { computed, ref } from 'vue'
import LoginForm from '@/components/auth/LoginForm.vue'
import RegisterForm from '@/components/auth/RegisterForm.vue'
import ForgetPasswordForm from '@/components/auth/ForgetPasswordForm.vue'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()

const mode = ref<'login' | 'register' | 'forgot'>('login')
const notice = ref('')

// 前进（登录→注册/忘记密码）用 panel-next，后退用 panel-prev
const transitionName = computed(() => (mode.value === 'login' ? 'panel-prev' : 'panel-next'))
const subtitle = computed(() => {
  switch (mode.value) {
    case 'login':
      return '登录你的账号，和朋友保持联系'
    case 'register':
      return '注册新账号，开启聊天'
    case 'forgot':
      return '重置密码，找回你的账号'
  }
})

function switchTo(m: 'login' | 'register' | 'forgot') {
  notice.value = ''
  mode.value = m
}

function onRegistered(email: string) {
  notice.value = `注册成功，请使用 ${email} 登录`
  mode.value = 'login'
}

function onResetSuccess() {
  toast.success('密码重置成功，请登录')
  mode.value = 'login'
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-[#f2f2f2] px-4">
    <div class="w-[400px] rounded-xl bg-white px-10 py-12 shadow-lg">
      <!-- logo + 标题 -->
      <div class="mb-8 flex flex-col items-center gap-3">
        <div class="flex h-16 w-16 items-center justify-center rounded-xl bg-[#07C160]">
          <!-- 聊天气泡 icon -->
          <svg class="h-9 w-9 text-white" viewBox="0 0 24 24" fill="currentColor">
            <path
              d="M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z"
            />
          </svg>
        </div>
        <h1 class="text-2xl font-semibold text-gray-800">EasyChat</h1>
        <p class="text-sm text-gray-400">{{ subtitle }}</p>
      </div>

      <!-- 表单切换区域 -->
      <div class="min-h-[430px] overflow-hidden">
        <Transition :name="transitionName" mode="out-in">
          <LoginForm
            v-if="mode === 'login'"
            key="login"
            :notice="notice"
            @switch-to-register="switchTo('register')"
            @switch-to-forgot="switchTo('forgot')"
          />
          <RegisterForm
            v-else-if="mode === 'register'"
            key="register"
            @switch-to-login="switchTo('login')"
            @registered="onRegistered"
          />
          <ForgetPasswordForm
            v-else
            key="forgot"
            @switch-to-login="switchTo('login')"
            @reset-success="onResetSuccess"
          />
        </Transition>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 前进：登录 → 注册/忘记密码（内容向左滑） */
.panel-next-enter-active,
.panel-next-leave-active,
.panel-prev-enter-active,
.panel-prev-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}

.panel-next-leave-to {
  opacity: 0;
  transform: translateX(-32px);
}
.panel-next-enter-from {
  opacity: 0;
  transform: translateX(32px);
}

/* 后退：注册/忘记密码 → 登录（内容向右滑） */
.panel-prev-leave-to {
  opacity: 0;
  transform: translateX(32px);
}
.panel-prev-enter-from {
  opacity: 0;
  transform: translateX(-32px);
}
</style>
