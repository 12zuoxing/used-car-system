<!-- @author pangzhihao -->
<script setup>
import { computed, ref, watch } from 'vue' // 管理受保护的贷款详情与办理表单。
import { useRoute } from 'vue-router' // 读取后台详情路由中的申请编号。
import Button from 'primevue/button' // 为提交提供加载和禁用状态。
import Message from 'primevue/message' // 展示处理成功、冲突或访问错误。
import { loanApi } from '../../api/loans.js' // 调用后台授权的详情与状态更新接口。
import LoanDetails from './LoanDetails.vue' // 先展示真实申请资料供工作人员核对。
import { loanActions } from './loanLabels.js' // 状态决定可进行的业务操作。

const route = useRoute() // 从当前后台路径取得登记编号。
const loan = ref(null) // 保存服务端返回的最新申请版本。
const next = ref('') // 记录工作人员希望进入的办理状态。
const note = ref('') // 保存给买家查看的办理说明。
const loading = ref(false) // 加载资料期间显示等待状态。
const saving = ref(false) // 写请求期间停止重复提交。
const error = ref('') // 展示处理失败的具体原因。
const notice = ref('') // 展示本次办理成功的反馈。
const actions = computed(() => loanActions[loan.value?.status]) // 终态没有可用的状态变更。
const noteRequired = computed(() => Boolean(next.value) && next.value !== 'PROCESSING') // 补充、结案和关闭需要明确说明。
let version = 0 // 避免其他记录的返回数据覆盖当前办理页。

function resetForm() {
  next.value = '' // 每次处理前要求工作人员明确选择动作。
  note.value = '' // 清除之前的待提交说明。
}

async function load() {
  const current = ++version // 为这次加载分配页面版本。
  const id = route.params.id // 保存生效的申请编号。
  loan.value = null // 新详情加载前清理旧资料。
  loading.value = true // 展示最新资料加载状态。
  error.value = '' // 清理旧记录的错误。
  notice.value = '' // 清理旧记录的成功提示。
  resetForm() // 确保处理动作不会从另一个申请沿用。
  try {
    const result = await loanApi.staffDetail(id) // 通过后台接口读取带审计版本的详情。
    if (version === current) loan.value = result // 只采用与当前记录匹配的响应。
  } catch (failure) {
    if (version === current) error.value = failure.message // 只有当前记录加载失败时显示错误。
  } finally {
    if (version === current) loading.value = false // 本次请求结束。
  }
}

async function handle() {
  if (saving.value || !loan.value || !actions.value?.[next.value]) return // 只提交当前允许的动作。
  if (noteRequired.value && !note.value.trim()) {
    error.value = '请填写办理说明。' // 拒绝仅输入空白的补充或处理结果。
    return // 保留当前选择，等待工作人员补充。
  }
  const current = version // 防止对旧路由的响应用到新页面。
  const record = loan.value // 保存要处理的申请编号与已读版本。
  saving.value = true // 提交期间锁定表单。
  error.value = '' // 清除上次失败提示。
  notice.value = '' // 显示即将执行的新动作结果。
  try {
    const result = await loanApi.updateStatus(record.id, {
      status: next.value, // 传入本次明确选择的办理结果。
      resultNote: note.value.trim(), // 保存清理后的经办说明。
      revision: record.revision, // 标明工作人员核对的是这一轮资料。
    })
    if (version !== current) return // 如果工作人员已离开此申请，则丢弃旧响应。
    loan.value = result // 以服务端保存的状态重新显示页面。
    resetForm() // 处理后重置表单，避免误应用下一次动作。
    notice.value = '办理状态已更新。' // 告知工作人员保存成功。
  } catch (failure) {
    if (version !== current) return // 旧记录的错误不应显示在当前页。
    if (failure.status === 409) await load() // 资料发生变化时重新加载，待核对后才能再次提交。
    if (version === current || (version === current + 1 && loan.value?.id === record.id)) error.value = failure.message // 只为正在看的申请显示失败原因。
  } finally {
    saving.value = false // 请求完成后释放表单。
  }
}

watch(() => route.params.id, load, { immediate: true }) // 刷新或切换到其他贷款记录时重新验证权限。
</script>

<template>
  <!-- 工作人员先看申请内容，再更新办理状态。 -->
  <section class="booking-section" aria-labelledby="loan-review-title">
    <RouterLink class="vehicle-back" :to="{ name: 'loan-queue' }">← 返回贷款办理</RouterLink>
    <header class="page-heading"><h1 id="loan-review-title">贷款登记详情</h1></header>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <p v-if="loading" class="vehicle-empty" role="status">正在读取贷款资料…</p>
    <template v-else-if="loan">
      <!-- 授权详情同时显示申请数据、办理说明与关联订单。 -->
      <section class="vehicle-panel booking-section">
        <LoanDetails :loan="loan" managed />
        <RouterLink :to="{ name: 'admin-order-detail', params: { id: loan.orderId } }">查看关联订单</RouterLink>
      </section>
      <section v-if="actions" class="vehicle-panel booking-section" aria-labelledby="loan-handling-title">
        <h2 id="loan-handling-title">更新办理进度</h2>
        <!-- 只能选取从当前状态可达的办理动作。 -->
        <form class="vehicle-form" @submit.prevent="handle">
          <label>处理结果
            <select v-model="next" :disabled="saving" required>
              <option value="" disabled>请选择</option>
              <option v-for="(label, state) in actions" :key="state" :value="state">{{ label }}</option>
            </select>
          </label>
          <label class="vehicle-form-wide">办理说明
            <textarea v-model="note" maxlength="500" rows="4" :required="noteRequired" :disabled="saving" placeholder="说明需要补充的内容或办理结果" />
          </label>
          <div class="vehicle-form-wide"><Button type="submit" label="保存办理结果" :loading="saving" /></div>
        </form>
      </section>
    </template>
  </section>
</template>
