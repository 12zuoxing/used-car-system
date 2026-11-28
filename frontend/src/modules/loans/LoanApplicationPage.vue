<!-- @author pangzhihao -->
<script setup>
import { computed, reactive, ref, watch } from 'vue' // 管理订单上下文、资料表单和申请状态。
import { useRoute } from 'vue-router' // 从本人订单的子路由取得订单编号。
import Button from 'primevue/button' // 提供可显示加载状态的提交按钮。
import Message from 'primevue/message' // 反馈查询、提交或订单状态变化。
import { loanApi } from '../../api/loans.js' // 请求当前订单的贷款登记与补充接口。
import { orderApi } from '../../api/orders.js' // 读取已锁定订单金额和买家视角。
import { formatPrice } from '../vehicles/vehicleLabels.js' // 用人民币显示贷款金额上限。
import LoanDetails from './LoanDetails.vue' // 渲染已有申请的办理详情。

const route = useRoute() // 切换订单子路由时重新加载。
const order = ref(null) // 保存个人订单的关键信息。
const loan = ref(null) // 保存尚未创建或已存在的登记。
const loading = ref(false) // 标记详情读取是否完成。
const saving = ref(false) // 避免快速点击重复提交。
const error = ref('') // 展示接口错误及过期提醒。
const notice = ref('') // 提示登记或补充成功。
const form = reactive({ amount: '', months: '', note: '' }) // 买家可填写的三项贷款信息。
const editable = computed(() => order.value?.side === 'BUY' && order.value.status !== 'CANCELLED'
  && (!loan.value || loan.value.status === 'SUPPLEMENT')) // 仅首次申请或被要求补充时开放表单。
let version = 0 // 用来丢弃旧路由的异步响应。

function syncForm(previous) {
  Object.assign(form, {
    amount: previous?.amount ?? '', // 补充资料时保留原金额供修改。
    months: previous?.months ?? '', // 补充资料时保留原期数。
    note: previous?.note ?? '', // 允许继续编辑或清空原备注。
  })
}

async function load() {
  const latest = ++version // 为当前读取生成独立的页面版本。
  const id = route.params.id // 固定本次请求所对应的订单编号。
  order.value = null // 切换订单时清除旧订单字段。
  loan.value = null // 清空上一份贷款申请的详情。
  loading.value = true // 加载期间避免展示空申请表。
  error.value = '' // 清除上一次请求的错误。
  notice.value = '' // 进入另一个订单时移除旧提示。
  try {
    const [currentOrder, currentLoan] = await Promise.all([orderApi.detail(id), loanApi.forOrder(id)]) // 两个接口均在后端验证对应资源权限。
    if (latest !== version) return // 忽略较早的页面请求。
    order.value = currentOrder // 用数据库中的成交金额控制表单上限。
    loan.value = currentLoan // 已登记时展示已有状态与原记录。
    syncForm(currentLoan) // 填充被退回补充的表单内容。
  } catch (failure) {
    if (latest === version) error.value = failure.message // 仅当前页面显示拒绝访问或网络失败。
  } finally {
    if (latest === version) loading.value = false // 结束本次加载。
  }
}

async function submit() {
  if (saving.value || !editable.value) return // 重复点击或状态已变时停止。
  const latest = version // 防止路由切换时写回旧记录。
  const current = loan.value // 区分首次提交和补充资料。
  const orderId = order.value.id // 固定本次操作对应的订单。
  const input = { ...form, note: form.note.trim() } // 提交本次填写的金额、期数与说明。
  saving.value = true // 发送请求时禁用按钮。
  error.value = '' // 清除旧错误并允许用户重试。
  notice.value = '' // 以本次操作结果刷新反馈。
  try {
    const result = current
      ? await loanApi.supplement(current.id, current.revision, input) // 更新原登记，携带已查看的资料版本。
      : await loanApi.create(orderId, input) // 当前订单尚未登记时创建。
    if (latest !== version) return // 切换页面时不显示上一笔申请。
    loan.value = result // 转入已登记的只读进度页面。
    syncForm(result) // 将界面数据与后端实际保存值对齐。
    notice.value = current ? '资料已补充并重新提交。' : '贷款登记已提交，等待工作人员处理。' // 区分首次提交与补充动作。
  } catch (failure) {
    if (latest !== version) return // 丢弃旧页面上的错误。
    if (failure.status === 409) await load() // 重复登记、订单取消或资料更新时读取当前办理状态。
    if (version === latest || (version === latest + 1 && order.value?.id === orderId)) error.value = failure.message // 只在仍对应此订单的页面显示原因。
  } finally {
    saving.value = false // 提交结束后恢复操作。
  }
}

watch(() => route.params.id, load, { immediate: true }) // 支持从订单或个人贷款列表直接进入。
</script>

<template>
  <!-- 从订单进入的申请页同时承担补充资料和查询办理结果。 -->
  <main class="public-width vehicle-page booking-page">
    <RouterLink class="vehicle-back" :to="{ name: 'my-order-detail', params: { id: route.params.id } }">← 返回订单详情</RouterLink>
    <header class="page-heading">
      <h1>贷款登记</h1>
      <p v-if="order">订单 #{{ order.id }} · {{ order.vehicleModel }}</p>
    </header>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <p v-if="loading" class="vehicle-empty" role="status">正在读取贷款登记…</p>
    <template v-else-if="order">
      <!-- 已登记时先展示工作人员的最新说明，再按状态渲染可用动作。 -->
      <section v-if="loan" class="vehicle-panel booking-section">
        <LoanDetails :loan="loan" />
        <RouterLink :to="{ name: 'my-loans' }">查看我的贷款</RouterLink>
      </section>
      <section v-if="editable" class="vehicle-panel booking-section" aria-labelledby="loan-form-title">
        <h2 id="loan-form-title">{{ loan ? '补充资料' : '填写申请' }}</h2>
        <p class="muted-text">订单金额 {{ formatPrice(order.amount) }}，办理进度可在「我的贷款」查看。</p>
        <!-- 浏览器按金额和期数限制基本输入，服务端仍会重新校验。 -->
        <form class="vehicle-form" @submit.prevent="submit">
          <label>申请金额（元）<input v-model="form.amount" type="number" min="0.01" :max="order.amount" step="0.01" :disabled="saving" required /></label>
          <label>期限（期）<input v-model="form.months" type="number" min="1" max="84" step="1" :disabled="saving" required /></label>
          <label class="vehicle-form-wide">申请说明<textarea v-model="form.note" maxlength="500" rows="4" :disabled="saving" /></label>
          <div class="vehicle-form-wide"><Button type="submit" :label="loan ? '重新提交申请' : '提交贷款登记'" :loading="saving" /></div>
        </form>
      </section>
      <Message v-else-if="!loan && order.status === 'CANCELLED'" severity="info" :closable="false">关联订单已取消。</Message>
    </template>
  </main>
</template>
