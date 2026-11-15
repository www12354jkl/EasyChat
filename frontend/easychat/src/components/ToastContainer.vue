<script setup lang="ts">
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()

const typeClass: Record<string, string> = {
  success: 'bg-[var(--ec-primary)]',
  error: 'bg-[var(--ec-danger)]',
  info: 'bg-[var(--ec-text)]',
}
</script>

<template>
  <Teleport to="body">
    <TransitionGroup
      name="toast"
      tag="div"
      class="pointer-events-none fixed inset-x-0 top-12 z-50 flex flex-col items-center gap-2 px-4"
    >
      <div
        v-for="t in toast.toasts"
        :key="t.id"
        class="rounded-full px-5 py-2 text-sm text-white shadow-[0_4px_16px_rgba(26,46,36,0.12)]"
        :class="typeClass[t.type]"
      >
        {{ t.message }}
      </div>
    </TransitionGroup>
  </Teleport>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
