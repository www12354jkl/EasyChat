import { createRouter, createWebHistory } from 'vue-router'
import AuthView from '@/views/AuthView.vue'
import HomeView from '@/views/HomeView.vue'
import ProfileView from '@/views/ProfileView.vue'
import ContactsView from '@/views/ContactsView.vue'
import ChatView from '@/views/ChatView.vue'
import GroupDetailView from '@/views/GroupDetailView.vue'
import UserDetailView from '@/views/UserDetailView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: AuthView,
    },
    {
      path: '/',
      component: HomeView,
      children: [
        { path: '', redirect: { name: 'chat' } },
        {
          path: 'chat',
          name: 'chat',
          component: ChatView,
        },
        {
          path: 'group/:id',
          name: 'groupDetail',
          component: GroupDetailView,
        },
        {
          path: 'user/:id',
          name: 'userDetail',
          component: UserDetailView,
        },
        {
          path: 'contacts',
          name: 'contacts',
          component: ContactsView,
        },
        {
          path: 'profile',
          name: 'profile',
          component: ProfileView,
        },
      ],
    },
  ],
})

// 登录守卫：未登录访问受保护页 → /login；已登录访问 /login → 主页
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'login' && !auth.isLoggedIn) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'chat' }
  }
  return true
})

export default router
