<!-- @author pangzhihao -->
<script setup>
import { computed, onMounted, ref } from 'vue' // 管理预约筛选和当前记录可执行的处理动作。
import Button from 'primevue/button' // 在复核后提交已选择的处理动作。
import Dialog from 'primevue/dialog' // 展示预约资料和经办表单。
import Message from 'primevue/message' // 向经办人显示操作结果或原因。
import Tag from 'primevue/tag' // 显示预约当前状态。
import { appointmentApi } from '../../api/bookings.js' // 请求后台预约列表与处理接口。
import BookingTable from '../bookings/BookingTable.vue' // 复用筛选和分页列表。
import { appointmentStatuses, timeLabel } from '../bookings/bookingLabels.js' // 统一状态标签及时间显示。
import { useBookingAction } from '../bookings/useBookingAction.js' // 复用经办对话框的提交行为。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 加载最新的分页结果。

const status = ref('PENDING') // 打开时优先处理待确认的预约。
const next = ref('') // 保存工作人员明确选择的结果。
const page = useVehiclePage((query) => appointmentApi.queue({ ...query, status: status.value }), 10) // 根据状态加载后台记录。
const { target, visible, note, saving, error, notice, open, run } = useBookingAction(page) // 保存当前经办的记录和表单状态。
const columns = [
  { key: 'vehicleModel', label: '车源' }, // 显示买家想看的车型。
  { key: 'buyerName', label: '预约人' }, // 显示提交预约的买家姓名。
  { key: 'visitAt', label: '到访时间', format: (row) => timeLabel(row.visitAt) }, // 显示预计到访的北京时间。
  { key: 'status', label: '处理状态' }, // 使用状态标签表示经办进度。
]
const options = computed(() => {
  if (target.value?.status === 'PENDING') return { CONFIRMED: '确认预约', REJECTED: '退回预约', CANCELLED: '取消预约' } // 待处理只提供这三个动作。
  if (target.value?.status === 'CONFIRMED') return { COMPLETED: '标记完成', CANCELLED: '取消预约' } // 已确认后提供到访登记或取消。
  return {} // 历史记录只用于查看。
})
const canHandle = computed(() => Object.keys(options.value).length > 0) // 根据可执行动作决定是否展示表单。

function filter(value) {
  status.value = value // 保存当前筛选条件。
  void page.load(1) // 筛选变更后回到第一页。
}

function inspect(entry) {
  next.value = '' // 每次打开记录都要求明确选择处理动作。
  open(entry) // 展示当前预约详情并清理上一笔说明。
}

function handle() {
  if (!next.value) {
    error.value = '请选择处理结果。' // 提醒明确选择状态后再提交。
    return // 继续打开表单。
  }
  if (['REJECTED', 'CANCELLED'].includes(next.value) && !note.value.trim()) {
    error.value = '退回或取消时请填写原因。' // 让买家能查看处理原因。
    return // 保留其他表单信息。
  }
  void run((row, reason) => appointmentApi.handle(row.id, { status: next.value, note: reason }),
    '预约处理结果已保存。') // 执行预约经办并刷新列表。
}

onMounted(() => page.load()) // 首次进入后台预约队列时加载数据。
</script>

<template>
  <!-- 后台顺序展示预约时间、申请人和进度。 -->
  <section class="booking-section" aria-labelledby="appointment-queue-title">
    <header class="page-heading">
      <h1 id="appointment-queue-title">看车预约</h1>
      <p>确认到访安排，到访时间已到后可登记完成。</p>
    </header>
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <!-- 工作人员可切换到已确认列表处理到访完成。 -->
    <BookingTable :page="page" :columns="columns" :statuses="appointmentStatuses" :status="status"
      empty="暂无符合条件的看车预约。" @update:status="filter">
      <template #status="{ entry }"><Tag :value="appointmentStatuses[entry.status].label" :severity="appointmentStatuses[entry.status].severity" /></template>
      <template #actions="{ entry }">
        <Button :label="['PENDING', 'CONFIRMED'].includes(entry.status) ? '处理' : '查看'" size="small" variant="text" @click="inspect(entry)" />
      </template>
    </BookingTable>
    <!-- 经办人先核对原始预约和先前说明，再保存新的处理结果。 -->
    <Dialog v-model:visible="visible" :header="canHandle ? '处理预约' : '查看预约'" modal
      :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(540px, 94vw)' }">
      <div v-if="target" class="booking-section">
        <strong>{{ target.vehicleModel }} · {{ target.buyerName }}</strong>
        <p class="vehicle-description">到访时间 {{ timeLabel(target.visitAt) }}（北京时间）</p>
        <p v-if="target.note" class="vehicle-description">买家备注 {{ target.note }}</p>
        <p v-if="target.handlingNote" class="vehicle-description">上一条经办说明 {{ target.handlingNote }}</p>
        <form v-if="canHandle" id="appointment-handling" class="vehicle-form" @submit.prevent="handle">
          <label>处理结果
            <select v-model="next" :disabled="saving" required>
              <option disabled value="">请选择</option>
              <option v-for="(label, key) in options" :key="key" :value="key">{{ label }}</option>
            </select>
          </label>
          <label class="vehicle-form-wide">处理说明<textarea v-model="note" rows="3" maxlength="500" :disabled="saving" /></label>
        </form>
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      </div>
      <template #footer>
        <Button label="关闭" severity="secondary" variant="outlined" :disabled="saving" @click="visible = false" />
        <Button v-if="canHandle" type="submit" form="appointment-handling" label="保存处理结果" :loading="saving" />
      </template>
    </Dialog>
  </section>
</template>
