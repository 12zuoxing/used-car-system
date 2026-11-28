<!-- @author pangzhihao -->
<script setup>
import { computed, reactive, ref, watch } from 'vue' // 管理车源上下文、提交数据与反馈。
import { useRoute, useRouter } from 'vue-router' // 从订单或车源读取编号，提交后打开工单详情。
import Button from 'primevue/button' // 显示表单提交和等待反馈。
import Message from 'primevue/message' // 显示读取失败和校验错误。
import { orderApi } from '../../api/orders.js' // 获取售后订单的成交状态与实际车辆。
import { repairApi } from '../../api/repairs.js' // 创建已通过授权检查的工单。
import { vehicleApi } from '../../api/vehicles.js' // 读取本人发布的售前车辆。
import { businessInputTime } from '../bookings/bookingLabels.js' // 使用北京时间设置允许到店的最早时间。
import { vehicleTitle } from '../vehicles/vehicleLabels.js' // 售前使用车源统一的显示标题。
import { canRequestAfterSale, canRequestPreSale, repairStages } from './repairLabels.js' // 保证入口和表单使用相同条件。

const props = defineProps({ afterSale: { type: Boolean, default: false } }) // 路由明确本次申请属于哪一阶段。
const route = useRoute() // 读取当前车源或订单路径编号。
const router = useRouter() // 跳转至已创建工单的详情。
const entry = ref(null) // 记录由受保护接口返回的车源或订单。
const loading = ref(false) // 阻止在资格校验完成前填写。
const saving = ref(false) // 防止并发点击提交两笔申请。
const savedId = ref(null) // 页面跳转中断时仍可进入已经创建的工单。
const error = ref('') // 在表单附近反馈接口或网络失败。
const form = reactive({ visitAt: '', description: '' }) // 只保存用户可变更的预约信息。
let version = 0 // 用递增值过滤不再属于目前路由的响应。

const stage = computed(() => props.afterSale ? 'AFTER_SALE' : 'PRE_SALE') // 将路由上下文映射为后端枚举。
const back = computed(() => ({
  name: props.afterSale ? 'my-order-detail' : 'my-vehicle-detail', // 让申请人回到发起工单的业务页面。
  params: { id: route.params.id }, // 保留原订单或车源编号。
}))
const allowed = computed(() => props.afterSale ? canRequestAfterSale(entry.value) : canRequestPreSale(entry.value)) // 由实际返回状态控制表单是否可用。
const title = computed(() => {
  if (!entry.value) return '' // 未加载时不渲染车型。
  return props.afterSale ? entry.value.vehicleModel : vehicleTitle(entry.value) // 两种授权接口返回的车型结构各自保持一致。
})

async function load() {
  const current = ++version // 标记本轮上下文加载开始。
  const id = route.params.id // 固定此轮读取的业务编号。
  entry.value = null // 切换车源时移除上一笔车源信息。
  savedId.value = null // 使用与当前路由独立的提交结果。
  Object.assign(form, { visitAt: '', description: '' }) // 不把上一辆车的说明带入新工单。
  loading.value = true // 显示授权数据读取进度。
  error.value = '' // 清除旧错误。
  try {
    const result = await (props.afterSale ? orderApi.detail(id) : vehicleApi.myDetail(id)) // 先核对能否读取该订单或本人车源。
    if (current === version) entry.value = result // 只有对应当前路由的数据才进入表单。
  } catch (failure) {
    if (current === version) error.value = failure.message // 保留服务端提供的不可访问原因。
  } finally {
    if (current === version) loading.value = false // 完成本次读取。
  }
}

async function submit() {
  if (!allowed.value || saving.value || savedId.value) return // 资格不成立或已有成功结果时停止发送。
  const description = form.description.trim() // 去掉用户文本两端空格。
  if (!description) {
    error.value = '请填写整备或送修说明。' // 防止只填空格仍发起请求。
    return // 保留表单以供补充。
  }
  const current = version // 保留目前页面的上下文，用于忽略过期响应。
  const record = entry.value // 保存发起本次申请时的车源或订单。
  saving.value = true // 发出请求后停止重复提交。
  error.value = '' // 清除上次失败提示。
  try {
    const result = await repairApi.create({
      stage: stage.value, // 按当前进入表单的业务阶段提交。
      vehicleId: props.afterSale ? record.vehicleId : record.id, // 售后车辆从订单中读取。
      tradeOrderId: props.afterSale ? record.id : null, // 仅售后记录需要订单依据。
      visitAt: form.visitAt, // 以北京时间提交用户选择的到店时间。
      description, // 保存具体的整备要求或送修描述。
    })
    if (current !== version) return // 路由更换后不再跳到上一笔工单。
    savedId.value = result.id // 保存已创建编号，避免导航异常产生重复提交。
    await router.push({ name: 'my-repair-detail', params: { id: result.id } }) // 创建后立即查看状态。
  } catch (failure) {
    if (current !== version) return // 只在本次业务页面反馈错误。
    error.value = failure instanceof TypeError ? '暂时无法确认提交结果，可到我的工单查看。' : failure.message // 网络中断时提供核实入口。
  } finally {
    saving.value = false // 结束提交等待。
  }
}

watch(() => [route.params.id, props.afterSale], load, { immediate: true }) // 首次与切换路由使用相同读取流程。
</script>

<template>
  <!-- 车源和成交订单使用同一个工单表单，提交目标由路由上下文决定。 -->
  <main class="public-width vehicle-page booking-page">
    <RouterLink class="vehicle-back" :to="back">← {{ afterSale ? '返回订单详情' : '返回车源详情' }}</RouterLink>
    <header class="page-heading">
      <h1>申请{{ repairStages[stage] }}</h1>
      <p v-if="entry">{{ title }} <template v-if="afterSale">· 订单 #{{ entry.id }}</template></p>
    </header>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <p v-if="loading" class="vehicle-empty" role="status">正在读取资料…</p>
    <!-- 没有跳转成功时提供已创建工单的明确入口。 -->
    <RouterLink v-else-if="savedId" :to="{ name: 'my-repair-detail', params: { id: savedId } }">查看已提交的工单</RouterLink>
    <section v-else-if="allowed" class="vehicle-panel booking-section" aria-labelledby="repair-form-title">
      <h2 id="repair-form-title">到店安排</h2>
      <form class="vehicle-form" @submit.prevent="submit">
        <label>预计到店时间（北京时间）
          <input v-model="form.visitAt" type="datetime-local" :min="businessInputTime(new Date(Date.now() + 60_000))" :disabled="saving" required />
        </label>
        <label class="vehicle-form-wide">{{ afterSale ? '送修说明' : '整备要求' }}
          <textarea v-model="form.description" maxlength="800" rows="5" :disabled="saving" required />
        </label>
        <div class="vehicle-form-wide vehicle-actions">
          <Button label="提交工单" type="submit" :loading="saving" />
          <RouterLink :to="{ name: 'my-repairs' }">我的工单</RouterLink>
        </div>
      </form>
    </section>
    <Message v-else-if="entry" severity="info" :closable="false">
      {{ afterSale ? '请从本人买入的已成交订单发起售后送修。' : '当前车源状态无法申请售前整备。' }}
    </Message>
  </main>
</template>
