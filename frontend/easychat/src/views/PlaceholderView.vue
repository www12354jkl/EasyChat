<script setup lang="ts">
defineProps<{
  title: string
  desc: string
}>()
</script>

<template>
  <div class="flex h-full flex-col items-center justify-center gap-2 text-gray-400">
    <svg
      class="h-12 w-12 opacity-40"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
    >
      <path
        d="M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
    <p class="text-lg font-medium">{{ title }}</p>
    <p class="text-sm">{{ desc }}</p>
  </div>
</template>

<style scoped></style>
