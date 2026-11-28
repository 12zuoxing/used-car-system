<!-- @author pangzhihao -->
<script setup>
import { computed, ref, watch } from 'vue' // 将订单和经办操作绑定页面状态。
import { useRoute } from 'vue-router' // 从个人或后台路由获取订单编号。
import Button from 'primevue/button' // 提供支付、取消和确认成交按钮。
import Dialog from 'primevue/dialog' // 执行写操作前显示确认信息。
import Message from 'primevue/message' // 显示订单操作反馈。
import Tag from 'primevue/tag' // 展示订单当前状态。
import { orderApi } from '../../api/orders.js' // 使用对应个人或后台接口。
import { timeLabel } from '../bookings/bookingLabels.js' // 统一按北京时间显示交易时刻。
import { formatPrice } from '../vehicles/vehicleLabels.js' // 显示订单的真实金额快照。
import { orderSaleMode, orderSides, orderStatuses } from './orderLabels.js' // 使用后台和个人页一致的状态文案。

const props = defineProps({ managed: { type: Boolean, default: false } }) // 由路由决定读取个人或管理端视图。
const route = useRoute() // 获取正在访问的订单编号。
const order = ref(null) // 保存当前订单资料。
const loading = ref(false) // 显示详情读取状态。
const error = ref('') // 展示详情读取或订单状态冲突。
const notice = ref('') // 展示写操作成功的结果。
const action = ref('') // 记录本次要执行的交易动作。
const dialog = ref(false) // 控制确认对话框是否展示。
const actionError = ref('') // 在弹窗内显示可重试的处理错误。
const saving = ref(false) // 防止快速点击重复提交。
const note = ref('') // 保存工作人员填写的可选过户说明。
let requestVersion = 0 // 阻止旧订单请求覆盖新路由页面。

const pending = computed(() => order.value?.status === 'LOCKED') // 待支付订单可以执行取消或买家支付。
const summary = computed(() => ({
  pay: { title: '模拟支付', text: '确认后，订单将进入待过户确认。此操作用于演示，不会扣款。', success: '模拟支付成功，等待工作人员确认过户。' },
  cancel: { title: '取消订单', text: '订单取消后将释放这笔订单对车辆的占用。', success: '订单已取消。' },
  complete: { title: '确认过户', text: '请确认过户手续已完成。提交后，车辆将标记为已售。', success: '过户已确认，订单已成交。' },
})[action.value]) // 根据当前动作选择正确的确认信息。

async function load() {
  const version = ++requestVersion // 让每次加载具有独立的请求编号。
  const id = route.params.id // 记录本次路由的订单编号。
  order.value = null // 加载新的记录前清理旧资料。
  error.value = '' // 清理前一次请求错误。
  notice.value = '' // 切换订单时不保留旧的操作提示。
  dialog.value = false // 切换订单或刷新时关闭旧操作框。
  loading.value = true // 触发加载提示。
  try {
    const result = await (props.managed ? orderApi.staffDetail(id) : orderApi.detail(id)) // 由服务端验证工作人员或交易参与方权限。
    if (requestVersion === version) order.value = result // 只采用仍属于当前页面的详情结果。
  } catch (failure) {
    if (requestVersion === version) error.value = failure.message // 页面仍对应本次请求时展示错误。
  } finally {
    if (requestVersion === version) loading.value = false // 停止当前加载标识。
  }
}

function prepare(kind) {
  action.value = kind // 保存这次确认对应的业务动作。
  note.value = '' // 每次确认都从空经办说明开始。
  actionError.value = '' // 清除上次失败结果。
  error.value = '' // 清除订单上一次冲突或读取错误。
  dialog.value = true // 提示操作者确认交易动作。
}

async function submit() {
  if (saving.value || !order.value || !summary.value) return // 避免重复操作或提交不存在的订单。
  const current = requestVersion // 记录写操作开始时打开的订单。
  const id = order.value.id // 保存要变更的真实订单编号。
  const kind = action.value // 保存用户已确认选择的动作。
  const success = summary.value.success // 在响应后展示对应成功文案。
  saving.value = true // 在操作提交时禁止关闭或重试。
  actionError.value = '' // 清除前一次操作错误。
  try {
    const result = kind === 'complete'
      ? await orderApi.complete(id, note.value.trim()) // 传入后台过户说明。
      : await orderApi[kind](id) // 个人动作只需指定订单编号。
    if (current !== requestVersion) return // 切换订单后忽略旧操作响应。
    order.value = result // 更新支付、取消或成交后的正式状态。
    dialog.value = false // 结束本次确认。
    notice.value = kind === 'pay' && result.status === 'COMPLETED'
      ? '订单已成交，过户结果已更新。' // 付款响应丢失后重试时，可能已经完成过户。
      : success // 正常情况下展示当前操作的成功反馈。
  } catch (failure) {
    if (current !== requestVersion) return // 切换订单后丢弃旧记录的错误。
    if (failure.status === 409) {
      await load() // 状态已被其他人改变，立刻读取新的交易状态。
      if (current + 1 === requestVersion && order.value) error.value = failure.message // 在最新状态旁显示本次拒绝原因。
    } else {
      actionError.value = failure.message // 网络或字段错误时保留弹窗以供重试。
    }
  } finally {
    saving.value = false // 结束提交状态。
  }
}

watch(() => [route.params.id, props.managed], load, { immediate: true }) // 路由改变时重新加载授权详情。
</script>

<template>
  <!-- 同一详情适配个人中心和后台，后台使用自己独立的接口和动作。 -->
  <section :class="managed ? 'booking-section' : 'public-width vehicle-page booking-page'" aria-labelledby="order-detail-title">
    <RouterLink class="vehicle-back" :to="{ name: managed ? 'order-queue' : 'my-orders' }">← {{ managed ? '返回交易订单' : '返回我的订单' }}</RouterLink>
    <header class="page-heading">
      <h1 id="order-detail-title">订单详情</h1>
      <p v-if="order">订单 #{{ order.id }} · {{ orderSaleMode(order) }}<template v-if="order.side"> · {{ orderSides[order.side] }}</template></p>
    </header>
    <!-- 操作返回和状态冲突显示在最新详情之外，确保用户能看到。 -->
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <p v-if="loading" class="vehicle-empty" role="status">正在读取订单…</p>
    <section v-else-if="order" class="vehicle-panel booking-section order-summary" aria-labelledby="order-vehicle-title">
      <!-- 核心信息优先展示车辆、成交金额和处理状态。 -->
      <div class="order-summary-heading">
        <div>
          <h2 id="order-vehicle-title">{{ order.vehicleModel }}</h2>
          <Tag :value="orderStatuses[order.status].label" :severity="orderStatuses[order.status].severity" />
        </div>
        <div class="order-total">
          <span>订单金额</span>
          <strong>{{ formatPrice(order.amount) }}</strong>
        </div>
      </div>
      <!-- 交易双方和操作时间由后端授权数据填入。 -->
      <dl class="vehicle-facts">
        <div><dt>买家</dt><dd>{{ order.buyerName }}</dd></div>
        <div><dt>卖家</dt><dd>{{ order.sellerName }}</dd></div>
        <div><dt>下单时间</dt><dd>{{ timeLabel(order.createdAt) }}</dd></div>
        <div v-if="order.paidAt"><dt>模拟支付时间</dt><dd>{{ timeLabel(order.paidAt) }}</dd></div>
        <div v-if="order.completedAt"><dt>成交时间</dt><dd>{{ timeLabel(order.completedAt) }}</dd></div>
        <div v-if="order.consignmentId"><dt>代销委托</dt><dd>#{{ order.consignmentId }}</dd></div>
      </dl>
      <p v-if="order.handlingNote" class="vehicle-description">过户说明 {{ order.handlingNote }}</p>
      <!-- 操作按钮随视角和状态变化，最终由服务端执行业务条件校验。 -->
      <div v-if="(!managed && (pending || order.side === 'BUY')) || (managed && order.status === 'PAID')" class="vehicle-actions">
        <Button v-if="!managed && pending && order.side === 'BUY'" label="模拟支付" @click="prepare('pay')" />
        <Button v-if="!managed && pending" label="取消订单" severity="danger" variant="outlined" @click="prepare('cancel')" />
        <Button v-if="managed && order.status === 'PAID'" label="确认过户" @click="prepare('complete')" />
        <!-- 只给订单买家显示登记或查看既有贷款资料的入口。 -->
        <RouterLink v-if="!managed && order.side === 'BUY'" :to="{ name: 'loan-application', params: { id: order.id } }">贷款登记</RouterLink>
      </div>
    </section>
    <!-- 对话框将模拟支付、取消和过户确认都变成明确的用户操作。 -->
    <Dialog v-model:visible="dialog" :header="summary?.title" modal :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(520px, 94vw)' }">
      <div class="booking-section">
        <p class="vehicle-description">{{ summary?.text }}</p>
        <p v-if="action === 'pay'" class="vehicle-description">本次模拟支付金额 {{ formatPrice(order?.amount) }}</p>
        <label v-if="action === 'complete'" class="vehicle-dialog-label">过户说明
          <textarea v-model="note" maxlength="500" rows="3" :disabled="saving" />
        </label>
        <Message v-if="actionError" severity="error" :closable="false">{{ actionError }}</Message>
      </div>
      <template #footer>
        <Button label="返回" severity="secondary" variant="outlined" :disabled="saving" @click="dialog = false" />
        <Button :label="summary?.title" :severity="action === 'cancel' ? 'danger' : undefined" :loading="saving" @click="submit" />
      </template>
    </Dialog>
  </section>
</template>
