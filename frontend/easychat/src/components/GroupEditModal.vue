<script setup lang="ts">
import { ref, watch } from 'vue'
import { authInputClass } from '@/components/auth/auth-ui'

const props = defineProps<{
  title: string
  value: string
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
  <div class="ec-modal-overlay" @click.self="emit('close')">
    <div class="ec-modal-panel w-[340px]">
      <div class="flex items-center justify-between px-5 pb-2 pt-4">
        <p class="text-base font-semibold text-[var(--ec-text)]">{{ title }}</p>
        <button type="button" class="ec-icon-btn" @click="emit('close')">
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
          class="ec-input resize-none"
        />
        <input
          v-else
          v-model="input"
          type="text"
          :maxlength="maxLength ?? 30"
          placeholder="请输入群昵称"
          :class="authInputClass"
          @keyup.enter="handleSave"
        />
      </div>
      <div class="flex border-t border-[var(--ec-border)]">
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm text-[var(--ec-text)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)]"
          @click="emit('close')"
        >
          取消
        </button>
        <span class="w-px bg-[var(--ec-border)]" />
        <button
          type="button"
          class="flex-1 cursor-pointer py-3 text-sm font-medium text-[var(--ec-primary)] transition-colors duration-200 hover:bg-[var(--ec-bg-muted)]"
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
