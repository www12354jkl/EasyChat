const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function parseDate(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? null : d
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function hhmm(d: Date) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function isSameDay(a: Date, b: Date) {
  return a.toDateString() === b.toDateString()
}

function isYesterday(d: Date, now: Date) {
  const yesterday = startOfDay(now)
  yesterday.setDate(yesterday.getDate() - 1)
  return startOfDay(d).getTime() === yesterday.getTime()
}

/** 自然周：周一为起点 */
function isSameWeek(d: Date, now: Date) {
  const monday = (date: Date) => {
    const x = startOfDay(date)
    const day = x.getDay() || 7
    x.setDate(x.getDate() - day + 1)
    return x
  }
  return monday(d).getTime() === monday(now).getTime()
}

export function formatMessageTime(iso: string | null | undefined, now = new Date()): string {
  const d = parseDate(iso)
  if (!d) return ''

  const diffMs = now.getTime() - d.getTime()
  if (diffMs >= 0 && diffMs < 60_000) return '刚刚'

  const time = hhmm(d)
  if (isSameDay(d, now)) return time
  if (isYesterday(d, now)) return `昨天 ${time}`
  if (isSameWeek(d, now)) return `${WEEKDAYS[d.getDay()]} ${time}`

  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${time}`
}

export function formatConversationTime(iso: string | null | undefined, now = new Date()): string {
  const d = parseDate(iso)
  if (!d) return ''

  const time = hhmm(d)
  if (isSameDay(d, now)) return time
  if (isYesterday(d, now)) return '昨天'
  if (isSameWeek(d, now)) return WEEKDAYS[d.getDay()]!

  const y = d.getFullYear()
  const thisYear = now.getFullYear()
  if (y === thisYear) return `${pad(d.getMonth() + 1)}/${pad(d.getDate())}`
  return `${y}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}`
}

/** 组间时间分隔：间隔超过 gapMs 时显示 */
export function shouldShowTimeDivider(
  prev: { createdAt?: string } | null | undefined,
  curr: { createdAt?: string },
  gapMs = 5 * 60_000,
): boolean {
  if (!curr.createdAt) return true
  if (!prev?.createdAt) return true
  return new Date(curr.createdAt).getTime() - new Date(prev.createdAt).getTime() > gapMs
}
