<!-- @author pangzhihao -->
<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { vehicleApi } from '../../api/vehicles.js'
import AppointmentForm from '../appointments/AppointmentForm.vue' // 公共详情可发起看车预约。
import { auth } from '../auth/session.js' // 已登录用户可在车源不可售时检查订单状态。
import BuyVehiclePanel from '../orders/BuyVehiclePanel.vue' // 在公开车源加载成功时展示购买入口。
import VehicleMediaPanel from './VehicleMediaPanel.vue'
import { canEdit, formatMileage, formatPrice, fuels, saleModes, statuses, transmissions, vehicleTitle } from './vehicleLabels.js'

const props = defineProps({ managed: { type: Boolean, default: false } })
const route = useRoute()
const vehicle = ref(null)
const error = ref('')
let latest = 0

async function load() {
  const version = ++latest
  vehicle.value = null
  error.value = ''
  try {
    const result = await (props.managed ? vehicleApi.myDetail(route.params.id) : vehicleApi.detail(route.params.id))
    if (latest === version) vehicle.value = result
  } catch (failure) {
    if (latest === version) error.value = failure.message
  }
}
watch(() => [route.params.id, props.managed], load, { immediate: true })
</script>

<template>
  <main class="public-width vehicle-page">
    <RouterLink class="vehicle-back" :to="{ name: managed ? 'my-vehicles' : 'vehicle-market' }">← {{ managed ? '我的车源' : '返回车源市场' }}</RouterLink>
    <template v-if="error">
      <Message severity="error" :closable="false">{{ error }}</Message>
      <!-- 下单响应中断后，车源可能因本人下单已经被锁定，可前往个人订单核实。 -->
      <RouterLink v-if="!managed && auth.state.account" :to="{ name: 'my-orders' }">查看我的订单</RouterLink>
    </template>
    <p v-else-if="!vehicle" class="vehicle-empty" role="status">正在读取车源…</p>
    <template v-else>
      <section class="vehicle-detail-heading">
        <div>
          <div class="vehicle-tags"><Tag :value="saleModes[vehicle.saleMode]" severity="info" />
            <Tag :value="statuses[vehicle.status].label" :severity="statuses[vehicle.status].severity" /></div>
          <h1>{{ vehicleTitle(vehicle) }}</h1>
          <p>{{ vehicle.cityName }} · {{ vehicle.registrationYear }} 年登记</p>
        </div>
        <strong>{{ formatPrice(vehicle.price) }}</strong>
      </section>
      <div class="vehicle-detail-body">
        <VehicleMediaPanel :key="vehicle.id" :vehicle="vehicle" :managed="managed" />
        <section class="vehicle-panel">
          <h2>车辆参数</h2>
          <dl class="vehicle-facts">
            <div><dt>品牌</dt><dd>{{ vehicle.brandName }}</dd></div><div><dt>行驶里程</dt><dd>{{ formatMileage(vehicle.mileage) }}</dd></div>
            <div><dt>变速箱</dt><dd>{{ transmissions[vehicle.transmission] }}</dd></div><div><dt>能源</dt><dd>{{ fuels[vehicle.fuel] }}</dd></div>
          </dl>
        </section>
        <section class="vehicle-panel"><h2>车辆描述</h2><p class="vehicle-description">{{ vehicle.description }}</p></section>
        <!-- 购买操作只在公开车源详情出现，切换车辆时重建本次下单状态。 -->
        <BuyVehiclePanel v-if="!managed" :key="'buy-' + vehicle.id" :vehicle="vehicle" />
        <!-- 市场详情只展示仍在售的车辆，预约表单再按会话显示登录或提交入口。 -->
        <AppointmentForm v-if="!managed" :key="'appointment-' + vehicle.id" :vehicle-id="vehicle.id" />
        <!-- 卖家从自己的代销车详情进入委托表单和历史。 -->
        <section v-else-if="vehicle.saleMode === 'CONSIGN'" class="vehicle-panel booking-section">
          <h2>代销委托</h2>
          <p class="muted-text">车源上架需要当前有效的委托。你可以提交委托或查看审核结果。</p>
          <div><RouterLink class="solid-link" :to="{ name: 'my-consignments', params: { id: vehicle.id } }">办理代销委托</RouterLink></div>
        </section>
        <section v-if="managed && (vehicle.reviewNote || canEdit(vehicle))" class="vehicle-panel">
          <h2>审核与发布</h2>
          <p v-if="vehicle.reviewNote" class="vehicle-description">{{ vehicle.reviewNote }}</p>
          <RouterLink v-if="canEdit(vehicle)" class="solid-link" :to="{ name: 'vehicle-edit', params: { id: vehicle.id } }">修改并重提</RouterLink>
        </section>
      </div>
    </template>
  </main>
</template>
