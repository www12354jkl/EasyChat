export interface Conversation {
  id: string
  name: string
  /** 头像 URL（图片）；为空或颜色值时用首字/默认色块 */
  avatar?: string
  avatarColor: string
  lastMessage: string
  lastTime: string
  unread: number
  /** 1=单聊 2=群聊 3=群邀请 */
  type?: number
  isGroup?: boolean
  /** 群邀请会话（type=3） */
  isGroupInvite?: boolean
  /** 单聊对方的用户 id（群聊无此字段） */
  friendId?: string
  /** 群邀请：目标群 id */
  groupId?: string
  /** 群邀请：邀请记录 id，接受邀请时用 */
  inviteId?: string
  /** 群邀请：目标群是否需验证 1=需要 0=不需要 */
  joinVerify?: number
  inviterName?: string
  inviterAvatar?: string
  /** pending=待处理 waiting=已申请待审批 joined=已加入 expired=已失效 */
  inviteStatus?: 'pending' | 'waiting' | 'joined' | 'expired'
}

export interface ChatMessage {
  id: string
  conversationId: string
  /** 'me' 表示自己发的消息（后端返回当前用户 userId） */
  senderId: string
  /** 发送者昵称（群聊显示用；自己发的可省略） */
  senderName?: string
  /** 发送者头像 URL */
  senderAvatar?: string
  content: string
  /** 1=文本 2=图片 3=文件 4=群邀请卡片 */
  contentType?: number
  time: string
}

/** 群邀请卡片消息 content 字段 JSON 结构（contentType=4） */
export interface GroupInviteContent {
  inviteId: string
  groupId: string
  groupName: string
  groupAvatar?: string
  joinVerify?: number
  inviterName?: string
  inviteStatus?: 'pending' | 'waiting' | 'joined' | 'expired'
}

/** 群邀请弹窗展示数据 */
export interface GroupInviteInfo {
  inviteId: string
  name: string
  avatar?: string
  joinVerify?: number
  inviterName?: string
  inviteStatus?: GroupInviteContent['inviteStatus']
}

/** 群成员（群详情用） */
export interface GroupMember {
  userId: string
  nickname: string
  avatar: string
  /** 2=群主/管理员，1=普通成员 */
  role: number
}

/** 群详情（后端 GET /api/chat/group/{id} 返回） */
export interface GroupDetail {
  id: string
  name: string
  avatar: string
  ownerId: string
  total: number
  /** 群公告 */
  announcement: string
  /** 加入验证：1=需要验证，0=无需验证 */
  joinVerify: number
  members: GroupMember[]
}

// ============ WebSocket 实时推送 ============

/** 后端 WS 推送的会话更新（CONVERSATION_UPDATE 的 payload） */
export interface ConversationUpdate {
  id: string
  lastMessage: string | null
  lastMessageAt: string | null
  /** 可选：会话成员数变化时携带 */
  total?: number
}

/** 在线状态变更（ONLINE_STATUS 的 payload） */
export interface OnlineStatusEvent {
  userId: string
  online: boolean
}

/** WS 统一信封：顶层 type + payload */
/** WS 推送的新会话（群邀请等） */
export interface NewConversationEvent {
  id: string
  name: string
  avatar: string
  type: number
  isGroup: boolean
  isGroupInvite?: boolean
  groupId?: string
  inviteId?: string
  joinVerify?: number
  inviterName?: string
  inviterAvatar?: string
  inviteStatus?: Conversation['inviteStatus']
  lastMessage: string | null
  lastMessageAt: string | null
  unread: number
  total: number
  friendId?: string | null
}

export interface WsMessage<T = unknown> {
  type:
    | 'NEW_MESSAGE'
    | 'MESSAGE_UPDATE'
    | 'CONVERSATION_UPDATE'
    | 'CONVERSATION_REMOVED'
    | 'ONLINE_STATUS'
    | 'NEW_CONVERSATION'
    | 'FRIEND_REQUEST'
    | 'FRIEND_REQUEST_HANDLED'
    | 'GROUP_JOIN_REQUEST'
    | 'GROUP_JOIN_REQUEST_HANDLED'
    | 'PONG'
  payload: T
}

/** WS 推送：会话被移除（删好友等） */
export interface ConversationRemovedEvent {
  id: string
}

/** WS 推送：新的好友申请 */
export interface FriendRequestEvent {
  id: string
  fromUserId: string
  fromName: string
  fromAvatar: string
  message: string
  createdAt: string
}

/** WS 推送：好友申请已处理（推送给申请人） */
export interface FriendRequestHandledEvent {
  id: string
  status: 1 | 2
  userId: string
  nickname: string
  avatar: string
}

/** WS 推送：新的入群申请 */
export interface GroupJoinRequestEvent {
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

/** WS 推送：入群申请已处理 */
export interface GroupJoinRequestHandledEvent {
  id: string
  conversationId: string
  groupName: string
  status: 1 | 2
}
