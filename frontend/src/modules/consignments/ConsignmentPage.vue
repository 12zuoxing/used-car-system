<!-- @author pangzhihao -->
<script setup>
import { computed, reactive, ref, watch } from 'vue' // 维护委托表单和路由切换时的当前车辆。
import { useRoute } from 'vue-router' // 从受保护路由取得车辆编号。
import Button from 'primevue/button' // 使用统一的提交和撤回按钮。
import Dialog from 'primevue/dialog' // 在撤回或查看历史时展示详情。
import Message from 'primevue/message' // 在表单和列表展示处理反馈。
import Tag from 'primevue/tag' // 为委托状态提供一致的视觉提示。
import { consignmentApi } from '../../api/bookings.js' // 提交和查询委托。
import { vehicleApi } from '../../api/vehicles.js' // 检查车辆确实属于当前卖家。
import BookingTable from '../bookings/BookingTable.vue' // 复用表格和分页控件。
import { businessInputTime, consignmentLabel, consignmentStatuses, timeLabel } from '../bookings/bookingLabels.js' // 统一显示时间与状态。
import { useBookingAction } from '../bookings/useBookingAction.js' // 复用撤回对话框的提交状态。
import { canEdit, vehicleTitle } from '../vehicles/vehicleLabels.js' // 保持车型名称和可编辑状态与车源详情一致。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 分页时只保留最新请求结果。

const route = useRoute() // 跟随浏览器当前打开的车辆编号。
const vehicle = ref(null) // 保存已校验归属的车辆资料。
const loadingVehicle = ref(true) // 表示车辆详情是否还在读取。
const error = ref('') // 展示查询或提交失败原因。
const success = ref('') // 展示提交完成后车主需要看到的状态。
const saving = ref(false) // 防止重复提交同一份委托。
const form = reactive({ startsAt: '', expiresAt: '', fee: '', terms: '' }) // 保存卖家可编辑的委托信息。
const allowed = computed(() => vehicle.value?.saleMode === 'CONSIGN'
  && !['LOCKED', 'SOLD'].includes(vehicle.value.status)) // 在可办理委托的代销车源上提供表单。
const page = useVehiclePage((query) => consignmentApi.mine(route.params.id, query), 6) // 加载当前车辆的委托历史。
const { target, visible, saving: changing, error: actionError, notice, open, run } = useBookingAction(page) // 管理详情对话框的提交与反馈。
const columns = [
  { key: 'startsAt', label: '开始时间', format: (row) => timeLabel(row.startsAt) }, // 用北京时间显示生效起点。
  { key: 'expiresAt', label: '截止时间', format: (row) => timeLabel(row.expiresAt) }, // 提醒车主查看到期时间。
  { key: 'fee', label: '费用（元）' }, // 显示申请时约定的费用。
  { key: 'status', label: '委托状态' }, // 交由状态标签展示。
]
let version = 0 // 标记最新车辆请求，阻止旧路由响应覆盖当前车源。

async function loadVehicle(id) {
  const current = ++version // 为这次读取分配请求版本。
  loadingVehicle.value = true // 在确认归属期间显示加载状态。
  vehicle.value = null // 切换车辆时清除旧信息。
  visible.value = false // 清除上一辆车的详情对话框。
  error.value = '' // 清除上一辆车或上次请求的错误。
  success.value = '' // 清除上一辆车或上次请求的提示。
  notice.value = '' // 不在新车辆里显示上一辆车的撤回反馈。
  const result = await vehicleApi.myDetail(id).catch((failure) => { // 用现有本人车源接口确认归属。
    if (current === version) error.value = failure.message // 只展示当前这次请求的错误。
    return null // 查询失败时保留空的车辆资料。
  })
  if (current !== version) return // 路由改变时丢弃旧响应。
  vehicle.value = result // 保存当前车源资料供标题和表单使用。
  loadingVehicle.value = false // 请求已完成，可以显示结果或错误。
  if (result?.saleMode === 'CONSIGN') await page.load(1) // 仅在代销车源上查询委托历史。
}

watch(() => route.params.id, (id) => { // 页面复用组件时重新读取车源及委托历史。
  Object.assign(form, { startsAt: businessInputTime(), expiresAt: '', fee: '', terms: '' }) // 切换车源时清空其他车辆的输入。
  void loadVehicle(id) // 在路由更新后发起异步读取。
}, { immediate: true }) // 首次打开也执行相同的加载流程。

async function submit() {
  if (saving.value) return // 请求进行中保持单次提交。
  saving.value = true // 提交成功或失败前锁定提交按钮。
  error.value = '' // 清除前一次表单错误。
  success.value = '' // 清除前一次提交成功提示。
  const id = vehicle.value.id // 保存当前操作的车辆编号。
  const current = version // 防止切换车辆后显示上一辆车的提交提示。
  try {
    await consignmentApi.create(id, { ...form, terms: form.terms.trim() }) // 按北京时间和填写的费用提交给后端。
    if (current !== version) return // 结果属于旧页面时不更新当前页面。
    await loadVehicle(id) // 刷新车辆状态与最新委托历史。
    if (current + 1 !== version) return // 路由变化时停止处理旧请求的结果。
    success.value = '委托已提交，工作人员审核后可在这里查看结果。' // 提示后续在当前页查看进度。
    Object.assign(form, { startsAt: businessInputTime(), expiresAt: '', fee: '', terms: '' }) // 复位表单供以后再次发起。
  } catch (failure) {
    if (current === version) error.value = failure.message // 保留表单并展示服务端拒绝原因。
  } finally {
    saving.value = false // 恢复按钮，允许重新尝试。
  }
}

async function withdraw() {
  const id = vehicle.value.id // 记住撤回操作关联的车辆。
  const current = version // 避免旧操作完成后跳回旧车源。
  await run((row) => consignmentApi.withdraw(row.id), '委托已撤回。') // 交给共享对话框管理本次撤回。
  if (!actionError.value && current === version) {
    const refreshed = await vehicleApi.myDetail(id).catch(() => null) // 撤回可能下架车源，重新读取车辆状态。
    if (refreshed && current === version) vehicle.value = refreshed // 只同步当前仍在浏览的车源。
  }
}
</script>

<template>
  <!-- 当前车主可在这个页面填报及查询单辆车的委托。 -->
  <main class="public-width vehicle-page booking-page">
    <RouterLink class="vehicle-back" :to="{ name: 'my-vehicle-detail', params: { id: route.params.id } }">← 返回我的车源详情</RouterLink>
    <header class="page-heading">
      <h1>代销委托</h1>
      <p v-if="vehicle">{{ vehicleTitle(vehicle) }} · 业务时间为北京时间</p>
    </header>
    <p v-if="loadingVehicle" class="vehicle-empty" role="status">正在读取车辆…</p>
    <Message v-if="!vehicle && error" severity="error" :closable="false">{{ error }}</Message>
    <template v-if="vehicle?.saleMode === 'CONSIGN'">
      <!-- 交易锁定或已售出的车只显示历史。 -->
      <section v-if="allowed" class="vehicle-panel booking-section" aria-labelledby="consignment-form-title">
        <h2 id="consignment-form-title">提交委托</h2>
        <p class="muted-text">同一车源可保留一份待审或未到期的委托。委托生效后，车源还需通过上架审核。</p>
        <!-- 采用本地时间控件收集北京时间，服务端再次校验区间。 -->
        <form class="vehicle-form" @submit.prevent="submit">
          <label>开始时间<input v-model="form.startsAt" type="datetime-local" required /></label>
          <label>截止时间<input v-model="form.expiresAt" type="datetime-local" :min="form.startsAt" required /></label>
          <label>代销费用（元）<input v-model="form.fee" type="number" min="0" max="9999999999.99" step="0.01" required /></label>
          <label class="vehicle-form-wide">委托约定<textarea v-model="form.terms" maxlength="600" rows="3" /></label>
          <div class="vehicle-form-wide"><Button type="submit" label="提交代销委托" :loading="saving" /></div>
        </form>
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
        <Message v-if="success" severity="success" :closable="false">{{ success }}</Message>
      </section>
      <!-- 卖家可通过历史记录查看审核理由或撤回有效委托。 -->
      <section class="vehicle-panel booking-section" aria-labelledby="consignment-history-title">
        <div class="vehicle-section-head">
          <h2 id="consignment-history-title">委托历史</h2>
          <RouterLink v-if="canEdit(vehicle)" :to="{ name: 'vehicle-edit', params: { id: vehicle.id } }">修改车源并重提</RouterLink>
        </div>
        <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
        <BookingTable :page="page" :columns="columns" empty="这辆车暂无委托记录。">
          <template #status="{ entry }"><Tag :value="consignmentLabel(entry)" :severity="consignmentStatuses[entry.status].severity" /></template>
          <template #actions="{ entry }"><Button label="查看" size="small" variant="text" @click="open(entry)" /></template>
        </BookingTable>
      </section>
    </template>
    <Message v-else-if="vehicle" severity="info" :closable="false">这辆车采用直销方式。</Message>
    <!-- 撤回按钮与委托详情同屏，确保车主能辨认将被撤回的记录。 -->
    <Dialog v-model:visible="visible" header="委托详情" modal :closable="!changing" :close-on-escape="!changing" :style="{ width: 'min(530px, 94vw)' }">
      <div v-if="target" class="booking-section">
        <div><Tag :value="consignmentLabel(target)" :severity="consignmentStatuses[target.status].severity" /></div>
        <p class="vehicle-description">委托期限 {{ timeLabel(target.startsAt) }} 至 {{ timeLabel(target.expiresAt) }}</p>
        <p class="vehicle-description">费用 {{ target.fee }} 元</p>
        <p v-if="target.terms" class="vehicle-description">委托约定 {{ target.terms }}</p>
        <p v-if="target.reviewNote" class="vehicle-description">审核说明 {{ target.reviewNote }}</p>
        <p v-if="target.active" class="muted-text">撤回当前委托会下架已经公开的代销车源。</p>
        <Message v-if="actionError" severity="error" :closable="false">{{ actionError }}</Message>
      </div>
      <template #footer>
        <Button label="关闭" severity="secondary" variant="outlined" :disabled="changing" @click="visible = false" />
        <Button v-if="allowed && ['PENDING', 'APPROVED'].includes(target?.status)"
          label="撤回委托" severity="danger" variant="outlined" :loading="changing" @click="withdraw" />
      </template>
    </Dialog>
  </main>
</template>
