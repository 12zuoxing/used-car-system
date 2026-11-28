<!-- @author pangzhihao -->
<script setup>
import { computed, ref } from 'vue' // 保存下单确认、动作和结果状态。
import { useRoute, useRouter } from 'vue-router' // 登录后回到原车源，下单后打开订单详情。
import Button from 'primevue/button' // 复用项目按钮和加载状态。
import Dialog from 'primevue/dialog' // 提交订单前要求买家确认。
import Message from 'primevue/message' // 展示提交错误和服务端解释。
import { orderApi } from '../../api/orders.js' // 发起带有幂等请求标识的下单。
import { auth } from '../auth/session.js' // 获取当前买家会话。
import { formatPrice, vehicleTitle } from '../vehicles/vehicleLabels.js' // 复用市场页的价格和车型显示。
import { forgetOrderRequest, orderRequestKey } from './requestKeys.js' // 在本次请求失败时保留标识。

const props = defineProps({ vehicle: { type: Object, required: true } }) // 接收目前公开展示的车源。
const route = useRoute() // 为游客保留登录后的返回地址。
const router = useRouter() // 下单成功时跳转。
const visible = ref(false) // 控制确认窗是否可见。
const saving = ref(false) // 同一时间只允许提交一次。
const error = ref('') // 显示失败原因，允许继续尝试。
const orderId = ref(null) // 保留已创建的订单地址。
const loginLink = computed(() => ({ name: 'login', query: { redirect: route.fullPath } })) // 让游客返回原车源。

function open() {
  error.value = '' // 清除旧错误，但保留同一次下单的请求标识。
  visible.value = true // 让买家先核对车源和页面报价。
}

async function placeOrder() {
  const buyerId = auth.state.account?.id // 所用请求标识与当前登录的买家绑定。
  if (saving.value || buyerId == null) return // 避免重复发送或在注销后继续提交。
  saving.value = true // 在响应前禁用确认按钮。
  error.value = '' // 清除上一次提交的错误信息。
  try {
    const vehicleId = props.vehicle.id // 以此次确认的车源作为操作目标。
    const key = orderRequestKey(buyerId, vehicleId) // 新发和重试都调用同一生成规则。
    const order = await orderApi.create(vehicleId, key) // 发起实际下单请求。
    orderId.value = order.id // 记住服务器已创建的订单。
    forgetOrderRequest(buyerId, vehicleId, key) // 只有确定成功后才清理原请求标识。
    visible.value = false // 购买者确认后关闭对话框。
    await router.push({ name: 'my-order-detail', params: { id: order.id } }) // 让买家查看真实订单金额和状态。
  } catch (failure) {
    error.value = failure instanceof TypeError
      ? '暂时无法确认下单结果，可重试或到我的订单查看。' // 网络中断时提示买家重试原请求。
      : failure.message // 展示资格限制、车辆已被抢占等服务端原因。
  } finally {
    saving.value = false // 结束等待状态，允许在同一请求标识下重试。
  }
}
</script>

<template>
  <!-- 购买入口跟随公开车源详情展示。 -->
  <section class="vehicle-panel booking-section" aria-labelledby="buy-title">
    <div class="vehicle-section-head">
      <div>
        <h2 id="buy-title">购买车辆</h2>
        <p>提交订单后锁定这辆车，支付步骤为模拟操作。</p>
      </div>
      <!-- 若页面跳转被中断，可打开已成功创建的订单。 -->
      <RouterLink v-if="orderId" class="solid-link" :to="{ name: 'my-order-detail', params: { id: orderId } }">查看已创建订单</RouterLink>
      <RouterLink v-else-if="!auth.state.account" class="solid-link" :to="loginLink">登录后下单</RouterLink>
      <Button v-else label="立即下单" @click="open" />
    </div>
    <!-- 确认前显示车源与报价，订单详情显示服务端最终保存的金额。 -->
    <Dialog v-model:visible="visible" header="确认下单" modal :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(500px, 94vw)' }">
      <div class="booking-section">
        <strong>{{ vehicleTitle(vehicle) }}</strong>
        <p class="vehicle-description">当前报价 {{ formatPrice(vehicle.price) }}</p>
        <p class="muted-text">订单金额以提交时的车源报价为准，创建后可在订单详情核对。</p>
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      </div>
      <template #footer>
        <Button label="返回" severity="secondary" variant="outlined" :disabled="saving" @click="visible = false" />
        <Button label="确认下单" :loading="saving" @click="placeOrder" />
      </template>
    </Dialog>
  </section>
</template>
