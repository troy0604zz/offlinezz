<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { apiErrorMessage } from '../services/http'
import { useAuthStore } from '../stores/auth'
import { Permission } from '../types/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function defaultPath(): string {
  if (auth.hasPermission(Permission.DATA_QUERY)) return '/query'
  if (auth.hasPermission(Permission.SMART_REPORT)) return '/reports'
  if (auth.hasPermission(Permission.AI_TRAINING)) return '/training'
  return '/forbidden'
}

async function submit(): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : defaultPath()
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '登录失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-intro">
      <div class="login-intro__content">
        <span class="login-intro__eyebrow">ENTERPRISE AI BI</span>
        <h1>让企业数据<br><em>真正可以被提问</em></h1>
        <p>基于业务语义、标准 SQL 和受控查询流程，为团队提供可信的数据问答与智能报告。</p>
        <div class="capabilities">
          <span>自然语言问数</span><span>智能经营报告</span><span>企业知识训练</span>
        </div>
      </div>
      <footer>Vue 3 · Spring Boot · Oracle 19c · Qwen 3.5 · Qdrant</footer>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="login-card__brand"><span>AI</span><strong>企业数据智能平台</strong></div>
        <div class="login-card__heading"><h2>欢迎回来</h2><p>请使用管理员分配的账号登录</p></div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" :prefix-icon="User" size="large" autocomplete="username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" :prefix-icon="Lock" size="large" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit" />
          </el-form-item>
          <el-button class="login-button" type="primary" size="large" native-type="submit" :loading="loading">登录工作台</el-button>
        </el-form>
        <p class="security-note">账号权限由服务端统一控制。如需开通功能，请联系 AI 平台管理员。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page{min-height:100vh;display:grid;grid-template-columns:minmax(440px,1.15fr) minmax(440px,.85fr);background:var(--surface)}.login-intro{position:relative;padding:72px clamp(48px,7vw,110px);display:flex;flex-direction:column;justify-content:center;overflow:hidden;color:white;background:radial-gradient(circle at 18% 12%,#245b8f 0,transparent 35%),radial-gradient(circle at 80% 85%,#147f72 0,transparent 32%),#0f2744}.login-intro::before{content:"";position:absolute;inset:0;background-image:linear-gradient(#ffffff0a 1px,transparent 1px),linear-gradient(90deg,#ffffff0a 1px,transparent 1px);background-size:42px 42px;mask-image:linear-gradient(to bottom,#000,transparent)}.login-intro__content,.login-intro footer{position:relative}.login-intro__eyebrow{color:#5ce0be;font-size:12px;letter-spacing:.2em;font-weight:700}.login-intro h1{margin:24px 0;font-size:clamp(40px,5vw,68px);line-height:1.12;letter-spacing:-.04em}.login-intro h1 em{color:#56dbbb;font-style:normal}.login-intro p{max-width:620px;color:#b9c9d9;font-size:17px;line-height:1.8}.capabilities{display:flex;gap:10px;flex-wrap:wrap;margin-top:30px}.capabilities span{padding:8px 13px;border:1px solid #ffffff2b;border-radius:999px;background:#ffffff0b;font-size:12px;color:#d5e1eb}.login-intro footer{position:absolute;left:clamp(48px,7vw,110px);bottom:32px;color:#708ba4;font-size:11px}.login-panel{display:grid;place-items:center;padding:40px}.login-card{width:min(420px,100%)}.login-card__brand{display:flex;align-items:center;gap:11px}.login-card__brand span{width:38px;height:38px;border-radius:11px;display:grid;place-items:center;background:var(--primary);color:white;font-weight:800}.login-card__brand strong{color:var(--text-strong)}.login-card__heading{margin:52px 0 30px}.login-card__heading h2{margin:0 0 10px;font-size:30px}.login-card__heading p,.security-note{color:var(--text-muted)}.login-card :deep(.el-form-item){margin-bottom:22px}.login-card :deep(.el-form-item__label){font-weight:600;color:var(--text)}.login-button{width:100%;margin-top:4px;height:46px;font-weight:600}.security-note{text-align:center;margin:22px 0 0;font-size:11px;line-height:1.6}@media(max-width:900px){.login-page{grid-template-columns:1fr}.login-intro{display:none}.login-panel{min-height:100vh;padding:28px}}
.login-intro h1{color:white}
</style>
