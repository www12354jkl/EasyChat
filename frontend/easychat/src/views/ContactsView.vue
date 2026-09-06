<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CreateGroupModal from '@/components/CreateGroupModal.vue'
import {
  getGroupJoinRequests,
  getJoinGroups,
  getMyGroups,
  getOrCreateDirectConversation,
} from '@/api/chat'
import GroupJoinRequestsPanel from '@/components/GroupJoinRequestsPanel.vue'
import {
  getFriendRequests,
  getFriends,
  handleFriendRequest,
  searchUser,
  sendFriendRequest,
  type FriendItem,
  type FriendRequestItem,
} from '@/api/user'
import { formatTime, useChatStore } from '@/stores/chat'
import { useContactsNotifyStore } from '@/stores/contactsNotify'
import { useToastStore } from '@/stores/toast'
import { ws } from '@/api/ws'
import { ecAvatarStyle } from '@/utils/ec-ui'

type Tab = 'newFriends' | 'myGroups' | 'joinedGroups' | 'friends'

const activeTab = ref<Tab>('newFriends')

const toast = useToastStore()
const router = useRouter()
const chatStore = useChatStore()
const contactsNotify = useContactsNotifyStore()

const joinRequestsPanelRef = ref<InstanceType<typeof GroupJoinRequestsPanel> | null>(null)

const tabItems = ref<{ key: Tab; label: string; badge?: number }[]>([
  { key: 'newFriends', label: '新朋友' },
  { key: 'myGroups', label: '我的群聊' },
  { key: 'joinedGroups', label: '我加入的群聊' },
  { key: 'friends', label: '我的好友' },
])

const friendRequests = ref<FriendRequestItem[]>([])
const friends = ref<FriendItem[]>([])

async function loadFriendPanel() {
  try {
    const [reqs, list] = await Promise.all([getFriendRequests(), getFriends()])
    friendRequests.value = reqs
    friends.value = list
  } catch {
    friendRequests.value = []
    friends.value = []
  }
  const pending = friendRequests.value.length
  const newFriendsTab = tabItems.value.find((t) => t.key === 'newFriends')
  if (newFriendsTab) newFriendsTab.badge = pending > 0 ? pending : undefined
  contactsNotify.friendRequestCount = pending
}

function syncJoinRequestBadge(count: number) {
  const myGroupsTab = tabItems.value.find((t) => t.key === 'myGroups')
  if (myGroupsTab) myGroupsTab.badge = count > 0 ? count : undefined
  contactsNotify.groupJoinRequestCount = count
}

interface GroupItem {
  id: string
  name: string
  members: number
  avatar: string
  avatarColor: string
}

const myGroups = ref<GroupItem[]>([])
const joinedGroups = ref<GroupItem[]>([])

async function loadGroups() {
  try {
    const [mine, joined] = await Promise.all([getMyGroups(), getJoinGroups()])
    myGroups.value = mine.map((g) => ({
      id: g.id,
      name: g.name,
      members: g.total,
      avatar: g.avatar,
      avatarColor: '#07C160',
    }))
    joinedGroups.value = joined.map((g) => ({
      id: g.id,
      name: g.name,
      members: g.total,
      avatar: g.avatar,
      avatarColor: '#07C160',
    }))
  } catch (e) {
    toast.error((e as Error).message)
  }
}

async function loadJoinRequestBadge() {
  try {
    const reqs = await getGroupJoinRequests()
    syncJoinRequestBadge(reqs.length)
  } catch {
    // 接口未就绪时静默
  }
}

function handleWsFriendRequest(e: FriendRequestEvent) {
  if (friendRequests.value.some((r) => r.id === e.id)) return
  friendRequests.value.unshift({
    id: e.id,
    fromUserId: e.fromUserId,
    fromName: e.fromName,
    fromAvatar: e.fromAvatar,
    message: e.message,
    createdAt: e.createdAt,
  })
  const newFriendsTab = tabItems.value.find((t) => t.key === 'newFriends')
  if (newFriendsTab) newFriendsTab.badge = friendRequests.value.length
  contactsNotify.friendRequestCount = friendRequests.value.length
}

onMounted(() => {
  loadGroups()
  loadFriendPanel()
  loadJoinRequestBadge()
  ws.on('FRIEND_REQUEST', handleWsFriendRequest)
})

onUnmounted(() => {
  ws.off('FRIEND_REQUEST', handleWsFriendRequest)
})

/** 点击群：加入会话列表并跳聊天页选中该群 */
function openGroupChat(g: GroupItem) {
  chatStore.addConversation({
    id: g.id,
    name: g.name,
    avatar: g.avatar,
    avatarColor: g.avatarColor,
    lastMessage: '',
    lastTime: '',
    unread: 0,
    isGroup: true,
  })
  router.push({ path: '/chat', query: { conversationId: g.id } })
}

/** 点击好友：创建/获取单聊会话并跳聊天页选中该好友 */
async function openFriendChat(f: FriendItem) {
  try {
    const conv = await getOrCreateDirectConversation(f.userId)
    chatStore.addConversation({
      id: conv.id,
      name: conv.name,
      avatar: conv.avatar ?? f.avatar,
      avatarColor: '#07C160',
      lastMessage: conv.lastMessage ?? '',
      lastTime: formatTime(conv.lastMessageAt),
      unread: 0,
      isGroup: false,
      friendId: f.userId,
    })
    router.push({ path: '/chat', query: { conversationId: conv.id } })
  } catch (e) {
    toast.error((e as Error).message)
  }
}

// 新朋友面板：搜索用户（真实接口）
const searchKeyword = ref('')
const searchResult = ref<FriendItem[]>([])
const searching = ref(false)

async function handleSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) {
    searchResult.value = []
    return
  }
  searching.value = true
  try {
    searchResult.value = await searchUser(kw)
  } catch {
    toast.error('搜索失败')
  } finally {
    searching.value = false
  }
}

/** 添加好友：发申请（对方无需验证则直接成为好友） */
async function handleAddFriend(u: FriendItem) {
  try {
    const { status } = await sendFriendRequest(u.userId, '')
    if (status === 'added') {
      toast.success(`已添加 ${u.nickname}`)
    } else {
      toast.success(`已向 ${u.nickname} 发送好友申请`)
    }
    searchResult.value = []
    searchKeyword.value = ''
    loadFriendPanel()
  } catch (e) {
    toast.error((e as Error).message)
  }
}

/** 接受/拒绝好友申请 */
async function handleRequestAction(req: FriendRequestItem, accept: boolean) {
  try {
    await handleFriendRequest(req.id, accept ? 1 : 2)
    toast.success(accept ? `已添加 ${req.fromName}` : '已拒绝')
    loadFriendPanel()
  } catch (e) {
    toast.error((e as Error).message)
  }
}

/** 头像：图片 URL 显示图片，否则首字色块 */
function avatarStyle(avatar: string): Record<string, string> {
  return ecAvatarStyle(avatar)
}

function isImageAvatar(avatar: string): boolean {
  return !!avatar && !avatar.startsWith('#')
}

const tabTitle: Record<Tab, string> = {
  newFriends: '新的朋友',
  myGroups: '我的群聊',
  joinedGroups: '我加入的群聊',
  friends: '我的好友',
}

// 创建群聊弹窗
const createGroupOpen = ref(false)

function onGroupCreated(_group: { id: string; name: string; avatar: string }) {
  loadGroups()
}

function onJoinRequestsChanged() {
  const count = joinRequestsPanelRef.value?.count() ?? 0
  syncJoinRequestBadge(count)
}
</script>

<template>
  <div class="flex h-full min-w-[960px]">
    <!-- 左侧菜单栏 -->
    <aside class="flex h-full w-[240px] shrink-0 flex-col border-r border-[var(--ec-border)] bg-[var(--ec-bg)] xl:w-[280px]">
      <div class="border-b border-[var(--ec-border)] px-4 py-4">
        <h2 class="text-base font-semibold text-[var(--ec-text)]">通讯录</h2>
      </div>
      <nav class="flex-1 overflow-y-auto py-2">
        <button
          v-for="item in tabItems"
          :key="item.key"
          type="button"
          class="ec-sidebar-item relative mx-2 w-[calc(100%-1rem)] rounded-[var(--ec-radius)]"
          :class="activeTab === item.key ? 'ec-sidebar-item-active' : ''"
          @click="activeTab = item.key"
        >
          <span
            v-if="activeTab === item.key"
            class="absolute bottom-2 left-0 top-2 w-[3px] rounded-full bg-[var(--ec-primary)]"
            aria-hidden="true"
          />
          <span class="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-[var(--ec-radius)] bg-[var(--ec-primary-soft)] text-[var(--ec-primary)]">
            <!-- 新朋友 -->
            <svg v-if="item.key === 'newFriends'" class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M15 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-7.5 1a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zm7.5 1c-2.7 0-8 1.3-8 4v2h16v-2c0-2.7-5.3-4-8-4zm-7.5-.5C5.3 13.5 3 15.4 3 17.5V19h4v-1.5c0-1 .3-1.8.9-2.5-.3-.1-.7-.2-1-.5z" />
            </svg>
            <!-- 群聊 -->
            <svg v-else-if="item.key === 'myGroups' || item.key === 'joinedGroups'" class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 3C6.5 3 2 6.9 2 11.7c0 2.6 1.3 5 3.5 6.6L4.6 21l3.3-1.7c1.3.4 2.7.6 4.1.6 5.5 0 10-3.9 10-8.7S17.5 3 12 3z" />
            </svg>
            <!-- 好友 -->
            <svg v-else class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-3.3 0-8 1.7-8 5v1h16v-1c0-3.3-4.7-5-8-5z" />
            </svg>
            <span
              v-if="item.badge"
              class="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-[var(--ec-danger)] px-1 text-[10px] leading-none text-white"
            >
              {{ item.badge }}
            </span>
          </span>
          <span class="text-[15px] text-[var(--ec-text)]">{{ item.label }}</span>
        </button>
      </nav>
    </aside>

    <!-- 右侧内容区 -->
    <main class="flex min-w-0 flex-1 flex-col bg-[var(--ec-bg-subtle)]">
      <header class="ec-page-header">
        <h1 class="ec-page-title">{{ tabTitle[activeTab] }}</h1>
      </header>

      <div class="flex-1 overflow-y-auto p-6">
        <!-- ========== 新朋友 ========== -->
        <div v-if="activeTab === 'newFriends'" class="mx-auto max-w-[640px]">
          <!-- 搜索好友 -->
          <div class="mb-6 ec-card">
            <p class="mb-3 text-sm font-medium text-[var(--ec-text)]">搜索好友</p>
            <div class="flex gap-2">
              <input
                v-model="searchKeyword"
                type="text"
                placeholder="输入对方昵称搜索"
                class="ec-input flex-1"
                @keyup.enter="handleSearch"
              />
              <button
                type="button"
                class="ec-btn-primary"
                @click="handleSearch"
              >
                搜索
              </button>
            </div>

            <!-- 搜索结果 -->
            <div v-if="searchResult.length" class="mt-3 space-y-2">
              <div
                v-for="f in searchResult"
                :key="f.userId"
                class="flex items-center gap-3 rounded-[var(--ec-radius)] border border-[var(--ec-border)] px-3 py-2.5"
              >
                <div
                  class="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
                  :style="avatarStyle(f.avatar)"
                >
                  <img v-if="isImageAvatar(f.avatar)" :src="f.avatar" class="h-full w-full object-cover" alt="头像" />
                  <span v-else>{{ f.nickname.charAt(0) }}</span>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm text-[var(--ec-text)]">{{ f.nickname }}</p>
                  <p class="truncate text-xs text-[var(--ec-text-faint)]">{{ f.signature || '暂无签名' }}</p>
                </div>
                <button
                  type="button"
                  class="cursor-pointer rounded-[var(--ec-radius)] border border-[var(--ec-primary)] px-3 py-1 text-xs text-[var(--ec-primary)] transition hover:bg-[var(--ec-primary)]/5"
                  @click="handleAddFriend(f)"
                >
                  添加好友
                </button>
              </div>
            </div>
            <p v-else-if="searchKeyword.trim() && !searching" class="mt-3 text-center text-sm text-[var(--ec-text-faint)]">
              未找到相关用户
            </p>
            <p v-else-if="searching" class="mt-3 text-center text-sm text-[var(--ec-text-faint)]">搜索中...</p>
          </div>

          <!-- 新的朋友（好友申请） -->
          <div class="ec-card">
            <p class="mb-3 text-sm font-medium text-[var(--ec-text)]">新的朋友</p>
            <div v-if="friendRequests.length" class="space-y-3">
              <div
                v-for="r in friendRequests"
                :key="r.id"
                class="flex items-center gap-3"
              >
                <div
                  class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
                  :style="avatarStyle(r.fromAvatar)"
                >
                  <img v-if="isImageAvatar(r.fromAvatar)" :src="r.fromAvatar" class="h-full w-full object-cover" alt="头像" />
                  <span v-else>{{ r.fromName.charAt(0) }}</span>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm text-[var(--ec-text)]">{{ r.fromName }}</p>
                  <p class="truncate text-xs text-[var(--ec-text-muted)]">{{ r.message || '请求添加你为好友' }}</p>
                </div>
                <span class="shrink-0 text-xs text-[var(--ec-text-faint)]">{{ formatTime(r.createdAt) }}</span>
                <div class="flex shrink-0 gap-2">
                  <button
                    type="button"
                    class="ec-btn-primary px-3 py-1 text-xs"
                    @click="handleRequestAction(r, true)"
                  >
                    接受
                  </button>
                  <button
                    type="button"
                    class="cursor-pointer rounded-[var(--ec-radius)] border border-[var(--ec-border)] px-3 py-1 text-xs text-[var(--ec-text-muted)] transition hover:bg-[var(--ec-bg-subtle)]"
                    @click="handleRequestAction(r, false)"
                  >
                    拒绝
                  </button>
                </div>
              </div>
            </div>
            <p v-else class="py-6 text-center text-sm text-[var(--ec-text-faint)]">暂无新的朋友申请</p>
          </div>
        </div>

        <!-- ========== 我的群聊（我创建的） ========== -->
        <div v-else-if="activeTab === 'myGroups'" class="mx-auto max-w-[640px]">
          <!-- 入群申请审批（群主/管理员） -->
          <div class="mb-4 rounded-2xl bg-[var(--ec-bg)] p-4 shadow-[0_1px_2px_rgba(26,46,36,0.04)]">
            <GroupJoinRequestsPanel ref="joinRequestsPanelRef" @changed="onJoinRequestsChanged" />
          </div>

          <button
            type="button"
            class="ec-btn-primary mb-4 flex w-full items-center justify-center gap-2 py-2.5 text-sm font-medium"
            @click="createGroupOpen = true"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M12 5v14M5 12h14" />
            </svg>
            新建群聊
          </button>

          <div class="ec-card">
            <p class="mb-3 text-sm font-medium text-[var(--ec-text)]">我创建的群聊</p>
            <div v-if="myGroups.length" class="space-y-3">
              <div
                v-for="g in myGroups"
                :key="g.id"
                class="flex cursor-pointer items-center gap-3 rounded-[var(--ec-radius)] px-2 py-1.5 transition-colors duration-150 hover:bg-[var(--ec-bg-subtle)]"
                @click="openGroupChat(g)"
              >
                <div
                  class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
                  :style="{ backgroundColor: g.avatar?.startsWith('#') ? g.avatar : g.avatarColor }"
                >
                  <img
                    v-if="g.avatar && !g.avatar.startsWith('#')"
                    :src="g.avatar"
                    class="h-full w-full object-cover"
                    alt="群头像"
                  />
                  <span v-else>群</span>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm text-[var(--ec-text)]">{{ g.name }}</p>
                  <p class="text-xs text-[var(--ec-text-faint)]">{{ g.members }} 人</p>
                </div>
              </div>
            </div>
            <p v-else class="py-6 text-center text-sm text-[var(--ec-text-faint)]">还没有创建群聊</p>
          </div>
        </div>

        <!-- ========== 我加入的群聊 ========== -->
        <div v-else-if="activeTab === 'joinedGroups'" class="mx-auto max-w-[640px]">
          <div class="ec-card">
            <p class="mb-3 text-sm font-medium text-[var(--ec-text)]">我加入的群聊</p>
            <p class="mb-4 text-xs leading-relaxed text-[var(--ec-text-faint)]">
              加入群聊需收到群成员邀请，邀请会出现在聊天列表的「群邀请」会话中。
            </p>
            <div v-if="joinedGroups.length" class="space-y-3">
              <div
                v-for="g in joinedGroups"
                :key="g.id"
                class="flex cursor-pointer items-center gap-3 rounded-[var(--ec-radius)] px-2 py-1.5 transition-colors duration-150 hover:bg-[var(--ec-bg-subtle)]"
                @click="openGroupChat(g)"
              >
                <div
                  class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
                  :style="{ backgroundColor: g.avatar?.startsWith('#') ? g.avatar : g.avatarColor }"
                >
                  <img
                    v-if="g.avatar && !g.avatar.startsWith('#')"
                    :src="g.avatar"
                    class="h-full w-full object-cover"
                    alt="群头像"
                  />
                  <span v-else>群</span>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm text-[var(--ec-text)]">{{ g.name }}</p>
                  <p class="text-xs text-[var(--ec-text-faint)]">{{ g.members }} 人</p>
                </div>
              </div>
            </div>
            <p v-else class="py-6 text-center text-sm text-[var(--ec-text-faint)]">还没有加入任何群聊</p>
          </div>
        </div>

        <!-- ========== 我的好友 ========== -->
        <div v-else class="mx-auto max-w-[640px]">
          <div class="ec-card">
            <div v-if="friends.length" class="space-y-3">
              <div
                v-for="f in friends"
                :key="f.userId"
                class="flex cursor-pointer items-center gap-3 rounded-[var(--ec-radius)] px-2 py-1.5 transition-colors duration-150 hover:bg-[var(--ec-bg-subtle)]"
                @click="openFriendChat(f)"
              >
                <div
                  class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
                  :style="avatarStyle(f.avatar)"
                >
                  <img v-if="isImageAvatar(f.avatar)" :src="f.avatar" class="h-full w-full object-cover" alt="头像" />
                  <span v-else>{{ f.nickname.charAt(0) }}</span>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm text-[var(--ec-text)]">{{ f.nickname }}</p>
                  <p class="truncate text-xs text-[var(--ec-text-faint)]">{{ f.signature || '暂无签名' }}</p>
                </div>
              </div>
            </div>
            <p v-else class="py-6 text-center text-sm text-[var(--ec-text-faint)]">还没有好友</p>
          </div>
        </div>
      </div>
    </main>

    <!-- 创建群聊弹窗 -->
    <CreateGroupModal
      v-if="createGroupOpen"
      @close="createGroupOpen = false"
      @created="onGroupCreated"
    />
  </div>
</template>

<style scoped></style>
