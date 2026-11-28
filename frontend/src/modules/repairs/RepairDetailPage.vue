<!-- @author pangzhihao -->
<script setup>
import { computed, ref, watch } from 'vue' // 维护详情数据和办单状态。
import { useRoute } from 'vue-router' // 根据路径中的编号读取对应工单。
import Button from 'primevue/button' // 为状态表单和确认撤回提供操作按钮。
import Dialog from 'primevue/dialog' // 申请人取消前再次确认目标工单。
import Message from 'primevue/message' // 提示状态变化和请求失败。
import Tag from 'primevue/tag' // 显示实时办理状态。
import { repairApi } from '../../api/repairs.js' // 调用个人或后台授权接口。
import { timeLabel } from '../bookings/bookingLabels.js' // 统一以北京时间展示安排与办结时刻。
import { repairActions, repairStages, repairStatuses } from './repairLabels.js' // 使用各入口共享的状态标签和处理规则。

const props = defineProps({ managed: { type: Boolean, default: false } }) // 路由明确普通用户还是工作人员界面。
const route = useRoute() // 使用当前 URL 中的工作单编号。
const repair = ref(null) // 保存授权接口返回的工单信息。
const loading = ref(false) // 读取中显示等待反馈。
const saving = ref(false) // 在同一时间只允许提交一次状态变更。
const confirming = ref(false) // 个人取消使用独立的确认弹窗。
const next = ref('') // 保存后台选中的办理动作。
const note = ref('') // 保存待提交的处理说明。
const error = ref('') // 当前详情读取与处理错误。
const notice = ref('') // 最新状态变更的成功提示。
const actions = computed(() => repairActions[repair.value?.status]) // 已终结的工单隐藏后台办理表单。
const noteRequired = computed(() => ['COMPLETED', 'CANCELLED'].includes(next.value)) // 只有完工和取消必须附上说明。
let version = 0 // 避免一个工单的响应影响另一个详情页。

function reset() {
  next.value = '' // 重新填写状态操作时从空选项开始。
  note.value = '' // 清除本次未保存的说明。
  confirming.value = false // 切换工单时关闭旧取消确认窗口。
}

async function load() {
  const current = ++version // 标记当前有效的加载。
  const id = route.params.id // 固定本轮访问的工单编号。
  repair.value = null // 加载前移除旧工单。
  error.value = '' // 清除上一次的请求错误。
  notice.value = '' // 清除其他工单的处理反馈。
  reset() // 将表单和确认窗口恢复到初始状态。
  loading.value = true // 显示正在读取的状态。
  try {
    const data = await (props.managed ? repairApi.staffDetail(id) : repairApi.detail(id)) // 后端按角色或申请人关系授权。
    if (version === current) repair.value = data // 仅显示当前路由的最新数据。
  } catch (failure) {
    if (version === current) error.value = failure.message // 让当前用户看到实际的加载错误。
  } finally {
    if (version === current) loading.value = false // 结束当前加载反馈。
  }
}

function showCancel() {
  error.value = '' // 从新的确认对话框开始。
  confirming.value = true // 显示即将撤回的工单操作。
}

async function save() {
  if (saving.value || !repair.value) return // 无工单或已有请求正在发送时停止。
  if (props.managed && !actions.value?.[next.value]) return // 拒绝提交当前状态不支持的动作。
  if (props.managed && noteRequired.value && !note.value.trim()) {
    error.value = '请填写办理说明。' // 要求说明真正包含信息。
    return // 等待经办人员补充内容。
  }
  if (!props.managed && (!confirming.value || repair.value.status !== 'PENDING')) return // 个人只有确认撤回待受理工单这一种动作。
  const current = version // 记录发起请求时的页面版本。
  const id = repair.value.id // 固定本次操作对应的工单编号。
  saving.value = true // 禁止快速重复点击。
  error.value = '' // 清除前一次错误。
  notice.value = '' // 将提示更新为最新提交结果。
  try {
    const result = props.managed
      ? await repairApi.handle(id, { status: next.value, resultNote: note.value.trim() }) // 提交后台的结果和经办说明。
      : await repairApi.cancel(id) // 申请人取消只需要当前工单编号。
    if (version !== current) return // 路由已切换时忽略旧工单返回。
    repair.value = result // 用服务器保存的新状态更新详情。
    reset() // 清理已提交的说明和取消对话框状态。
    notice.value = props.managed ? '工单状态已更新。' : '工单已取消。' // 显示对应操作的业务结果。
  } catch (failure) {
    if (version !== current) return // 不向新页面显示旧工单错误。
    if (failure.status === 409) await load() // 发生并发受理或取消时重新显示实际工单状态。
    if (version === current || (version === current + 1 && repair.value?.id === id)) error.value = failure.message // 仍在当前工单时显示失败原因。
  } finally {
    saving.value = false // 恢复可操作的界面状态。
  }
}

watch(() => [route.params.id, props.managed], load, { immediate: true }) // 路由或视角变化后重新加载授权详情。
</script>

<template>
  <!-- 同一详情组件服务个人和后台，按角色提供对应的业务动作。 -->
  <section :class="managed ? 'booking-section' : 'public-width vehicle-page booking-page'" aria-labelledby="repair-detail-title">
    <RouterLink class="vehicle-back" :to="{ name: managed ? 'repair-queue' : 'my-repairs' }">← {{ managed ? '返回整备与送修' : '返回我的工单' }}</RouterLink>
    <header class="page-heading">
      <h1 id="repair-detail-title">工单详情</h1>
      <p v-if="repair">工单 #{{ repair.id }} · {{ repairStages[repair.stage] }}</p>
    </header>
    <Message v-if="error && !confirming" severity="error" :closable="false">{{ error }}</Message>
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <p v-if="loading" class="vehicle-empty" role="status">正在读取工单…</p>
    <template v-else-if="repair">
      <!-- 集中展示车源、预约送修时间和当前状态。 -->
      <section class="vehicle-panel booking-section" aria-labelledby="repair-vehicle-title">
        <div class="vehicle-section-head">
          <h2 id="repair-vehicle-title">{{ repair.vehicleModel }}</h2>
          <Tag :value="repairStatuses[repair.status].label" :severity="repairStatuses[repair.status].severity" />
        </div>
        <dl class="vehicle-facts">
          <div><dt>办理类型</dt><dd>{{ repairStages[repair.stage] }}</dd></div>
          <div><dt>预计到店（北京时间）</dt><dd>{{ timeLabel(repair.visitAt) }}</dd></div>
          <div><dt>提交时间</dt><dd>{{ timeLabel(repair.createdAt) }}</dd></div>
          <div v-if="managed"><dt>申请人</dt><dd>{{ repair.applicantName }}</dd></div>
          <div v-if="repair.handlerName"><dt>最近经办人</dt><dd>{{ repair.handlerName }}</dd></div>
          <div v-if="repair.completedAt"><dt>完工时间</dt><dd>{{ timeLabel(repair.completedAt) }}</dd></div>
          <div v-if="repair.tradeOrderId">
            <dt>关联成交订单</dt>
            <dd><RouterLink :to="{ name: managed ? 'admin-order-detail' : 'my-order-detail', params: { id: repair.tradeOrderId } }">#{{ repair.tradeOrderId }}</RouterLink></dd>
          </div>
        </dl>
        <p class="vehicle-description">申请说明 {{ repair.description }}</p>
        <p v-if="repair.resultNote" class="vehicle-description">办理说明 {{ repair.resultNote }}</p>
        <div v-if="!managed && repair.status === 'PENDING'">
          <Button label="取消工单" severity="danger" variant="outlined" @click="showCancel" />
        </div>
      </section>
      <!-- 后台只能选择当前状态可以达到的动作。 -->
      <section v-if="managed && actions" class="vehicle-panel booking-section" aria-labelledby="repair-action-title">
        <h2 id="repair-action-title">办理工单</h2>
        <form class="vehicle-form" @submit.prevent="save">
          <label>处理结果
            <select v-model="next" :disabled="saving" required>
              <option value="" disabled>请选择</option>
              <option v-for="(label, state) in actions" :key="state" :value="state">{{ label }}</option>
            </select>
          </label>
          <label class="vehicle-form-wide">办理说明
            <textarea v-model="note" maxlength="500" rows="4" :required="noteRequired" :disabled="saving" />
          </label>
          <div class="vehicle-form-wide"><Button label="保存办理结果" type="submit" :loading="saving" /></div>
        </form>
      </section>
    </template>
    <!-- 个人撤回待受理工单前须明确确认。 -->
    <Dialog v-model:visible="confirming" header="取消工单" modal :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(470px, 94vw)' }">
      <p v-if="repair">确认取消工单 #{{ repair.id }} 的到店安排？</p>
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <template #footer>
        <Button label="返回" severity="secondary" variant="outlined" :disabled="saving" @click="confirming = false" />
        <Button label="确认取消" severity="danger" :loading="saving" @click="save" />
      </template>
    </Dialog>
  </section>
</template>
