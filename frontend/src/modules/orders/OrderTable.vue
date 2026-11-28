<!-- @author pangzhihao -->
<script setup>
import Tag from 'primevue/tag' // 用项目统一的状态标签表示交易进度。
import BookingTable from '../bookings/BookingTable.vue' // 复用后台和个人列表的分页容器。
import { timeLabel } from '../bookings/bookingLabels.js' // 把创建时间按业务时区显示。
import { formatPrice } from '../vehicles/vehicleLabels.js' // 以人民币格式显示订单总额。
import { orderSaleMode, orderStatuses } from './orderLabels.js' // 使用订单状态与销售方式的统一文案。

defineProps({
  page: { type: Object, required: true }, // 调用方提供已授权的分页数据。
  status: { type: String, default: '' }, // 外层保存当前选择的状态。
  managed: { type: Boolean, default: false }, // 决定详情入口和列表列项。
  columns: { type: Array, required: true }, // 根据个人和后台权限显示所需的数据列。
})
defineEmits(['update:status']) // 将状态选择交给外层加载后端分页数据。
</script>

<template>
  <!-- 订单队列和个人订单使用相同的行布局和状态筛选。 -->
  <BookingTable :page="page" :columns="columns" :statuses="orderStatuses" :status="status"
    empty="暂无符合条件的订单。" @update:status="$emit('update:status', $event)">
    <template #filters><slot name="filters" /></template>
    <!-- 为订单车源同时显示编号与下单时的销售方式。 -->
    <template #vehicleModel="{ entry }">
      <strong>{{ entry.vehicleModel }}</strong>
      <small class="table-vehicle-meta">#{{ entry.id }} · {{ orderSaleMode(entry) }}</small>
    </template>
    <template #status="{ entry }"><Tag :value="orderStatuses[entry.status].label" :severity="orderStatuses[entry.status].severity" /></template>
    <template #amount="{ entry }">{{ formatPrice(entry.amount) }}</template>
    <template #createdAt="{ entry }">{{ timeLabel(entry.createdAt) }}</template>
    <!-- 后台为已支付事务标出直接处理过户的入口。 -->
    <template #actions="{ entry }">
      <RouterLink :to="{ name: managed ? 'admin-order-detail' : 'my-order-detail', params: { id: entry.id } }">
        {{ managed && entry.status === 'PAID' ? '办理过户' : '查看' }}
      </RouterLink>
    </template>
  </BookingTable>
</template>
