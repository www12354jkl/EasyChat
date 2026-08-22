<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Cropper } from 'vue-advanced-cropper'
import 'vue-advanced-cropper/dist/style.css'

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
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    @click.self="emit('close')"
  >
    <div class="flex w-[420px] flex-col rounded-lg bg-white shadow-xl">
      <div class="flex h-12 shrink-0 items-center justify-between border-b border-[#E5E5E5] px-4">
        <span class="text-sm font-medium text-[#1A1A1A]">裁剪头像</span>
        <button
          type="button"
          class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md text-[#8A8A8A] transition-colors hover:bg-[#F5F5F5] hover:text-[#1A1A1A]"
          @click="emit('close')"
        >
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>

      <div class="relative h-[320px] bg-[#DDD]">
        <Cropper
          ref="cropperRef"
          :src="src"
          :stencil-props="{ aspectRatio: 1 }"
          class="h-full w-full"
        />
      </div>

      <div class="flex shrink-0 gap-3 border-t border-[#E5E5E5] p-4">
        <button
          type="button"
          class="flex-1 cursor-pointer rounded-md border border-[#E5E5E5] py-2 text-sm text-[#8A8A8A] transition-colors hover:bg-[#F5F5F5]"
          @click="emit('close')"
        >
          取消
        </button>
        <button
          type="button"
          class="flex-1 cursor-pointer rounded-md bg-[#07C160] py-2 text-sm font-medium text-white transition-colors hover:bg-[#06ad56]"
          @click="confirm"
        >
          确认
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
