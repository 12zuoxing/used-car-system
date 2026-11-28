<!-- @author pangzhihao -->
<script setup>
import { computed, onMounted, reactive } from 'vue' // 管理查询输入和与账号角色相关的显示列。
import Button from 'primevue/button' // 执行筛选和恢复全部记录。
import { operationsApi } from '../../api/operations.js' // 请求后台授权的审计数据。
import { auth } from '../auth/session.js' // 用当前管理员权限决定是否显示说明列。
import BookingTable from '../bookings/BookingTable.vue' // 复用可滚动的分页表格。
import { timeLabel } from '../bookings/bookingLabels.js' // 按业务日期时间格式显示。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 保留最新筛选对应的分页响应。

const empty = { subjectType: '', subjectId: '', action: '', from: '', to: '' } // 定义搜索表单可选字段。
const filters = reactive({ ...empty }) // 用户编辑中的条件与已经执行的查找独立。
let applied = { ...empty } // 翻页与刷新沿用上一次成功提交的筛选。
const page = useVehiclePage((query) => operationsApi.audit({ ...query, ...applied }), 10) // 由服务端限定授权数据范围。
const types = computed(() => ({
  VEHICLE: '车源', REPORT: '车况报告', CONSIGNMENT: '代销委托', APPOINTMENT: '看车预约', ORDER: '订单', LOAN: '贷款', REPAIR: '工单',
  ...(auth.isAdministrator() ? { USER: '账号安全', BRAND: '品牌字典', CITY: '城市字典' } : {}), // 管理员额外可查账号和字典日志。
}))
const columns = computed(() => [
  { key: 'createdAt', label: '操作时间', format: (entry) => timeLabel(entry.createdAt) }, // 展示事件留痕时间。
  { key: 'action', label: '操作代码' }, // 保留可复用的精确筛选代码。
  { key: 'subjectType', label: '业务对象', format: (entry) => (types.value[entry.subjectType] || entry.subjectType) + ' #' + entry.subjectId }, // 将类型与记录编号放在一起。
  { key: 'actorName', label: '操作人' }, // 读取服务端提供的公开显示名称。
  ...(auth.isAdministrator() ? [{ key: 'detail', label: '操作说明' }] : []), // 普通经办角色只读审计摘要。
])

function find() {
  applied = { ...filters, action: filters.action.trim().toUpperCase() } // 规范化事件代码后冻结本轮条件。
  void page.load(1) // 每次新搜索都从第一页开始。
}

function clear() {
  Object.assign(filters, empty) // 复位所有可选筛选条件。
  find() // 立刻重新显示完整的授权日志。
}

onMounted(() => page.load()) // 后台首次打开日志页时加载默认分页。
</script>

<template>
  <!-- 管理端可按业务类别、记录编号和时间查询审计。 -->
  <section class="booking-section" aria-labelledby="audit-title">
    <header class="page-heading">
      <h1 id="audit-title">操作日志</h1>
      <p>{{ auth.isAdministrator() ? '查询业务操作、账号安全和办理说明。' : '查询可见的业务操作及经办人。' }}</p>
    </header>
    <form class="vehicle-panel vehicle-form" @submit.prevent="find">
      <label>业务类型
        <select v-model="filters.subjectType">
          <option value="">全部</option>
          <option v-for="(label, key) in types" :key="key" :value="key">{{ label }}</option>
        </select>
      </label>
      <label>业务编号<input v-model="filters.subjectId" type="number" min="1" step="1" /></label>
      <label>开始日期<input v-model="filters.from" type="date" /></label>
      <label>结束日期<input v-model="filters.to" type="date" :min="filters.from || undefined" /></label>
      <label>操作代码<input v-model="filters.action" maxlength="40" placeholder="例如 COMPLETE_ORDER" /></label>
      <div class="vehicle-form-wide vehicle-actions">
        <Button label="查询日志" type="submit" :loading="page.loading.value" />
        <Button label="清除条件" type="button" severity="secondary" variant="outlined" @click="clear" />
      </div>
    </form>
    <!-- 表格只展示受保护接口返回的权限字段。 -->
    <BookingTable :page="page" :columns="columns" empty="暂无符合条件的操作记录。">
      <template #action="{ entry }"><code class="audit-code">{{ entry.action }}</code></template>
      <template #detail="{ entry }"><span class="audit-note">{{ entry.detail || '—' }}</span></template>
    </BookingTable>
  </section>
</template>
