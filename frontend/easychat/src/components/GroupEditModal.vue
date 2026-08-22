<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  title: string
  value: string
  /** name=群昵称（单行输入），announcement=群公告（多行文本） */
  field: 'name' | 'announcement'
  maxLength?: number
}>()

const emit = defineEmits<{
  save: [value: string]
  close: []
}>()

const input = ref(props.value)

watch(
  () => props.value,
  (v) => (input.value = v),
)

const trimmed = () => input.value.trim()

function handleSave() {
  const v = trimmed()
  if (!v) return
  emit('save', v)
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="emit('close')">
    <div class="w-[340px] overflow-hidden rounded-lg bg-white shadow-xl">
      <div class="flex items-center justify-between px-5 pb-2 pt-4">
        <p class="text-base font-medium text-[#1A1A1A]">{{ title }}</p>
        <button
          type="button"
          class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md text-[#8A8A8A] transition-colors hover:bg-[#F5F5F5]"
          @click="emit('close')"
        >
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M18 6 6 18M6 6l12 12" />
          </svg>
        </button>
      </div>
      <div class="px-5 py-3">
        <textarea
          v-if="field === 'announcement'"
          v-model="input"
          rows="4"
          :maxlength="maxLength ?? 200"
          placeholder="请输入群公告"
          class="w-full resize-none rounded-md border border-[#E5E5E5] px-3 py-2 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
        ></textarea>
        <input
          v-else
          v-model="input"
          type="text"
          :maxlength="maxLength ?? 30"
          placeholder="请输入群昵称"
          class="w-full rounded-md border border-[#E5E5E5] px-3 py-2 text-sm outline-none transition focus:border-[#07C160] focus:ring-2 focus:ring-[#07C160]/20"
          @keyup.enter="handleSave"
        />
      </div>
      <div class="flex border-t border-[#F0F0F0]">
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm text-[#1A1A1A] transition-colors hover:bg-[#F5F5F5]"
          @click="emit('close')"
        >
          取消
        </button>
        <span class="w-px bg-[#F0F0F0]"></span>
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm font-medium text-[#07C160] transition-colors hover:bg-[#F5F5F5]"
          :disabled="!trimmed()"
          :class="{ 'cursor-not-allowed opacity-40': !trimmed() }"
          @click="handleSave"
        >
          保存
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
