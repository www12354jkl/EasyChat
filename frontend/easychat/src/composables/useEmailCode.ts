import { computed, onUnmounted, ref } from 'vue'
import { sendEmailCode, type VerifyCodeScene } from '@/api/auth'
import { useToastStore } from '@/stores/toast'

/**
 * 邮箱验证码发送逻辑：邮箱 + 验证码输入、格式校验、60s 倒计时、发送成功 toast。
 * 注册、忘记密码等需要"发送邮箱验证码"的表单复用。
 * @param scene 场景：register=注册（邮箱须未注册），forgot=忘记密码（邮箱须已注册）
 * @param sendFn 自定义发送函数，覆盖默认的 auth 发码接口（如登录态改密码走 /api/user/send-reset-code）
 */
export function useEmailCode(scene: VerifyCodeScene, sendFn?: (email: string) => Promise<void>) {
  const email = ref('')
  const emailCode = ref('')
  const sending = ref(false)
  const countdown = ref(0)
  const errorMsg = ref('')

  const toast = useToastStore()
  let timer: ReturnType<typeof setInterval> | undefined

  const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value))

  async function sendCode() {
    if (countdown.value > 0 || sending.value) return
    if (!emailValid.value) {
      errorMsg.value = '请输入正确的邮箱地址'
      return
    }
    sending.value = true
    try {
      const doSend = sendFn ?? ((e: string) => sendEmailCode(e, scene))
      await doSend(email.value)
      toast.success('验证码已发送，请查收邮件')
      countdown.value = 60
      timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0 && timer) {
          clearInterval(timer)
          timer = undefined
        }
      }, 1000)
    } catch (err) {
      errorMsg.value = err instanceof Error ? err.message : '验证码发送失败，请稍后重试'
    } finally {
      sending.value = false
    }
  }

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return { email, emailCode, sending, countdown, emailValid, errorMsg, sendCode }
}
