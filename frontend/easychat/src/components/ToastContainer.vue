<script setup lang="ts">
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()

const typeClass: Record<string, string> = {
  success: 'bg-[#07C160]',
  error: 'bg-red-500',
  info: 'bg-gray-800',
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
        class="rounded-full px-5 py-2 text-sm text-white shadow-lg"
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
  transition: all 0.25s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
