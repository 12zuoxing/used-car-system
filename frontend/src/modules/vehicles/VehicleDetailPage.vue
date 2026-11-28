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
import { canRequestPreSale } from '../repairs/repairLabels.js' // 本人车源详情可提供售前整备入口。
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
watch([() => route.params.id, () => props.managed], load, { immediate: true })

function jumpTo(title) {
  const heading = document.getElementById(title) // 使用标题定位，浏览器和读屏均能跟随当前区块。
  heading?.setAttribute('tabindex', '-1')
  heading?.focus({ preventScroll: true })
  heading?.scrollIntoView({ block: 'start' })
}
</script>

<template>
  <main class="public-width vehicle-page" :class="{ 'vehicle-detail-public': !managed }">
    <RouterLink class="vehicle-back" :to="managed ? { name: 'my-vehicles' } : { name: 'vehicle-market', query: route.query }">
      ← {{ managed ? '我的车源' : '返回车源市场' }}
    </RouterLink>
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
        <div class="vehicle-detail-quote"><span>车源报价</span><strong>{{ formatPrice(vehicle.price) }}</strong></div>
      </section>
      <nav v-if="!managed" class="vehicle-detail-jumps" aria-label="跳转到详情区块">
        <button type="button" @click="jumpTo('vehicle-photo-title')">车源照片</button>
        <button type="button" @click="jumpTo('vehicle-facts-title')">车辆参数</button>
        <button type="button" @click="jumpTo('vehicle-report-title')">车况报告</button>
        <button type="button" @click="jumpTo('appointment-form-title')">预约看车 <span aria-hidden="true">↗</span></button>
      </nav>
      <div class="vehicle-detail-layout">
        <div class="vehicle-detail-body">
          <VehicleMediaPanel :key="vehicle.id" :vehicle="vehicle" :managed="managed">
            <section class="vehicle-panel vehicle-spec-panel" aria-labelledby="vehicle-facts-title">
              <h2 id="vehicle-facts-title">车辆参数</h2>
              <dl class="vehicle-facts">
                <div><dt>品牌</dt><dd>{{ vehicle.brandName }}</dd></div><div><dt>行驶里程</dt><dd>{{ formatMileage(vehicle.mileage) }}</dd></div>
                <div><dt>变速箱</dt><dd>{{ transmissions[vehicle.transmission] }}</dd></div><div><dt>能源</dt><dd>{{ fuels[vehicle.fuel] }}</dd></div>
              </dl>
              <h3>车辆描述</h3>
              <p class="vehicle-description">{{ vehicle.description }}</p>
            </section>
          </VehicleMediaPanel>
          <!-- 卖家从自己的代销车详情进入委托表单和历史。 -->
          <section v-if="managed && vehicle.saleMode === 'CONSIGN'" class="vehicle-panel booking-section">
            <h2>代销委托</h2>
            <p class="muted-text">车源上架需要当前有效的委托。你可以提交委托或查看审核结果。</p>
            <div><RouterLink class="solid-link" :to="{ name: 'my-consignments', params: { id: vehicle.id } }">办理代销委托</RouterLink></div>
          </section>
          <section v-if="managed && (vehicle.reviewNote || canEdit(vehicle))" class="vehicle-panel">
            <h2>审核与发布</h2>
            <p v-if="vehicle.reviewNote" class="vehicle-description">{{ vehicle.reviewNote }}</p>
            <RouterLink v-if="canEdit(vehicle)" class="solid-link" :to="{ name: 'vehicle-edit', params: { id: vehicle.id } }">修改并重提</RouterLink>
          </section>
          <!-- 售前表单以本人的车辆编号为申请上下文。 -->
          <section v-if="managed && canRequestPreSale(vehicle)" class="vehicle-panel booking-section">
            <h2>售前整备</h2>
            <div><RouterLink class="solid-link" :to="{ name: 'pre-sale-repair', params: { id: vehicle.id } }">申请售前整备</RouterLink></div>
          </section>
        </div>
        <!-- 详情保留原有下单和预约组件，成交、锁车和预约校验继续由服务端处理。 -->
        <aside v-if="!managed" class="vehicle-detail-actions" aria-label="购车与预约">
          <BuyVehiclePanel :key="'buy-' + vehicle.id" :vehicle="vehicle" />
          <AppointmentForm :key="'appointment-' + vehicle.id" :vehicle-id="vehicle.id" />
        </aside>
      </div>
    </template>
  </main>
</template>
