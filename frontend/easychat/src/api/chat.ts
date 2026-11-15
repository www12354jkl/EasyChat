import { request, ApiError } from './request'

export interface CreateGroupParams {
  name: string
  avatar: string
  announcement: string
  joinVerify: number
  memberIds: string[]
}

export interface GroupMemberBrief {
  userId: string
  nickname: string
  avatar: string
}

export interface GroupInfo {
  id: string
  name: string
  avatar: string
  ownerId: string
  ownerName: string
  announcement: string
  joinVerify: number
  members: GroupMemberBrief[]
}

/** 群列表简要信息 */
export interface GroupBrief {
  id: string
  name: string
  avatar: string
  total: number
}

import type { GroupDetail } from '@/types/chat'

export function createGroup(params: CreateGroupParams): Promise<GroupInfo> {
  return request<GroupInfo>('/api/chat/group', { method: 'POST', body: params })
}

export function getGroupDetail(id: string): Promise<GroupDetail> {
  return request<GroupDetail>(`/api/chat/group/${id}`)
}

/** 退出群聊（后端 DELETE /api/chat/group/{id}） */
export function quitGroup(id: string): Promise<void> {
  return request<void>(`/api/chat/group/${id}`, { method: 'DELETE' })
}

/** 修改群信息（群昵称/群公告/入群验证，仅群主/管理员；后端 PUT /api/chat/group/{id}） */
export function updateGroupInfo(
  id: string,
  data: { name?: string; announcement?: string; joinVerify?: number },
): Promise<void> {
  return request<void>(`/api/chat/group/${id}`, { method: 'PUT', body: data })
}

/** 给群聊添加成员（仅群主/管理员；后端 POST /api/chat/group/{id}/members） */
export function addGroupMembers(groupId: string, memberIds: string[]): Promise<void> {
  return request<void>(`/api/chat/group/${groupId}/members`, {
    method: 'POST',
    body: { memberIds },
  })
}

/** 设置群成员角色（仅群主；后端 PUT /api/chat/group/{id}/member/{userId}/role，role 1=成员 2=管理员） */
export function setGroupMemberRole(groupId: string, userId: string, role: number): Promise<void> {
  return request<void>(`/api/chat/group/${groupId}/member/${userId}/role`, {
    method: 'PUT',
    body: { role },
  })
}

/** 移出群成员（群主可移出除自己外所有人；管理员仅可移出普通成员） */
export function kickGroupMember(groupId: string, userId: string): Promise<void> {
  return request<void>(`/api/chat/group/${groupId}/member/${userId}`, { method: 'DELETE' })
}

/** 清空会话聊天记录（仅自己可见，后端只更新 cleared_at） */
export function clearConversationMessages(conversationId: string): Promise<void> {
  return request<void>(`/api/chat/conversation/${conversationId}/messages`, {
    method: 'DELETE',
  })
}

// ============ 消息 ============

/** 后端 MessageDTO（历史消息分页返回的单条记录） */
export interface MessageRecord {
  id: string
  conversationId: string
  senderId: string
  senderName?: string | null
  senderAvatar?: string | null
  contentType?: number | null
  content: string
  /** 1=正常 2=已撤回 */
  status?: number | null
  createdAt?: string | null
}

/** 历史消息分页（后端 GET /api/chat/messages?conversationId=&title=&page=&size=） */
export interface MessagePage {
  records: MessageRecord[]
  total: number
  page: number
  size: number
}

export async function getMessagePage(
  conversationId: string,
  page = 1,
  size = 30,
  title = '',
): Promise<MessagePage> {
  const params = new URLSearchParams({
    conversationId,
    page: String(page),
    size: String(size),
  })
  if (title) params.set('title', title)
  return request<MessagePage>(`/api/chat/messages?${params.toString()}`)
}

/** 发送消息（后端 POST /api/chat/message）。网络失败时用同一个 clientMsgId 再试一次，避免插出两条 */
export async function sendMessage(params: {
  conversationId: string
  content: string
  contentType?: number
  clientMsgId?: string
}): Promise<MessageRecord> {
  const body = { ...params, clientMsgId: params.clientMsgId ?? crypto.randomUUID() }
  try {
    return await request<MessageRecord>('/api/chat/message', { method: 'POST', body })
  } catch (e) {
    if (e instanceof ApiError && e.message === '网络连接失败，请检查网络') {
      return request<MessageRecord>('/api/chat/message', { method: 'POST', body })
    }
    throw e
  }
}

/** 撤回消息（后端 POST /api/chat/message/{id}/recall，2 分钟内且仅自己发送） */
export function recallMessage(messageId: string): Promise<MessageRecord> {
  return request<MessageRecord>(`/api/chat/message/${messageId}/recall`, { method: 'POST' })
}

/** 上传聊天图片/文件，返回 COS URL（后端 POST /api/chat/upload，multipart） */
export function uploadChatFile(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  return request<string>('/api/chat/upload', { method: 'POST', body: formData })
}

/** 我的会话列表（后端 GET /api/chat/conversations） */
export function getConversations(): Promise<ConversationItem[]> {
  return request<ConversationItem[]>('/api/chat/conversations')
}

/** 标记会话已读（后端 PUT /api/chat/conversation/{id}/read） */
export function markConversationRead(conversationId: string): Promise<void> {
  return request<void>(`/api/chat/conversation/${conversationId}/read`, { method: 'PUT' })
}

/** 创建或获取单聊会话（后端 POST /api/chat/conversation/direct；会话 id 以后端返回为准） */
export function getOrCreateDirectConversation(toUserId: string): Promise<ConversationItem> {
  return request<ConversationItem>('/api/chat/conversation/direct', {
    method: 'POST',
    body: { toUserId },
  })
}

/** 后端 ConversationResponse */
export interface ConversationItem {
  id: string
  name: string
  avatar: string
  /** 1=单聊 2=群聊 3=群邀请 */
  type: number
  isGroup: boolean
  isGroupInvite?: boolean
  friendId: string | null
  groupId?: string | null
  inviteId?: string | null
  joinVerify?: number
  inviterName?: string | null
  inviterAvatar?: string | null
  inviteStatus?: 'pending' | 'waiting' | 'joined' | 'expired'
  lastMessage: string | null
  lastMessageAt: string | null
  unread: number
  total: number
}

export function getMyGroups(): Promise<GroupBrief[]> {
  return request<GroupBrief[]>('/api/chat/group/my')
}

export function getJoinGroups(): Promise<GroupBrief[]> {
  return request<GroupBrief[]>('/api/chat/group/all-join')
}

export function uploadGroupAvatar(id: string, file: File): Promise<string> {
  const formData = new FormData()
  formData.append('id', id)
  formData.append('file', file)
  return request<string>('/api/chat/group/avatar', { method: 'PUT', body: formData })
}

// ============ 群邀请 & 入群申请 ============

/** 邀请好友进群（后端为被邀请人生成群邀请会话，不直接加群） */
export function inviteGroupMembers(groupId: string, memberIds: string[]): Promise<void> {
  return request<void>(`/api/chat/group/${groupId}/invite`, {
    method: 'POST',
    body: { memberIds },
  })
}

/** 接受群邀请；joined=已入群 pending=已提交验证等待审批 */
export function acceptGroupInvite(
  inviteId: string,
  message = '',
): Promise<{ status: 'joined' | 'pending'; groupConversationId?: string }> {
  return request<{ status: 'joined' | 'pending'; groupConversationId?: string }>(
    `/api/chat/group/invite/${inviteId}/accept`,
    { method: 'POST', body: { message } },
  )
}

/** 忽略/拒绝群邀请 */
export function declineGroupInvite(inviteId: string): Promise<void> {
  return request<void>(`/api/chat/group/invite/${inviteId}`, { method: 'DELETE' })
}

/** 待处理的入群申请（群主/管理员可见） */
export interface GroupJoinRequestItem {
  id: string
  conversationId: string
  groupName: string
  groupAvatar: string
  userId: string
  nickname: string
  avatar: string
  message: string
  createdAt: string
}

/** 获取待审批入群申请；conversationId 可选，不传则返回全部 */
export function getGroupJoinRequests(conversationId?: string): Promise<GroupJoinRequestItem[]> {
  const qs = conversationId
    ? `?conversationId=${encodeURIComponent(conversationId)}`
    : ''
  return request<GroupJoinRequestItem[]>(`/api/chat/group/join-requests${qs}`)
}

/** 处理入群申请：status 1=同意 2=拒绝 */
export function handleGroupJoinRequest(id: string, status: 1 | 2): Promise<void> {
  return request<void>(`/api/chat/group/join-request/${id}`, {
    method: 'PUT',
    body: { status },
  })
}
