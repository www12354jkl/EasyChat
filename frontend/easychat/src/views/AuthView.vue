<script setup lang="ts">
import { computed, ref } from 'vue'
import LoginForm from '@/components/auth/LoginForm.vue'
import RegisterForm from '@/components/auth/RegisterForm.vue'
import ForgetPasswordForm from '@/components/auth/ForgetPasswordForm.vue'
import { useToastStore } from '@/stores/toast'
import { IS_ELECTRON } from '@/config/env'

const toast = useToastStore()

const mode = ref<'login' | 'register' | 'forgot'>('login')
const notice = ref('')

const transitionName = computed(() => (mode.value === 'login' ? 'panel-prev' : 'panel-next'))

const pageTitle = computed(() => {
  switch (mode.value) {
    case 'login':
      return '登录 EasyChat'
    case 'register':
      return '注册账号'
    case 'forgot':
      return '找回密码'
  }
})

const subtitle = computed(() => {
  switch (mode.value) {
    case 'login':
      return '简洁、安静地和朋友保持联系'
    case 'register':
      return '几步完成，即可开始聊天'
    case 'forgot':
      return '通过邮箱验证重置密码'
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
  <div
    class="auth-shell flex bg-[var(--ec-bg-subtle)]"
    :class="IS_ELECTRON ? 'h-full' : 'min-h-[100dvh]'"
  >
    <!-- 左侧品牌区（Web 宽屏） -->
    <aside
      v-if="!IS_ELECTRON"
      class="hidden w-[42%] min-w-[320px] flex-col justify-between border-r border-[var(--ec-border)] bg-[var(--ec-bg)] p-12 lg:flex"
    >
      <div>
        <div class="mb-8 flex items-center gap-3">
          <div
            class="flex h-11 w-11 items-center justify-center rounded-[var(--ec-radius)] bg-[var(--ec-primary)] text-white"
          >
            <svg class="h-6 w-6" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path
                d="M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z"
              />
            </svg>
          </div>
          <span class="text-lg font-semibold tracking-tight text-[var(--ec-text)]">EasyChat</span>
        </div>
        <h2 class="max-w-xs text-3xl font-semibold leading-snug tracking-tight text-[var(--ec-text)]">
          轻量桌面聊天，<br />专注对话本身
        </h2>
        <p class="mt-4 max-w-sm text-sm leading-relaxed text-[var(--ec-text-muted)]">
          白与绿的界面，少干扰、多留白。适合日常沟通，也适合长时间挂在桌面使用。
        </p>
      </div>
      <p class="text-xs text-[var(--ec-text-faint)]">端到端加密传输 · 简洁可靠</p>
    </aside>

    <!-- 表单区 -->
    <div
      class="flex flex-1 flex-col"
      :class="
        IS_ELECTRON
          ? 'px-6 pb-8 pt-5'
          : 'items-center justify-center px-4 py-10'
      "
    >
      <div
        class="w-full"
        :class="IS_ELECTRON ? 'flex flex-1 flex-col' : 'max-w-[380px]'"
      >
        <header class="mb-6" :class="IS_ELECTRON ? 'text-center' : ''">
          <div
            v-if="IS_ELECTRON"
            class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-[var(--ec-radius)] bg-[var(--ec-primary)] text-white"
          >
            <svg class="h-7 w-7" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path
                d="M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z"
              />
            </svg>
          </div>
          <h1 class="text-2xl font-semibold tracking-tight text-[var(--ec-text)]">
            {{ pageTitle }}
          </h1>
          <p class="mt-2 text-sm text-[var(--ec-text-muted)]">
            {{ subtitle }}
          </p>
        </header>

        <div
          class="auth-card rounded-2xl border border-[var(--ec-border)] bg-[var(--ec-bg)] p-5 shadow-[0_1px_2px_rgba(26,46,36,0.04)]"
          :class="IS_ELECTRON ? 'flex-1 overflow-y-auto' : ''"
        >
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
  </div>
</template>

<style scoped>
.auth-card {
  animation: auth-enter 0.35s ease both;
}

@keyframes auth-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.panel-next-enter-active,
.panel-next-leave-active,
.panel-prev-enter-active,
.panel-prev-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.panel-next-leave-to,
.panel-next-enter-from {
  opacity: 0;
  transform: translateX(12px);
}

.panel-prev-leave-to,
.panel-prev-enter-from {
  opacity: 0;
  transform: translateX(-12px);
}

@media (prefers-reduced-motion: reduce) {
  .auth-card {
    animation: none;
  }

  .panel-next-leave-to,
  .panel-next-enter-from,
  .panel-prev-leave-to,
  .panel-prev-enter-from {
    transform: none;
  }
}
</style>
