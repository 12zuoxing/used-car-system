<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue' // 在管理工作台挂载时显示最新队列快照。
import Button from 'primevue/button' // 提供手动更新统计的入口。
import Message from 'primevue/message' // 反馈统计读取失败。
import { operationsApi } from '../../api/operations.js' // 读取当前真实业务数据。
import { auth, roleLabels } from '../auth/session.js' // 显示当前后台操作账号的身份。

const data = ref(null) // 统计读取成功前，不显示容易被误认为真实值的零。
const loading = ref(false) // 防止快速点击多次刷新。
const error = ref('') // 保存统计接口错误。
const groups = [
  { label: '车源审核', route: 'vehicle-review', states: [{ key: 'pendingVehicles', label: '待审核', status: 'PENDING' }] }, // 待审车源对应车辆审核列表。
  { label: '在办订单', route: 'order-queue', states: [
    { key: 'lockedOrders', label: '待支付', status: 'LOCKED' }, // 车源已锁定，买家尚待付款。
    { key: 'paidOrders', label: '待过户确认', status: 'PAID' }, // 待工作人员确认成交。
  ] },
  { label: '贷款待办', route: 'loan-queue', states: [
    { key: 'pendingLoans', label: '待处理', status: 'PENDING' }, // 首次登记和补充后重新提交的队列。
    { key: 'processingLoans', label: '办理中', status: 'PROCESSING' }, // 工作人员已开始办理的队列。
  ] },
  { label: '整备与送修待办', route: 'repair-queue', states: [
    { key: 'pendingRepairs', label: '待受理', status: 'PENDING' }, // 等待工作人员接单的工单。
    { key: 'processingRepairs', label: '处理中', status: 'PROCESSING' }, // 已接单且尚未完工的工单。
  ] },
]

async function load() {
  if (loading.value) return // 请求未结束时避免重复读取。
  loading.value = true // 统计按钮显示等待状态。
  error.value = '' // 移除上一次刷新失败提示。
  try {
    data.value = await operationsApi.dashboard() // 直接使用后端快照。
  } catch (failure) {
    error.value = failure.message // 显示有效的错误原因以便用户重试。
  } finally {
    loading.value = false // 恢复刷新按钮。
  }
}

function total(group) {
  return group.states.reduce((count, state) => count + data.value[state.key], 0) // 汇总同一业务下对应的明细数量。
}

onMounted(load) // 进入管理端首页时显示当前待办。
</script>

<template>
  <!-- 工作台按业务组织实际待办，状态行能直接打开对应队列。 -->
  <section class="booking-section" aria-labelledby="workspace-title">
    <header class="page-heading vehicle-section-head">
      <div>
        <h1 id="workspace-title">管理工作台</h1>
        <p>{{ auth.state.account?.displayName }} · {{ auth.state.account?.roles.map((role) => roleLabels[role]).join('、') }}</p>
      </div>
      <Button label="刷新统计" size="small" severity="secondary" variant="outlined" :loading="loading" @click="load" />
    </header>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <p v-if="loading && !data" class="vehicle-empty" role="status">正在读取工作台统计…</p>
    <div v-if="data" class="dashboard-grid">
      <section v-for="group in groups" :key="group.route" class="dashboard-card">
        <h2>{{ group.label }}</h2>
        <strong class="dashboard-count">{{ total(group) }}</strong>
        <ul>
          <li v-for="state in group.states" :key="state.key">
            <RouterLink :to="{ name: group.route, query: { status: state.status } }">
              <span>{{ state.label }}</span><strong>{{ data[state.key] }}</strong>
            </RouterLink>
          </li>
        </ul>
      </section>
    </div>
  </section>
</template>
