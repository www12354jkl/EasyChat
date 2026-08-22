<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { ws } from '@/api/ws'
import ToastContainer from '@/components/ToastContainer.vue'

const auth = useAuthStore()

onMounted(() => {
  // 已登录则建立全局 WS 连接（登出时由 auth store 统一断开）
  if (auth.isLoggedIn) {
    ws.connect()
  }
})
</script>

<template>
  <RouterView />
  <ToastContainer />
</template>
