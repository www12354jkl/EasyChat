<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Cropper } from 'vue-advanced-cropper'
import 'vue-advanced-cropper/dist/style.css'
import { authPrimaryBtnClass } from '@/components/auth/auth-ui'

const props = defineProps<{ file: File }>()
const emit = defineEmits<{ close: []; cropped: [file: File] }>()

const cropperRef = ref<any>(null)
const src = ref(URL.createObjectURL(props.file))

onBeforeUnmount(() => URL.revokeObjectURL(src.value))

function confirm() {
  const canvas = cropperRef.value?.getResult()?.canvas
  if (!canvas) return
  canvas.toBlob((blob: Blob | null) => {
    if (!blob) return
    emit('cropped', new File([blob], 'avatar.jpg', { type: 'image/jpeg' }))
  }, 'image/jpeg', 0.92)
}
</script>

<template>
  <div class="ec-modal-overlay" @click.self="emit('close')">
    <div class="ec-modal-panel flex w-[420px] flex-col">
      <div class="flex h-12 shrink-0 items-center justify-between border-b border-[var(--ec-border)] px-4">
        <span class="text-sm font-semibold text-[var(--ec-text)]">裁剪头像</span>
        <button type="button" class="ec-icon-btn" @click="emit('close')">
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>

      <div class="relative h-[320px] bg-[var(--ec-bg-muted)]">
        <Cropper
          ref="cropperRef"
          :src="src"
          :stencil-props="{ aspectRatio: 1 }"
          class="h-full w-full"
        />
      </div>

      <div class="flex shrink-0 gap-3 border-t border-[var(--ec-border)] p-4">
        <button type="button" class="ec-btn-secondary flex-1 py-2" @click="emit('close')">
          取消
        </button>
        <button type="button" :class="authPrimaryBtnClass + ' flex-1'" @click="confirm">
          确认
        </button>
      </div>
    </div>
  </div>
</template>
