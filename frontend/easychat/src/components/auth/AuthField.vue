<script setup lang="ts">
import { authInputClass, authLabelClass } from './auth-ui'

defineProps<{
  label: string
  type?: string
  placeholder?: string
  autocomplete?: string
  maxlength?: number | string
}>()

const model = defineModel<string>({ required: true })
</script>

<template>
  <div>
    <label class="auth-label" :class="authLabelClass">{{ label }}</label>
    <input
      v-model="model"
      :type="type ?? 'text'"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      :maxlength="maxlength"
      :class="authInputClass"
    />
  </div>
</template>
