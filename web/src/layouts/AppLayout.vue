<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatLineRound, DataAnalysis, Setting, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { platformApi } from '../services/platform-api'
import { useAuthStore } from '../stores/auth'
import { useDomainStore } from '../stores/domain'
import { Permission, type PermissionCode } from '../types/auth'
import type { PlatformInfo } from '../types/platform'

interface NavigationItem {
  label: string
  path: string
  permission: PermissionCode
  icon: typeof ChatLineRound
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const domainStore = useDomainStore()
const platform = ref<PlatformInfo | null>(null)

const navigation: NavigationItem[] = [
  { label: '数据问答', path: '/query', permission: Permission.DATA_QUERY, icon: ChatLineRound },
  { label: '智能报告', path: '/reports', permission: Permission.SMART_REPORT, icon: DataAnalysis },
  { label: 'AI 训练中心', path: '/training', permission: Permission.AI_TRAINING, icon: Setting },
]
const allowedNavigation = computed(() => navigation.filter((item) => auth.hasPermission(item.permission)))

async function signOut(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}

async function loadPlatform(): Promise<void> {
  try {
    platform.value = (await platformApi.info()).data
  } catch {
    platform.value = null
  }
}

onMounted(() => {
  loadPlatform()
  domainStore.load()
  window.addEventListener('model-runtime-changed', loadPlatform)
})
onUnmounted(() => window.removeEventListener('model-runtime-changed', loadPlatform))
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/">
        <span class="brand__mark">AI</span>
        <span><strong>企业数据智能</strong><small>AI BI · NL2SQL</small></span>
      </RouterLink>

      <p class="sidebar__label">工作台</p>
      <nav class="navigation" aria-label="主导航">
        <RouterLink v-for="item in allowedNavigation" :key="item.path" :to="item.path" :class="{ active: route.path.startsWith(item.path) }">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="runtime-status">
        <div><i :class="{ online: platform }"></i><strong>{{ platform ? '服务运行正常' : '服务状态未知' }}</strong></div>
        <p v-if="platform">{{ platform.chatProvider }} / {{ platform.chatModel }}<br>{{ platform.embeddingProvider }} / {{ platform.embeddingModel }}</p>
        <p v-else>请检查后端服务连接</p>
      </div>
    </aside>

    <section class="app-content">
      <div class="topbar">
        <el-select v-model="domainStore.selectedCode" class="domain-switcher" placeholder="请选择数据域" :loading="domainStore.loading" @change="domainStore.select">
          <el-option v-for="domain in domainStore.domains" :key="domain.code" :label="domain.name" :value="domain.code" />
        </el-select>
        <el-dropdown trigger="click">
          <button class="user-menu">
            <span class="user-menu__avatar"><el-icon><UserFilled /></el-icon></span>
            <span><strong>{{ auth.user?.displayName }}</strong><small>{{ auth.user?.username }}</small></span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :icon="SwitchButton" @click="signOut">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <main><RouterView /></main>
    </section>
  </div>
</template>

<style scoped>
.app-shell{min-height:100vh;display:grid;grid-template-columns:260px minmax(0,1fr)}.sidebar{position:sticky;top:0;height:100vh;padding:28px 20px 22px;background:#10223c;color:white;display:flex;flex-direction:column}.brand{display:flex;align-items:center;gap:12px;color:white;text-decoration:none;padding:0 8px}.brand__mark{width:42px;height:42px;display:grid;place-items:center;border-radius:12px;background:linear-gradient(145deg,#2dd4a4,#20aa88);font-size:15px;font-weight:800;box-shadow:0 8px 20px #0a142b4d}.brand strong,.brand small{display:block}.brand strong{font-size:17px}.brand small{margin-top:4px;color:#91a5bf;font-size:11px;font-weight:500;letter-spacing:.04em}.sidebar__label{margin:44px 12px 10px;color:#667f9e;font-size:11px;font-weight:700;letter-spacing:.12em}.navigation{display:grid;gap:6px}.navigation a{height:46px;padding:0 13px;border-radius:10px;display:flex;align-items:center;gap:12px;color:#aebcd0;text-decoration:none;font-size:14px;transition:.18s ease}.navigation a:hover,.navigation a.active{color:white;background:#213d63}.navigation a.active{box-shadow:inset 3px 0 #32cca5}.navigation .el-icon{font-size:18px}.runtime-status{margin-top:auto;padding:15px;border:1px solid #294668;border-radius:11px;background:#172f51}.runtime-status>div{display:flex;align-items:center;gap:8px;font-size:12px}.runtime-status i{width:8px;height:8px;border-radius:50%;background:#71849a}.runtime-status i.online{background:#31d19e;box-shadow:0 0 0 4px #31d19e1f}.runtime-status p{margin:8px 0 0;color:#849ab6;font-size:11px;line-height:1.7;overflow-wrap:anywhere}.app-content{min-width:0}.topbar{height:64px;padding:0 40px;background:var(--surface);border-bottom:1px solid var(--border);display:flex;align-items:center;justify-content:space-between}.topbar__domain{padding:6px 12px;border-radius:999px;background:var(--primary-soft);color:var(--primary);font-size:12px}.user-menu{border:0;background:transparent;display:flex;align-items:center;gap:10px;cursor:pointer;color:var(--text)}.user-menu__avatar{width:34px;height:34px;border-radius:10px;background:#e7eef9;color:#46627e;display:grid;place-items:center}.user-menu strong,.user-menu small{display:block;text-align:left}.user-menu strong{font-size:13px}.user-menu small{margin-top:2px;color:var(--text-subtle);font-size:11px}@media(max-width:900px){.app-shell{grid-template-columns:78px minmax(0,1fr)}.sidebar{padding:24px 12px}.brand{padding:0;justify-content:center}.brand>span:last-child,.sidebar__label,.navigation span,.runtime-status{display:none}.navigation{margin-top:42px}.navigation a{justify-content:center;padding:0}.topbar{padding:0 18px}}@media(max-width:560px){.app-shell{display:block}.sidebar{position:fixed;z-index:20;left:0;right:0;bottom:0;top:auto;width:auto;height:62px;padding:8px 14px;display:block}.brand,.sidebar__label,.runtime-status{display:none}.navigation{margin:0;display:flex;justify-content:space-around}.navigation a{width:56px;height:46px}.app-content{padding-bottom:62px}.topbar{height:56px}.topbar__domain{display:none}}
</style>
