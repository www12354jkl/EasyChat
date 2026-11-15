<script setup lang="ts">
defineProps<{
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <div class="ec-modal-overlay" @click.self="emit('cancel')">
    <div class="ec-modal-panel w-[300px]">
      <div class="px-5 pb-2 pt-5 text-center">
        <p class="text-base font-semibold text-[var(--ec-text)]">{{ title }}</p>
        <p class="mt-2 text-sm leading-relaxed text-[var(--ec-text-muted)]">{{ message }}</p>
      </div>
      <div class="mt-4 flex border-t border-[var(--ec-border)]">
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm text-[var(--ec-text)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)]"
          @click="emit('cancel')"
        >
          {{ cancelText ?? '取消' }}
        </button>
        <span class="w-px bg-[var(--ec-border)]" />
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm font-medium transition-colors duration-200 hover:bg-[var(--ec-bg-muted)]"
          :class="danger ? 'text-[var(--ec-danger)]' : 'text-[var(--ec-primary)]'"
          @click="emit('confirm')"
        >
          {{ confirmText ?? '确定' }}
        </button>
      </div>
    </div>
  </div>
</template>
