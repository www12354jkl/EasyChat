<script setup lang="ts">
import { onMounted, ref } from 'vue'

// 去掉易混淆字符：0/O、1/I/L
const CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const LENGTH = 4

const canvasRef = ref<HTMLCanvasElement | null>(null)
const code = ref('')

function randomChar(): string {
  return CHARS.charAt(Math.floor(Math.random() * CHARS.length))
}

function draw(): void {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const { width, height } = canvas

  // 背景
  ctx.fillStyle = '#ecfdf5'
  ctx.fillRect(0, 0, width, height)

  // 干扰线
  for (let i = 0; i < 3; i++) {
    ctx.strokeStyle = `hsl(${Math.floor(Math.random() * 360)}, 60%, 70%)`
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(Math.random() * width, Math.random() * height)
    ctx.lineTo(Math.random() * width, Math.random() * height)
    ctx.stroke()
  }

  // 字符（随机颜色 + 旋转 + 上下浮动）
  code.value.split('').forEach((ch, i) => {
    ctx.save()
    ctx.font = 'bold 22px Arial, sans-serif'
    ctx.fillStyle = `hsl(${Math.floor(Math.random() * 360)}, 60%, 40%)`
    ctx.translate(10 + i * 24 + Math.random() * 6, 28 + (Math.random() - 0.5) * 8)
    ctx.rotate((Math.random() - 0.5) * 0.5)
    ctx.fillText(ch, 0, 0)
    ctx.restore()
  })

  // 噪点
  for (let i = 0; i < 20; i++) {
    ctx.fillStyle = `hsl(${Math.floor(Math.random() * 360)}, 60%, 60%)`
    ctx.beginPath()
    ctx.arc(Math.random() * width, Math.random() * height, 1, 0, Math.PI * 2)
    ctx.fill()
  }
}

function refresh(): void {
  code.value = Array.from({ length: LENGTH }, randomChar).join('')
  draw()
}

defineExpose({ code, refresh })

onMounted(refresh)
</script>

<template>
  <canvas
    ref="canvasRef"
    width="110"
    height="40"
    class="cursor-pointer rounded-[var(--ec-radius)] border border-[var(--ec-border)]"
    title="点击刷新验证码"
    @click="refresh"
  />
</template>

<style scoped></style>
