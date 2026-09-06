<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { IS_ELECTRON, hasElectronBridge } from '@/config/env'
import { syncElectronWindowMode } from '@/utils/electronWindow'
import { ws } from '@/api/ws'
import ToastContainer from '@/components/ToastContainer.vue'
import ElectronTitleBar from '@/components/electron/ElectronTitleBar.vue'

const auth = useAuthStore()
const route = useRoute()

const isAuthRoute = computed(() => route.name === 'login')

const showElectronChrome = computed(() => IS_ELECTRON && hasElectronBridge())

onMounted(() => {
  if (IS_ELECTRON) {
    syncElectronWindowMode(isAuthRoute.value ? 'auth' : 'main')
  }
  if (auth.isLoggedIn) {
    ws.connect()
  }
})
</script>

<template>
  <div v-if="showElectronChrome" class="flex h-full flex-col overflow-hidden bg-[var(--ec-bg)]">
    <ElectronTitleBar />
    <div class="min-h-0 flex-1 overflow-hidden">
      <RouterView />
    </div>
    <ToastContainer />
  </div>
  <template v-else>
    <RouterView />
    <ToastContainer />
  </template>
</template>
