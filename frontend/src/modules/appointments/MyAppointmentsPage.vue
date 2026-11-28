<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue' // 页面挂载时加载预约并保存筛选状态。
import Button from 'primevue/button' // 使用项目中的取消与查看按钮。
import Dialog from 'primevue/dialog' // 查看预约详情并确认取消。
import Message from 'primevue/message' // 显示列表和提交结果。
import Tag from 'primevue/tag' // 根据处理进度呈现状态标签。
import { appointmentApi } from '../../api/bookings.js' // 查询和取消会话买家自己的预约。
import BookingTable from '../bookings/BookingTable.vue' // 共享筛选和分页表格。
import { appointmentStatuses, timeLabel } from '../bookings/bookingLabels.js' // 共享预约状态和时间格式。
import { useBookingAction } from '../bookings/useBookingAction.js' // 共享取消预约的请求管理。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 处理最新的服务端分页结果。

const status = ref('') // 初始显示本人所有处理状态的预约。
const page = useVehiclePage((query) => appointmentApi.mine({ ...query, status: status.value }), 10) // 读取当前账号的分页记录。
const { target, visible, saving, error, notice, open, run } = useBookingAction(page) // 保存详情弹窗及取消状态。
const columns = [
  { key: 'vehicleModel', label: '车源' }, // 显示预约对应的车型。
  { key: 'visitAt', label: '到访时间', format: (row) => timeLabel(row.visitAt) }, // 在列表中显示北京时间。
  { key: 'status', label: '处理状态' }, // 用状态标签指示预约进度。
]

function filter(value) {
  status.value = value // 保存新选择的预约状态。
  void page.load(1) // 筛选条件变化后重置为第一页。
}

function cancel() {
  void run((row) => appointmentApi.cancel(row.id), '预约已取消。') // 服务端再次核对买家身份和状态。
}

onMounted(() => page.load()) // 页面打开即加载预约进度。
</script>

<template>
  <!-- 个人中心提供独立页面查看预约和工作人员说明。 -->
  <main class="public-width vehicle-page booking-page">
    <RouterLink class="vehicle-back" :to="{ name: 'account' }">← 返回个人中心</RouterLink>
    <section class="vehicle-panel booking-section" aria-labelledby="my-appointments-title">
      <header class="page-heading">
        <h1 id="my-appointments-title">我的看车预约</h1>
        <p>到访时间采用北京时间。</p>
      </header>
      <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
      <!-- 服务端按当前登录买家筛选，前端只传入状态和页码。 -->
      <BookingTable :page="page" :columns="columns" :statuses="appointmentStatuses" :status="status"
        empty="暂无符合条件的预约。" @update:status="filter">
        <template #status="{ entry }"><Tag :value="appointmentStatuses[entry.status].label" :severity="appointmentStatuses[entry.status].severity" /></template>
        <template #actions="{ entry }"><Button label="查看" size="small" variant="text" @click="open(entry)" /></template>
      </BookingTable>
    </section>
    <!-- 查看预约详情时为未到访的进行中预约提供取消按钮。 -->
    <Dialog v-model:visible="visible" header="预约详情" modal :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(520px, 94vw)' }">
      <div v-if="target" class="booking-section">
        <strong>{{ target.vehicleModel }}</strong>
        <p class="vehicle-description">到访时间 {{ timeLabel(target.visitAt) }}</p>
        <p v-if="target.note" class="vehicle-description">我的备注 {{ target.note }}</p>
        <p v-if="target.handlingNote" class="vehicle-description">工作人员说明 {{ target.handlingNote }}</p>
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      </div>
      <template #footer>
        <Button label="关闭" severity="secondary" variant="outlined" :disabled="saving" @click="visible = false" />
        <Button v-if="target?.cancellable" label="取消预约" severity="danger" variant="outlined" :loading="saving" @click="cancel" />
      </template>
    </Dialog>
  </main>
</template>
