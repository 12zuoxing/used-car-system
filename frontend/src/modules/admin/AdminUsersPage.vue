<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { http } from '../../api/http.js'
import { auth, roleLabels } from '../auth/session.js'

const router = useRouter()
const users = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const dialogError = ref('')
const dialogVisible = ref(false)
const editingRoles = ref(false)
const target = ref(null)
const roleDraft = ref([])
let loadVersion = 0

async function onSessionFailure(error) {
  if (error.status !== 401 && error.status !== 403) return false
  await auth.restoreSession(true)
  if (!auth.state.account || !auth.isAdministrator()) {
    await router.replace({ name: auth.state.account ? 'account' : 'login' })
    return true
  }
  return false
}

/** 表格分页使用服务端结果。慢请求返回时不能覆盖刚切换到的新页。 */
async function load(nextPage = page.value) {
  const version = ++loadVersion
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await http.request('/admin/users?page=' + nextPage + '&pageSize=' + pageSize)
    if (version === loadVersion) {
      users.value = data.items
      total.value = data.total
      page.value = data.page
    }
  } catch (error) {
    if (version === loadVersion) {
      errorMessage.value = error.message || '读取账号失败。'
      await onSessionFailure(error).catch(() => {})
    }
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function edit(account, roles) {
  target.value = account
  editingRoles.value = roles
  roleDraft.value = [...account.roles]
  dialogError.value = ''
  dialogVisible.value = true
}

async function save() {
  if (saving.value || !target.value) return
  saving.value = true
  dialogError.value = ''
  const account = target.value
  const suffix = editingRoles.value ? 'roles' : 'active'
  const body = editingRoles.value ? { roles: roleDraft.value } : { active: !account.active }
  try {
    const saved = await http.request('/admin/users/' + account.id + '/' + suffix, { method: 'PUT', body })
    users.value = users.value.map((item) => item.id === saved.id ? saved : item)
    dialogVisible.value = false
    // 管理员修改本人时立即同步导航与当前页面，不继续保留过期的管理入口。
    if (auth.state.account?.id === saved.id) {
      await auth.restoreSession(true)
      if (!auth.isAdministrator()) {
        await router.replace({ name: auth.state.account ? 'account' : 'login' })
      }
    }
  } catch (error) {
    dialogError.value = error.message || '更新账号失败。'
    if (!dialogVisible.value) errorMessage.value = dialogError.value
    await onSessionFailure(error).catch(() => {})
  } finally {
    saving.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <section aria-labelledby="user-admin-title">
    <header class="page-heading">
      <h1 id="user-admin-title">账号管理</h1>
      <p>管理账号状态和系统角色</p>
    </header>
    <Message v-if="errorMessage" severity="error" :closable="false">
      {{ errorMessage }} <button type="button" class="text-button" @click="load()">重试</button>
    </Message>

    <DataTable :value="users" data-key="id" :loading="loading" :rows="pageSize"
      :first="(page - 1) * pageSize" :total-records="total" lazy paginator striped-rows scrollable
      :table-style="{ minWidth: '570px' }" paginator-template="PrevPageLink PageLinks NextPageLink"
      @page="load($event.page + 1)">
      <template #empty>暂无账号。</template>
      <Column field="username" header="用户名" />
      <Column field="displayName" header="显示名称" />
      <Column header="角色">
        <template #body="{ data }">
          <div class="role-list"><Tag v-for="role in data.roles" :key="role" :value="roleLabels[role]" severity="info" /></div>
        </template>
      </Column>
      <Column header="状态">
        <template #body="{ data }"><Tag :value="data.active ? '启用' : '停用'" :severity="data.active ? 'success' : 'secondary'" /></template>
      </Column>
      <Column header="操作">
        <template #body="{ data }">
          <div class="table-actions">
            <Button label="调整角色" size="small" variant="text" @click="edit(data, true)" />
            <Button :label="data.active ? '停用' : '启用'" size="small" variant="text" :severity="data.active ? 'danger' : 'secondary'" @click="edit(data, false)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <p class="table-total" aria-live="polite">共 {{ total }} 个账号</p>

    <Dialog v-model:visible="dialogVisible" modal :closable="!saving" :close-on-escape="!saving"
      :header="editingRoles ? '调整账号角色' : (target?.active ? '停用账号' : '启用账号')"
      :style="{ width: 'min(420px, 92vw)' }">
      <template v-if="target">
        <p class="dialog-account">{{ target.displayName }} · {{ target.username }}</p>
        <fieldset v-if="editingRoles" class="role-options">
          <legend>系统角色</legend>
          <label v-for="(label, role) in roleLabels" :key="role" :for="'account-role-' + role">
            <Checkbox v-model="roleDraft" :input-id="'account-role-' + role" :value="role" />
            {{ label }}
          </label>
        </fieldset>
        <p v-else-if="target.active" class="dialog-text">停用后，该账号现有会话将失效。</p>
        <p v-else class="dialog-text">启用后，该账号可以重新登录。</p>
        <Message v-if="dialogError" severity="error" :closable="false">{{ dialogError }}</Message>
      </template>
      <template #footer>
        <Button label="取消" severity="secondary" variant="outlined" :disabled="saving" @click="dialogVisible = false" />
        <Button :label="editingRoles ? '保存角色' : '确认'" :loading="saving" :disabled="editingRoles && roleDraft.length === 0" @click="save" />
      </template>
    </Dialog>
  </section>
</template>
