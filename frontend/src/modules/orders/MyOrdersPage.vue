<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue' // 页面打开后按当前条件请求订单。
import { orderApi } from '../../api/orders.js' // 只访问会话账号可见的订单列表。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 管理分页请求并丢弃过期响应。
import { orderSides } from './orderLabels.js' // 展示买入、卖出及全部筛选项。
import OrderTable from './OrderTable.vue' // 显示统一的交易列表。

const side = ref('ALL') // 用户首次打开可以看到自己全部订单。
const status = ref('') // 状态默认不限制。
const page = useVehiclePage((query) => orderApi.mine({ ...query, status: status.value, side: side.value }), 10) // 让服务端筛选及分页。
const columns = [
  { key: 'vehicleModel', label: '车辆 / 订单' }, // 通过车名与订单编号识别一笔交易。
  { key: 'side', label: '交易角色', format: (entry) => orderSides[entry.side] }, // 区分该账号是买家还是卖家。
  { key: 'amount', label: '订单金额' }, // 使用下单时成交金额快照。
  { key: 'status', label: '状态' }, // 显示当前办理步骤。
  { key: 'createdAt', label: '下单时间' }, // 显示用来确认订单先后的业务时间。
]

function filterStatus(value) {
  status.value = value // 保存新选中的订单状态。
  void page.load(1) // 更换筛选后重新从第一页开始。
}

onMounted(() => page.load()) // 直接打开个人订单页也能立即看到记录。
</script>

<template>
  <!-- 个人中心订单列表。 -->
  <main class="public-width vehicle-page booking-page">
    <RouterLink class="vehicle-back" :to="{ name: 'account' }">← 返回个人中心</RouterLink>
    <section class="vehicle-panel booking-section" aria-labelledby="personal-order-title">
      <header class="page-heading">
        <h1 id="personal-order-title">我的订单</h1>
        <p>查看买入、卖出及每笔交易的进度。</p>
      </header>
      <OrderTable :page="page" :status="status" :columns="columns" @update:status="filterStatus">
        <!-- 角色和状态一起筛选，跨角色切换时重置页码。 -->
        <template #filters>
          <label>交易角色
            <select v-model="side" @change="page.load(1)">
              <option v-for="(label, key) in orderSides" :key="key" :value="key">{{ label }}</option>
            </select>
          </label>
        </template>
      </OrderTable>
    </section>
  </main>
</template>
