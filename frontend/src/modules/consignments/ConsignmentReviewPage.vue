<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue' // 首次打开后台时读取待审核委托。
import Button from 'primevue/button' // 提供通过、退回和查看按钮。
import Dialog from 'primevue/dialog' // 在操作前显示委托详情。
import Message from 'primevue/message' // 展示处理结果及接口异常。
import Tag from 'primevue/tag' // 用统一状态颜色区分审核进度。
import { consignmentApi } from '../../api/bookings.js' // 调用后台委托队列和审核接口。
import BookingTable from '../bookings/BookingTable.vue' // 复用筛选、刷新和分页界面。
import { consignmentLabel, consignmentStatuses, timeLabel } from '../bookings/bookingLabels.js' // 将日期和状态转为可读文本。
import { useBookingAction } from '../bookings/useBookingAction.js' // 复用对话框的保存与刷新逻辑。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 采用最新返回的分页请求。

const status = ref('PENDING') // 默认展示等待工作人员操作的记录。
const page = useVehiclePage((query) => consignmentApi.queue({ ...query, status: status.value }), 10) // 按所选状态读取后台队列。
const { target, visible, note, saving, error, notice, open, run } = useBookingAction(page) // 提供对话框状态和统一提交入口。
const columns = [
  { key: 'vehicleModel', label: '车源' }, // 显示委托关联的车型。
  { key: 'ownerName', label: '委托人' }, // 显示提交委托的卖家姓名。
  { key: 'expiresAt', label: '截止时间', format: (row) => timeLabel(row.expiresAt) }, // 展示审核时应核对的时间。
  { key: 'fee', label: '费用（元）' }, // 展示卖家登记的约定费用。
  { key: 'status', label: '委托状态' }, // 由模板输出审核及实际生效状态。
]

function filter(value) {
  status.value = value // 记录工作人员选中的筛选状态。
  void page.load(1) // 更改筛选后从第一页读取。
}

function review(decision) {
  if (decision === 'REJECT' && !note.value.trim()) {
    error.value = '退回委托时请填写原因。' // 立即提示必填的退回原因。
    return // 保留对话框，等待填写。
  }
  void run((row, reason) => consignmentApi.review(row.id, { decision, note: reason }),
    decision === 'APPROVE' ? '委托已通过。生效后可继续审核车源。' : '委托已退回。') // 提交选择并在成功后刷新队列。
}

onMounted(() => page.load()) // 后台页面加载完成后取得第一批待审委托。
</script>

<template>
  <!-- 工作人员先核对委托，再处理对应车源的上架。 -->
  <section class="booking-section" aria-labelledby="consignment-review-title">
    <header class="page-heading">
      <h1 id="consignment-review-title">代销委托审核</h1>
      <p>核对委托期限、费用和约定。委托生效后可到车源审核处理上架。</p>
    </header>
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <!-- 委托队列以状态筛选，也允许查看已经处理的历史。 -->
    <BookingTable :page="page" :columns="columns" :statuses="consignmentStatuses" :status="status"
      empty="暂无符合条件的代销委托。" @update:status="filter">
      <template #status="{ entry }"><Tag :value="consignmentLabel(entry)" :severity="consignmentStatuses[entry.status].severity" /></template>
      <template #actions="{ entry }">
        <Button :label="entry.status === 'PENDING' ? '审核' : '查看'" size="small" variant="text" @click="open(entry)" />
      </template>
    </BookingTable>
    <!-- 审核弹窗保留必要的对照信息，包括期限、费用及说明。 -->
    <Dialog v-model:visible="visible" :header="target?.status === 'PENDING' ? '审核委托' : '查看委托'" modal
      :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(530px, 94vw)' }">
      <div v-if="target" class="booking-section">
        <strong>{{ target.vehicleModel }} · {{ target.ownerName }}</strong>
        <p class="vehicle-description">委托期限 {{ timeLabel(target.startsAt) }} 至 {{ timeLabel(target.expiresAt) }}</p>
        <p class="vehicle-description">费用 {{ target.fee }} 元</p>
        <p v-if="target.terms" class="vehicle-description">委托约定 {{ target.terms }}</p>
        <label v-if="target.status === 'PENDING'" class="vehicle-dialog-label">审核说明
          <textarea v-model="note" rows="3" maxlength="500" placeholder="退回时需填写原因" />
        </label>
        <p v-else-if="target.reviewNote" class="vehicle-description">审核说明 {{ target.reviewNote }}</p>
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      </div>
      <template #footer>
        <Button label="关闭" severity="secondary" variant="outlined" :disabled="saving" @click="visible = false" />
        <template v-if="target?.status === 'PENDING'">
          <Button label="退回委托" severity="danger" variant="outlined" :disabled="saving" @click="review('REJECT')" />
          <Button label="通过委托" :loading="saving" @click="review('APPROVE')" />
        </template>
      </template>
    </Dialog>
  </section>
</template>
