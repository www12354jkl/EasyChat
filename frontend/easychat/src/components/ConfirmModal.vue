<script setup lang="ts">
defineProps<{
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  /** 红色危险样式（删除/退出类操作） */
  danger?: boolean
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="emit('cancel')">
    <div class="w-[300px] overflow-hidden rounded-lg bg-white shadow-xl">
      <div class="px-5 pb-2 pt-5 text-center">
        <p class="text-base font-medium text-[#1A1A1A]">{{ title }}</p>
        <p class="mt-2 text-sm leading-relaxed text-[#5A5A5A]">{{ message }}</p>
      </div>
      <div class="mt-4 flex border-t border-[#F0F0F0]">
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm text-[#1A1A1A] transition-colors hover:bg-[#F5F5F5]"
          @click="emit('cancel')"
        >
          {{ cancelText ?? '取消' }}
        </button>
        <span class="w-px bg-[#F0F0F0]"></span>
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm font-medium transition-colors hover:bg-[#F5F5F5]"
          :class="danger ? 'text-[#FA5151]' : 'text-[#07C160]'"
          @click="emit('confirm')"
        >
          {{ confirmText ?? '确定' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
