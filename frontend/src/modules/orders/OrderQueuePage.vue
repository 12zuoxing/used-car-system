<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue' // 在页面挂载时加载后台订单。
import { useRoute } from 'vue-router' // 让工作台状态链接可以打开对应队列。
import { orderApi } from '../../api/orders.js' // 通过已授权的后台接口读取队列。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 使用统一分页组合函数。
import OrderTable from './OrderTable.vue' // 显示后台订单的当前状态与处理入口。
import { orderStatuses } from './orderLabels.js' // 仅接受真实存在的订单状态。

const initial = useRoute().query.status // 读取从待办统计页带入的查找条件。
const status = ref(typeof initial === 'string' && Object.hasOwn(orderStatuses, initial) ? initial : 'PAID') // 从统计页来时选中该状态，否则优先展示待过户。
const page = useVehiclePage((query) => orderApi.queue({ ...query, status: status.value }), 10) // 让后端按状态搜索分页。
const columns = [
  { key: 'vehicleModel', label: '车辆 / 订单' }, // 显示车源名称和订单编号。
  { key: 'buyerName', label: '买家' }, // 协助工作人员核对受让方。
  { key: 'sellerName', label: '卖家' }, // 协助工作人员核对转让方。
  { key: 'amount', label: '订单金额' }, // 展示双方确认的订单金额。
  { key: 'status', label: '状态' }, // 显示是否待过户或已经结案。
  { key: 'createdAt', label: '下单时间' }, // 展示办理历史的时间。
]

function filterStatus(value) {
  status.value = value // 保存工作人员选择的筛选条件。
  void page.load(1) // 保证筛选结果从第一页展示。
}

onMounted(() => page.load()) // 进入工作页后加载待办理记录。
</script>

<template>
  <!-- 后台交易队列沿用管理端的统一布局。 -->
  <section class="booking-section" aria-labelledby="order-queue-title">
    <header class="page-heading">
      <h1 id="order-queue-title">交易订单</h1>
      <p>查看订单状态，核对已支付订单后确认过户。</p>
    </header>
    <OrderTable :page="page" :status="status" :columns="columns" managed @update:status="filterStatus" />
  </section>
</template>
