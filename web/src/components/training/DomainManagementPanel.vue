<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../services/http'
import { domainApi } from '../../services/domain-api'
import { useDomainStore } from '../../stores/domain'
import type { DomainDataSource, DomainMember } from '../../types/domain'

const store = useDomainStore()
const domainDialog = ref(false)
const memberDialog = ref(false)
const saving = ref(false)
const testing = ref(false)
const deletingMemberId = ref<number | null>(null)
const memberDeletePromptOpen = ref(false)
const members = ref<DomainMember[]>([])
const domainForm = reactive({ code: '', name: '', description: '', editing: false })
const sourceForm = reactive({ jdbcUrl: '', username: '', password: '', driverClass: 'oracle.jdbc.OracleDriver', validationQuery: 'SELECT 1 FROM DUAL', passwordConfigured: false })
const memberForm = reactive({ username: '', canQuery: true, canReport: false, canTrain: false })

function trueish(value: boolean | number): boolean { return value === true || Number(value) === 1 }
function newDomain(): void { Object.assign(domainForm, { code: '', name: '', description: '', editing: false }); domainDialog.value = true }
function editDomain(): void {
  if (!store.current) return
  Object.assign(domainForm, { code: store.current.code, name: store.current.name, description: store.current.description || '', editing: true }); domainDialog.value = true
}
async function saveDomain(): Promise<void> {
  saving.value = true
  try {
    if (domainForm.editing) await domainApi.update(domainForm.code, domainForm)
    else await domainApi.create(domainForm)
    await store.load(); store.select(domainForm.code); domainDialog.value = false
    ElMessage.success(domainForm.editing ? '数据域已更新' : '数据域已创建，请继续配置独立数据源')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) } finally { saving.value = false }
}
async function loadDetail(): Promise<void> {
  if (!store.selectedCode) return
  try {
    const [source, users] = await Promise.all([domainApi.dataSource(store.selectedCode), domainApi.members(store.selectedCode)])
    const data: DomainDataSource = source.data
    Object.assign(sourceForm, { jdbcUrl: data.jdbcUrl, username: data.username, password: '', driverClass: data.driverClass, validationQuery: data.validationQuery, passwordConfigured: data.passwordConfigured })
    members.value = users.data
  } catch (error) { ElMessage.error(apiErrorMessage(error, '数据域配置加载失败')) }
}
async function saveSource(): Promise<void> {
  saving.value = true
  try { await domainApi.updateDataSource(store.selectedCode, sourceForm); ElMessage.success('数据源配置已加密保存'); await loadDetail() }
  catch (error) { ElMessage.error(apiErrorMessage(error)) } finally { saving.value = false }
}
async function testSource(): Promise<void> {
  testing.value = true
  try { const result = (await domainApi.testDataSource(store.selectedCode)).data; ElMessage.success(`${result.message}：${result.database}`) }
  catch (error) { ElMessage.error(apiErrorMessage(error)) } finally { testing.value = false }
}
function addMember(): void { Object.assign(memberForm, { username: '', canQuery: true, canReport: false, canTrain: false }); memberDialog.value = true }
function editMember(row: DomainMember): void { Object.assign(memberForm, { username: row.username, canQuery: trueish(row.can_query), canReport: trueish(row.can_report), canTrain: trueish(row.can_train) }); memberDialog.value = true }
async function saveMember(): Promise<void> {
  saving.value = true
  try { await domainApi.saveMember(store.selectedCode, memberForm); memberDialog.value = false; ElMessage.success('域内权限已保存'); await loadDetail() }
  catch (error) { ElMessage.error(apiErrorMessage(error)) } finally { saving.value = false }
}
async function deleteMember(row: DomainMember): Promise<void> {
  if (deletingMemberId.value !== null || memberDeletePromptOpen.value) return
  memberDeletePromptOpen.value = true
  try {
    await ElMessageBox.confirm(`确定移除 ${row.username} 对当前数据域的全部访问权限吗？`, '移除成员', { type: 'warning' })
    deletingMemberId.value = row.id
    await domainApi.deleteMember(store.selectedCode, row.id)
    await loadDetail()
    ElMessage.success('成员已移除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error))
  } finally {
    memberDeletePromptOpen.value = false
    deletingMemberId.value = null
  }
}

onMounted(loadDetail)
watch(() => store.selectedCode, loadDetail)
</script>

<template>
  <div class="domain-panel">
    <section class="domain-heading">
      <div><h2>数据域与隔离边界</h2><p>当前域的知识、语义、SQL 案例、问答记录、报告和数据源全部独立保存。</p></div>
      <div><el-button @click="editDomain">编辑当前域</el-button><el-button type="primary" @click="newDomain">创建数据域</el-button></div>
    </section>
    <el-alert v-if="sourceForm.jdbcUrl === 'UNCONFIGURED'" title="当前数据域尚未配置数据源，问答和报告会被安全阻止。" type="warning" show-icon :closable="false" />
    <div class="domain-grid">
      <section class="config-card">
        <div class="card-title"><div><h3>一对一数据源</h3><p>密码只在后端加密保存，页面不会回显。</p></div><el-tag>{{ store.current?.name }}</el-tag></div>
        <el-form label-position="top">
          <el-form-item label="Oracle JDBC URL"><el-input v-model="sourceForm.jdbcUrl" placeholder="jdbc:oracle:thin:@//host:1521/service" /></el-form-item>
          <div class="form-grid"><el-form-item label="用户名"><el-input v-model="sourceForm.username" /></el-form-item><el-form-item :label="sourceForm.passwordConfigured ? '密码（留空保持原密码）' : '密码'"><el-input v-model="sourceForm.password" type="password" show-password /></el-form-item></div>
          <el-form-item label="JDBC 驱动"><el-input v-model="sourceForm.driverClass" /></el-form-item>
          <el-form-item label="连接校验 SQL"><el-input v-model="sourceForm.validationQuery" /></el-form-item>
          <div class="actions"><el-button :loading="testing" @click="testSource">测试现有连接</el-button><el-button type="primary" :loading="saving" @click="saveSource">保存数据源</el-button></div>
        </el-form>
      </section>
      <section class="config-card">
        <div class="card-title"><div><h3>域内成员</h3><p>功能角色和域内权限同时满足时才能访问。</p></div><el-button type="primary" plain @click="addMember">添加成员</el-button></div>
        <el-table :data="members" empty-text="暂无域内成员" v-loading="deletingMemberId !== null" element-loading-text="正在移除成员，请稍候…">
          <el-table-column prop="username" label="账号" min-width="130" /><el-table-column prop="display_name" label="姓名" min-width="120" />
          <el-table-column label="问答" width="65"><template #default="scope"><el-icon color="#21a67a"><span>{{ trueish(scope.row.can_query) ? '✓' : '—' }}</span></el-icon></template></el-table-column>
          <el-table-column label="报告" width="65"><template #default="scope">{{ trueish(scope.row.can_report) ? '✓' : '—' }}</template></el-table-column>
          <el-table-column label="训练" width="65"><template #default="scope">{{ trueish(scope.row.can_train) ? '✓' : '—' }}</template></el-table-column>
          <el-table-column label="操作" width="140"><template #default="scope"><el-button link :disabled="deletingMemberId !== null" @click="editMember(scope.row)">编辑</el-button><el-button link type="danger" :loading="deletingMemberId === scope.row.id" :disabled="deletingMemberId !== null && deletingMemberId !== scope.row.id" @click="deleteMember(scope.row)">{{ deletingMemberId === scope.row.id ? '移除中' : '移除' }}</el-button></template></el-table-column>
        </el-table>
      </section>
    </div>
  </div>

  <el-dialog v-model="domainDialog" :title="domainForm.editing ? '编辑数据域' : '创建数据域'" width="min(600px, 92vw)">
    <el-form label-position="top"><el-form-item label="域编码"><el-input v-model="domainForm.code" :disabled="domainForm.editing" placeholder="例如 finance" /></el-form-item><el-form-item label="域名称"><el-input v-model="domainForm.name" /></el-form-item><el-form-item label="说明"><el-input v-model="domainForm.description" type="textarea" :rows="3" /></el-form-item></el-form>
    <template #footer><el-button @click="domainDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDomain">保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="memberDialog" title="域内成员权限" width="min(560px, 92vw)">
    <el-form label-position="top"><el-form-item label="登录账号"><el-input v-model="memberForm.username" /></el-form-item><el-form-item label="允许使用"><el-checkbox v-model="memberForm.canQuery">数据问答</el-checkbox><el-checkbox v-model="memberForm.canReport">智能报告</el-checkbox><el-checkbox v-model="memberForm.canTrain">AI 训练</el-checkbox></el-form-item></el-form>
    <template #footer><el-button @click="memberDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveMember">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>.domain-panel{display:grid;gap:20px}.domain-heading,.card-title,.actions{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.domain-heading h2,.card-title h3{margin:0 0 6px}.domain-heading p,.card-title p{margin:0;color:var(--text-muted);font-size:13px}.domain-grid{display:grid;grid-template-columns:1fr 1.15fr;gap:18px}.config-card{padding:22px;border:1px solid var(--border);border-radius:12px;background:var(--surface-subtle)}.card-title{margin-bottom:20px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.actions{justify-content:flex-end}@media(max-width:1050px){.domain-grid{grid-template-columns:1fr}}@media(max-width:620px){.domain-heading,.card-title{flex-direction:column}.form-grid{grid-template-columns:1fr;gap:0}}</style>
