<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getGroupDetail, kickGroupMember, quitGroup, updateGroupInfo, uploadGroupAvatar, setGroupMemberRole, clearConversationMessages } from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useToastStore } from '@/stores/toast'
import { ws } from '@/api/ws'
import ConfirmModal from '@/components/ConfirmModal.vue'
import GroupEditModal from '@/components/GroupEditModal.vue'
import ChatHistoryModal from '@/components/ChatHistoryModal.vue'
import AddGroupMembersModal from '@/components/AddGroupMembersModal.vue'
import AvatarCropperModal from '@/components/AvatarCropperModal.vue'
import GroupJoinRequestsPanel from '@/components/GroupJoinRequestsPanel.vue'
import type { ConversationRemovedEvent, ConversationUpdate, GroupDetail, GroupMember, GroupJoinRequestHandledEvent } from '@/types/chat'

const props = defineProps<{ id?: string }>()
const emit = defineEmits<{ close: [] }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const chatStore = useChatStore()
const toast = useToastStore()

const groupId = props.id ?? String(route.params.id)

function onBack() {
  if (props.id) {
    emit('close')
  } else {
    router.back()
  }
}

const detail = ref<GroupDetail | null>(null)
const avatarInput = ref<HTMLInputElement | null>(null)
const cropperFile = ref<File | null>(null)

async function loadDetail() {
  try {
    detail.value = await getGroupDetail(groupId)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '群信息加载失败')
  }
}

onMounted(() => {
  loadDetail()
  ws.on('CONVERSATION_REMOVED', handleRemovedFromGroup)
  ws.on('CONVERSATION_UPDATE', handleGroupUpdate)
  ws.on('GROUP_JOIN_REQUEST_HANDLED', handleJoinRequestHandled)
})

onUnmounted(() => {
  ws.off('CONVERSATION_REMOVED', handleRemovedFromGroup)
  ws.off('CONVERSATION_UPDATE', handleGroupUpdate)
  ws.off('GROUP_JOIN_REQUEST_HANDLED', handleJoinRequestHandled)
})

function handleJoinRequestHandled(e: GroupJoinRequestHandledEvent) {
  if (e?.conversationId === groupId && e.status === 1) {
    loadDetail()
  }
}

function handleGroupUpdate(c: ConversationUpdate) {
  if (c?.id === groupId && c.total != null) {
    loadDetail()
  }
}

function handleRemovedFromGroup(e: ConversationRemovedEvent) {
  if (e?.id !== groupId) return
  chatStore.removeConversation(groupId)
  toast.info('你已被移出群聊')
  onBack()
}

// ============ 权限：群主 / 管理员 ============
const me = computed(() => auth.user?.userId ?? '')
const isOwner = computed(() => detail.value?.ownerId === me.value)
const myRole = computed(
  () => detail.value?.members.find((m) => m.userId === me.value)?.role ?? 1,
)
const isAdmin = computed(() => isOwner.value || myRole.value === 2)

// ============ 成员：排序 + 搜索 + 折叠 ============
// 排序：群主(ownerId)最前 → 管理员(role=2) → 普通成员(role=1)
const sortedMembers = computed<GroupMember[]>(() => {
  if (!detail.value) return []
  return [...detail.value.members].sort((a, b) => {
    const aIsOwner = a.userId === detail.value!.ownerId ? 0 : 1
    const bIsOwner = b.userId === detail.value!.ownerId ? 0 : 1
    if (aIsOwner !== bIsOwner) return aIsOwner - bIsOwner
    return (b.role ?? 1) - (a.role ?? 1)
  })
})

const searchKeyword = ref('')
const membersExpanded = ref(false)

const filteredMembers = computed(() => {
  const kw = searchKeyword.value.trim()
  if (!kw) return sortedMembers.value
  return sortedMembers.value.filter((m) => m.nickname.includes(kw))
})

// 折叠时只显示一行（5 个）；展开显示全部（搜索结果）
const visibleMembers = computed(() =>
  membersExpanded.value ? filteredMembers.value : filteredMembers.value.slice(0, 5),
)
const hasMoreMembers = computed(() => filteredMembers.value.length > 5)

const manageableMembers = computed(() => {
  if (!detail.value) return []
  return sortedMembers.value.filter((m) => {
    if (m.userId === detail.value!.ownerId) return false
    if (m.userId === me.value) return false
    if (isOwner.value) return true
    return isAdmin.value && m.role !== 2
  })
})

// 头像：图片 URL 显示图片；空/色值显示昵称首字
function avatarStyle(m: GroupMember): Record<string, string> {
  return {
    backgroundColor: m.avatar?.startsWith('#') ? m.avatar : '#07C160',
  }
}

function isImageAvatar(m: GroupMember): boolean {
  return !!m.avatar && !m.avatar.startsWith('#')
}

// ============ 编辑群昵称 / 群公告（仅群主/管理员） ============
const editState = ref<{ field: 'name' | 'announcement'; value: string } | null>(null)

function openEditName() {
  if (!detail.value || !isAdmin.value) return
  editState.value = { field: 'name', value: detail.value.name }
}

function openEditAnnouncement() {
  if (!detail.value || !isAdmin.value) return
  editState.value = { field: 'announcement', value: detail.value.announcement }
}

async function saveEdit(value: string) {
  if (!detail.value || !editState.value) return
  const field = editState.value.field
  try {
    await updateGroupInfo(groupId, field === 'name' ? { name: value } : { announcement: value })
    if (field === 'name') {
      detail.value.name = value
    } else {
      detail.value.announcement = value
    }
    toast.success(field === 'name' ? '群昵称已更新' : '群公告已更新')
    editState.value = null
  } catch (e) {
    toast.error((e as Error).message)
  }
}

// ============ 入群验证方式（仅群主/管理员） ============
async function toggleJoinVerify() {
  if (!detail.value || !isAdmin.value) return
  const next = detail.value.joinVerify === 1 ? 0 : 1
  try {
    await updateGroupInfo(groupId, { joinVerify: next })
    detail.value.joinVerify = next
    toast.success(next === 1 ? '已开启入群验证' : '已关闭入群验证')
  } catch (e) {
    toast.error((e as Error).message)
  }
}

// ============ 聊天记录 ============
const chatHistoryOpen = ref(false)
const addMembersOpen = ref(false)

function openChatHistory() {
  chatHistoryOpen.value = true
}

function triggerAvatarSelect() {
  avatarInput.value?.click()
}

function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) {
    cropperFile.value = file
  }
}

async function handleCropped(file: File) {
  cropperFile.value = null
  try {
    const url = await uploadGroupAvatar(groupId, file)
    if (detail.value) {
      detail.value.avatar = url
    }
    toast.success('群头像已更新')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '头像上传失败')
  }
}

async function toggleAdmin(m: GroupMember) {
  const next = m.role === 2 ? 1 : 2
  try {
    await setGroupMemberRole(groupId, m.userId, next)
    await loadDetail()
    toast.success(next === 2 ? '已设为管理员' : '已取消管理员')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '操作失败')
  }
}

function canKick(m: GroupMember): boolean {
  if (!detail.value) return false
  if (m.userId === me.value) return false
  if (m.userId === detail.value.ownerId) return false
  if (isOwner.value) return true
  return isAdmin.value && m.role !== 2
}

// ============ 确认弹窗（删除聊天记录 / 退出群聊 / 移出成员） ============
const confirmState = ref<{ type: 'clear' | 'quit' | 'kick'; member?: GroupMember } | null>(null)

function openKickConfirm(m: GroupMember) {
  confirmState.value = { type: 'kick', member: m }
}

async function handleConfirm() {
  if (!confirmState.value) return
  const { type, member } = confirmState.value
  confirmState.value = null
  if (type === 'clear') {
    try {
      await clearConversationMessages(groupId)
      chatStore.updateConversation(groupId, { lastMessage: '', lastTime: '' })
      toast.success('聊天记录已清除')
    } catch (e) {
      toast.error((e as Error).message)
    }
  } else if (type === 'kick' && member) {
    try {
      await kickGroupMember(groupId, member.userId)
      await loadDetail()
      toast.success(`已将 ${member.nickname} 移出群聊`)
    } catch (e) {
      toast.error((e as Error).message)
    }
  } else {
    quitGroupAction()
  }
}

async function quitGroupAction() {
  try {
    await quitGroup(groupId)
    chatStore.removeConversation(groupId)
    toast.success('已退出群聊')
    if (props.id) {
      emit('close')
    } else {
      router.push('/chat')
    }
  } catch (e) {
    toast.error((e as Error).message)
  }
}
</script>

<template>
  <div class="flex h-full flex-col bg-[#F5F5F5]">
    <!-- 顶部：标题 + 关闭 -->
    <header class="flex h-[48px] shrink-0 items-center justify-between border-b border-[#E5E5E5] bg-white px-4">
      <h1 class="truncate text-base font-medium text-[#1A1A1A]">群聊信息</h1>
      <button
        type="button"
        class="flex h-9 w-9 cursor-pointer items-center justify-center rounded-md text-[#8A8A8A] transition-colors hover:bg-[#F5F5F5] hover:text-[#1A1A1A]"
        title="关闭"
        @click="onBack"
      >
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </header>

    <div class="flex-1 overflow-y-auto">
      <!-- 群成员卡片 -->
      <div class="bg-white px-4 py-4">
        <!-- 搜索栏 -->
        <div class="flex items-center gap-2 rounded-md bg-[#F7F7F7] px-3 py-1.5">
          <svg class="h-4 w-4 shrink-0 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" stroke-linecap="round" />
          </svg>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索群成员"
            class="w-full bg-transparent py-0.5 text-sm outline-none placeholder:text-gray-400"
          />
        </div>

        <!-- 成员标题 + 折叠开关 -->
        <div class="mt-4 flex items-center justify-between">
          <p class="text-sm font-medium text-[#1A1A1A]">群成员（{{ detail?.total ?? 0 }}）</p>
          <button
            v-if="hasMoreMembers"
            type="button"
            class="cursor-pointer text-xs text-[#B2B2B2] transition-colors hover:text-[#8A8A8A]"
            @click="membersExpanded = !membersExpanded"
          >
            {{ membersExpanded ? '收起' : `展开全部 ${filteredMembers.length} 人` }}
          </button>
        </div>

        <!-- 成员网格（末尾是添加成员按钮） -->
        <div class="mt-3 grid grid-cols-4 gap-x-2 gap-y-4">
          <div v-for="m in visibleMembers" :key="m.userId" class="flex min-w-0 flex-col items-center gap-1.5">
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
              :style="avatarStyle(m)"
            >
              <img v-if="isImageAvatar(m)" :src="m.avatar" class="h-full w-full object-cover" alt="头像" />
              <span v-else>{{ m.nickname.charAt(0) }}</span>
            </div>
            <p class="w-full truncate text-center text-xs text-[#1A1A1A]">{{ m.nickname }}</p>
            <p v-if="m.userId === detail?.ownerId || m.role === 2" class="w-full text-center text-[10px] text-[#B2B2B2]">
              {{ m.userId === detail?.ownerId ? '群主' : '管理员' }}
            </p>
          </div>

          <!-- 添加成员按钮 -->
          <div class="flex min-w-0 flex-col items-center gap-1.5">
            <button
              type="button"
              class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-[4px] bg-[#F0F0F0] text-[#07C160] transition-colors hover:bg-[#E5E5E5]"
              title="邀请成员"
              @click="addMembersOpen = true"
            >
              <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M12 5v14M5 12h14" />
              </svg>
            </button>
            <p class="w-full truncate text-center text-xs text-[#B2B2B2]">邀请</p>
          </div>
        </div>

        <!-- 成员管理（群主/管理员） -->
        <div v-if="isAdmin" class="mt-4 border-t border-[#F0F0F0] pt-3">
          <p class="text-sm font-medium text-[#1A1A1A]">成员管理</p>
          <div class="mt-2 space-y-1">
            <div
              v-for="m in manageableMembers"
              :key="m.userId"
              class="flex items-center gap-3 rounded-md px-1 py-1.5"
            >
              <div
                class="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-[4px] text-xs font-medium text-white"
                :style="avatarStyle(m)"
              >
                <img v-if="isImageAvatar(m)" :src="m.avatar" class="h-full w-full object-cover" alt="头像" />
                <span v-else>{{ m.nickname.charAt(0) }}</span>
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm text-[#1A1A1A]">{{ m.nickname }}</p>
                <p class="text-xs text-[#B2B2B2]">{{ m.role === 2 ? '管理员' : '成员' }}</p>
              </div>
              <div class="flex shrink-0 gap-2">
                <button
                  v-if="isOwner"
                  type="button"
                  class="cursor-pointer rounded-md border border-[#E5E5E5] px-3 py-1 text-xs text-[#576B95] transition-colors hover:bg-[#F5F5F5]"
                  @click="toggleAdmin(m)"
                >
                  {{ m.role === 2 ? '取消管理员' : '设为管理员' }}
                </button>
                <button
                  v-if="canKick(m)"
                  type="button"
                  class="cursor-pointer rounded-md border border-[#FA5151]/30 px-3 py-1 text-xs text-[#FA5151] transition-colors hover:bg-[#FFF5F5]"
                  @click="openKickConfirm(m)"
                >
                  移出群聊
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 群信息设置 -->
      <div class="mt-3 bg-white">
        <!-- 群头像 -->
        <div class="flex items-center justify-between px-4 py-3">
          <span class="text-sm text-[#1A1A1A]">群头像</span>
          <div class="flex items-center gap-3">
            <div
              class="flex h-10 w-10 items-center justify-center overflow-hidden rounded-[4px] text-sm font-medium text-white"
              :style="{ backgroundColor: detail?.avatar?.startsWith('#') ? detail.avatar : '#07C160' }"
            >
              <img
                v-if="detail?.avatar && !detail.avatar.startsWith('#')"
                :src="detail.avatar"
                class="h-full w-full object-cover"
                alt="群头像"
              />
              <span v-else>群</span>
            </div>
            <button
              v-if="isAdmin"
              type="button"
              class="cursor-pointer rounded-md border border-[#E5E5E5] px-3 py-1 text-xs text-[#576B95] transition-colors hover:bg-[#F5F5F5]"
              @click="triggerAvatarSelect"
            >
              更换
            </button>
          </div>
        </div>
        <div class="mx-4 border-t border-[#F0F0F0]"></div>

        <!-- 群昵称 -->
        <div class="flex items-center justify-between px-4 py-3">
          <span class="text-sm text-[#1A1A1A]">群昵称</span>
          <button
            v-if="isAdmin"
            type="button"
            class="flex max-w-[60%] cursor-pointer items-center gap-1 text-sm text-[#8A8A8A] transition-colors hover:text-[#1A1A1A]"
            @click="openEditName"
          >
            <span class="truncate">{{ detail?.name }}</span>
            <svg class="h-4 w-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </button>
          <span v-else class="max-w-[60%] truncate text-sm text-[#8A8A8A]">{{ detail?.name }}</span>
        </div>
        <div class="mx-4 border-t border-[#F0F0F0]"></div>

        <!-- 群公告 -->
        <div class="flex items-center justify-between px-4 py-3">
          <span class="text-sm text-[#1A1A1A]">群公告</span>
          <button
            v-if="isAdmin"
            type="button"
            class="flex max-w-[60%] cursor-pointer items-center gap-1 text-sm text-[#8A8A8A] transition-colors hover:text-[#1A1A1A]"
            @click="openEditAnnouncement"
          >
            <span class="truncate">{{ detail?.announcement || '暂无公告' }}</span>
            <svg class="h-4 w-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </button>
          <span v-else class="max-w-[60%] truncate text-sm text-[#8A8A8A]">{{ detail?.announcement || '暂无公告' }}</span>
        </div>
        <div v-if="isAdmin" class="mx-4 border-t border-[#F0F0F0]"></div>

        <!-- 入群验证方式（仅群主/管理员可见） -->
        <div v-if="isAdmin" class="flex items-center justify-between px-4 py-3">
          <span class="text-sm text-[#1A1A1A]">入群验证方式</span>
          <button
            type="button"
            class="flex cursor-pointer items-center gap-1 text-sm text-[#8A8A8A] transition-colors hover:text-[#1A1A1A]"
            @click="toggleJoinVerify"
          >
            {{ detail?.joinVerify === 1 ? '需要验证' : '无需验证' }}
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </button>
        </div>
        <div class="mx-4 border-t border-[#F0F0F0]"></div>

        <!-- 聊天记录 -->
        <div class="flex items-center justify-between px-4 py-3">
          <span class="text-sm text-[#1A1A1A]">聊天记录</span>
          <button
            type="button"
            class="flex cursor-pointer items-center gap-1 text-sm text-[#8A8A8A] transition-colors hover:text-[#1A1A1A]"
            @click="openChatHistory"
          >
            查看
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </button>
        </div>
      </div>

        <!-- 入群申请（仅群主/管理员） -->
        <div v-if="isAdmin" class="mt-3 bg-white px-4 py-4">
          <GroupJoinRequestsPanel :conversation-id="groupId" :show-title="true" />
        </div>

        <!-- 危险操作 -->
      <div class="mt-3 bg-white">
        <button
          type="button"
          class="w-full cursor-pointer px-4 py-3 text-left text-sm text-[#FA5151] transition-colors hover:bg-[#FFF5F5]"
          @click="confirmState = { type: 'clear' }"
        >
          删除聊天记录
        </button>
        <div class="mx-4 border-t border-[#F0F0F0]"></div>
        <button
          type="button"
          class="w-full cursor-pointer px-4 py-3 text-left text-sm text-[#FA5151] transition-colors hover:bg-[#FFF5F5]"
          @click="confirmState = { type: 'quit' }"
        >
          退出群聊
        </button>
      </div>
    </div>

    <!-- 编辑群昵称/公告弹窗 -->
    <GroupEditModal
      v-if="editState"
      :title="editState.field === 'name' ? '修改群昵称' : '修改群公告'"
      :value="editState.value"
      :field="editState.field"
      @save="saveEdit"
      @close="editState = null"
    />

    <!-- 确认弹窗 -->
    <ConfirmModal
      v-if="confirmState"
      :title="confirmState.type === 'clear' ? '删除聊天记录' : confirmState.type === 'kick' ? '移出群聊' : '退出群聊'"
      :message="confirmState.type === 'clear' ? '确定删除与该群的聊天记录吗？此操作不可恢复。' : confirmState.type === 'kick' ? `确定将 ${confirmState.member?.nickname} 移出群聊吗？` : '退出后你将不再接收该群的任何消息，确定退出吗？'"
      :confirm-text="confirmState.type === 'clear' ? '删除' : confirmState.type === 'kick' ? '移出' : '退出'"
      danger
      @confirm="handleConfirm"
      @cancel="confirmState = null"
    />
    <ChatHistoryModal
      v-if="chatHistoryOpen"
      :conversation-id="groupId"
      :name="detail?.name"
      @close="chatHistoryOpen = false"
    />
    <AddGroupMembersModal
      v-if="addMembersOpen"
      :group-id="groupId"
      :member-ids="detail?.members.map((m) => m.userId) ?? []"
      @close="addMembersOpen = false"
      @invited="loadDetail"
    />
    <AvatarCropperModal
      v-if="cropperFile"
      :file="cropperFile"
      @close="cropperFile = null"
      @cropped="handleCropped"
    />
    <input
      ref="avatarInput"
      type="file"
      accept="image/jpeg,image/png,image/gif,image/webp"
      class="hidden"
      @change="handleAvatarChange"
    />
  </div>
</template>

<style scoped></style>
