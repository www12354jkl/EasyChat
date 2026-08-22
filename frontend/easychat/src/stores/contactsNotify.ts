import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getGroupJoinRequests } from '@/api/chat'
import { getFriendRequests } from '@/api/user'

/** 通讯录待办角标（好友申请 + 入群申请） */
export const useContactsNotifyStore = defineStore('contactsNotify', () => {
  const friendRequestCount = ref(0)
  const groupJoinRequestCount = ref(0)

  const totalBadge = computed(() => friendRequestCount.value + groupJoinRequestCount.value)

  async function refresh() {
    try {
      const [friendReqs, joinReqs] = await Promise.all([getFriendRequests(), getGroupJoinRequests()])
      friendRequestCount.value = friendReqs.length
      groupJoinRequestCount.value = joinReqs.length
    } catch {
      // 静默失败，保留当前角标
    }
  }

  function onFriendRequest() {
    friendRequestCount.value += 1
  }

  function onFriendRequestHandled() {
    friendRequestCount.value = Math.max(0, friendRequestCount.value - 1)
  }

  function onGroupJoinRequest() {
    groupJoinRequestCount.value += 1
  }

  function onGroupJoinRequestHandled() {
    groupJoinRequestCount.value = Math.max(0, groupJoinRequestCount.value - 1)
  }

  return {
    friendRequestCount,
    groupJoinRequestCount,
    totalBadge,
    refresh,
    onFriendRequest,
    onFriendRequestHandled,
    onGroupJoinRequest,
    onGroupJoinRequestHandled,
  }
})
