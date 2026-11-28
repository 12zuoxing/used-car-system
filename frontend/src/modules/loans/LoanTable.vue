<!-- @author pangzhihao -->
<script setup>
import Tag from 'primevue/tag' // 将贷款状态转成易于扫描的标签。
import BookingTable from '../bookings/BookingTable.vue' // 复用分页、筛选和窄屏表格。
import { timeLabel } from '../bookings/bookingLabels.js' // 时间显示按北京时间统一格式。
import { formatPrice } from '../vehicles/vehicleLabels.js' // 将申请金额格式化为人民币。
import { loanActions, loanStatuses } from './loanLabels.js' // 共享业务状态和办理规则。

const props = defineProps({
  page: { type: Object, required: true }, // 读取父页面传入的分页结果。
  status: { type: String, default: '' }, // 保留当前状态筛选。
  managed: { type: Boolean, default: false }, // 由调用位置选择个人详情或后台经办。
})
defineEmits(['update:status']) // 状态变化由调用页面重新读第一页。
const baseColumns = [
  { key: 'vehicleModel', label: '车辆 / 订单' }, // 通过车型和订单号判别记录。
  { key: 'amount', label: '申请金额', format: (row) => formatPrice(row.amount) }, // 以元显示保存的申请金额。
  { key: 'months', label: '期限', format: (row) => row.months + ' 期' }, // 说明申请人选择的贷款期数。
  { key: 'status', label: '办理状态' }, // 用状态标签显示办理进度。
  { key: 'createdAt', label: '提交时间', format: (row) => timeLabel(row.createdAt) }, // 展示首次提交时间。
]
const columns = props.managed
  ? [baseColumns[0], { key: 'applicantName', label: '申请人' }, ...baseColumns.slice(1)] // 后台还需要识别提交人。
  : baseColumns // 个人列表已经限定申请人为登录买家。

function target(row) {
  return props.managed
    ? { name: 'admin-loan-detail', params: { id: row.id } } // 后台通过申请编号打开办理工作页。
    : { name: 'loan-application', params: { id: row.orderId } } // 申请人通过订单访问已有登记。
}
</script>

<template>
  <!-- 个人和后台共享筛选表格，只传入各自被授权的接口数据。 -->
  <BookingTable :page="page" :columns="columns" :statuses="loanStatuses" :status="status" empty="暂无符合条件的贷款登记。"
    @update:status="$emit('update:status', $event)">
    <!-- 车型和关联的交易订单号合并展示以减少横向宽度。 -->
    <template #vehicleModel="{ entry }">
      <strong>{{ entry.vehicleModel }}</strong>
      <small class="table-vehicle-meta">订单 #{{ entry.orderId }}</small>
    </template>
    <template #status="{ entry }"><Tag :value="loanStatuses[entry.status].label" :severity="loanStatuses[entry.status].severity" /></template>
    <template #actions="{ entry }">
      <RouterLink :to="target(entry)">
        {{ managed && loanActions[entry.status] ? '办理' : !managed && entry.status === 'SUPPLEMENT' ? '补充资料' : '查看' }}
      </RouterLink>
    </template>
  </BookingTable>
</template>
