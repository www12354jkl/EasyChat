import type { GroupInviteContent } from '@/types/chat'

export function parseInviteContent(content: string): GroupInviteContent | null {
  try {
    return JSON.parse(content) as GroupInviteContent
  } catch {
    return null
  }
}

export function inviteSummary(content: string): string {
  const info = parseInviteContent(content)
  return info ? `[群邀请] ${info.groupName}` : '[群邀请]'
}

export function inviteCardLabel(
  info: GroupInviteContent | null,
  opts?: { isSender?: boolean },
): string {
  if (!info) return '群聊邀请'
  if (info.inviteStatus === 'waiting') return '已申请，等待审批'
  if (info.inviteStatus === 'joined') return '已加入该群'
  if (info.inviteStatus === 'expired') return '邀请已失效'
  return opts?.isSender ? '已发送群邀请' : '邀请你加入群聊'
}
