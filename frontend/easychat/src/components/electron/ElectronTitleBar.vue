<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const compact = computed(() => route.name === 'login')
const maximized = ref(false)

let stopListen: (() => void) | undefined

onMounted(() => {
  stopListen = window.electronAPI?.onMaximizedChanged?.((v) => {
    maximized.value = v
  })
})

onUnmounted(() => {
  stopListen?.()
})

function minimize() {
  window.electronAPI?.windowMinimize?.()
}

function toggleMaximize() {
  window.electronAPI?.windowToggleMaximize?.()
}

function close() {
  window.electronAPI?.windowClose?.()
}
</script>

<template>
  <header
    class="electron-titlebar relative z-50 flex h-9 shrink-0 select-none items-center border-b border-[var(--ec-border)] bg-[var(--ec-bg)]"
    :class="compact ? 'px-3' : 'px-2'"
  >
    <!-- 左侧品牌 -->
    <div class="titlebar-brand flex min-w-0 items-center gap-2">
      <div
        class="flex h-5 w-5 shrink-0 items-center justify-center rounded-[4px] bg-[var(--ec-primary)] text-white"
      >
        <svg class="h-3 w-3" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path
            d="M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z"
          />
        </svg>
      </div>
      <span class="truncate text-[13px] font-medium text-[var(--ec-text)]">EasyChat</span>
    </div>

    <!-- 中间拖拽区 -->
    <div class="titlebar-drag min-w-0 flex-1" />

    <!-- 窗口控制按钮 -->
    <div class="titlebar-controls flex shrink-0 items-stretch">
      <button
        type="button"
        class="titlebar-btn"
        title="最小化"
        aria-label="最小化"
        @click.stop="minimize"
        @mousedown.stop.prevent="minimize"
      >
        <svg class="h-3 w-3" viewBox="0 0 12 12" fill="currentColor" aria-hidden="true">
          <rect x="1" y="5.5" width="10" height="1" rx="0.5" />
        </svg>
      </button>

      <button
        v-if="!compact"
        type="button"
        class="titlebar-btn"
        :title="maximized ? '还原' : '最大化'"
        :aria-label="maximized ? '还原' : '最大化'"
        @click.stop="toggleMaximize"
        @mousedown.stop.prevent="toggleMaximize"
      >
        <svg
          v-if="!maximized"
          class="h-3 w-3"
          viewBox="0 0 12 12"
          fill="none"
          stroke="currentColor"
          stroke-width="1.2"
          aria-hidden="true"
        >
          <rect x="1.5" y="1.5" width="9" height="9" rx="0.5" />
        </svg>
        <svg
          v-else
          class="h-3 w-3"
          viewBox="0 0 12 12"
          fill="none"
          stroke="currentColor"
          stroke-width="1.2"
          aria-hidden="true"
        >
          <path d="M3.5 3.5h5v5H3.5z" />
          <path d="M5 1.5h5.5v5.5" />
        </svg>
      </button>

      <button
        type="button"
        class="titlebar-btn titlebar-btn-close"
        title="关闭"
        aria-label="关闭"
        @click.stop="close"
        @mousedown.stop.prevent="close"
      >
        <svg class="h-3 w-3" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
          <path d="M2 2l8 8M10 2L2 10" stroke-linecap="round" />
        </svg>
      </button>
    </div>
  </header>
</template>

<style scoped>
.titlebar-drag {
  -webkit-app-region: drag;
  height: 100%;
}

.titlebar-controls {
  position: relative;
  z-index: 2;
  -webkit-app-region: no-drag;
}

.titlebar-brand,
.titlebar-btn {
  -webkit-app-region: no-drag;
}

.titlebar-btn {
  display: flex;
  width: 2.75rem;
  height: 2.25rem;
  align-items: center;
  justify-content: center;
  color: var(--ec-text-muted);
  transition: background-color 0.15s ease, color 0.15s ease;
}

.titlebar-btn:hover {
  background-color: var(--ec-bg-muted);
  color: var(--ec-text);
}

.titlebar-btn-close:hover {
  background-color: #e81123;
  color: #fff;
}
</style>
