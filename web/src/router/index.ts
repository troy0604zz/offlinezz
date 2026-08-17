import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'
import { Permission, type PermissionCode } from '../types/auth'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    permission?: PermissionCode
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true, title: '登录' } },
    {
      path: '/',
      component: () => import('../layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: '/query' },
        { path: 'query', name: 'query', component: () => import('../views/QueryView.vue'), meta: { permission: Permission.DATA_QUERY, title: '数据问答' } },
        { path: 'reports', name: 'reports', component: () => import('../views/ReportView.vue'), meta: { permission: Permission.SMART_REPORT, title: '智能报告' } },
        { path: 'training', name: 'training', component: () => import('../views/training/TrainingCenterView.vue'), meta: { permission: Permission.AI_TRAINING, title: 'AI 训练中心' } },
        { path: 'forbidden', name: 'forbidden', component: () => import('../views/ForbiddenView.vue'), meta: { title: '无访问权限' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

function firstAllowedRoute(auth: ReturnType<typeof useAuthStore>): string {
  if (auth.hasPermission(Permission.DATA_QUERY)) return '/query'
  if (auth.hasPermission(Permission.SMART_REPORT)) return '/reports'
  if (auth.hasPermission(Permission.AI_TRAINING)) return '/training'
  return '/forbidden'
}

router.beforeEach(async (to: RouteLocationNormalized) => {
  const auth = useAuthStore(pinia)
  await auth.restoreSession()

  if (to.meta.public) {
    return to.name === 'login' && auth.authenticated ? firstAllowedRoute(auth) : true
  }
  if (!auth.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (!auth.hasPermission(to.meta.permission)) return { name: 'forbidden' }
  if (to.path === '/') return firstAllowedRoute(auth)
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '工作台'} · 企业 AI BI`
})

export default router
