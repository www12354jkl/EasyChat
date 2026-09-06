import { onMounted, onUnmounted, ref } from 'vue'

/** 默认每 60s 刷新，用于「刚刚」→ HH:mm 的自动切换 */
export function useNowTicker(intervalMs = 60_000) {
  const now = ref(new Date())
  let timer: ReturnType<typeof setInterval> | undefined

  onMounted(() => {
    timer = setInterval(() => {
      now.value = new Date()
    }, intervalMs)
  })

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return now
}
