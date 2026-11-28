<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue' // 记录筛选条件并在打开时读取贷款队列。
import { useRoute } from 'vue-router' // 从工作台链接读取选中的贷款状态。
import { loanApi } from '../../api/loans.js' // 请求工作人员有权查看的队列。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 复用分页状态和错误反馈。
import LoanTable from './LoanTable.vue' // 展示状态、申请人和办理入口。
import { loanStatuses } from './loanLabels.js' // 校验待办链接带入的状态值。

const initial = useRoute().query.status // 读取待办状态筛选值。
const status = ref(typeof initial === 'string' && Object.hasOwn(loanStatuses, initial) ? initial : 'PENDING') // 应用有效条件，默认使用待处理。
const page = useVehiclePage((query) => loanApi.queue({ ...query, status: status.value }), 10) // 从后端按状态获取数据。

function filter(value) {
  status.value = value // 更新处理队列目前的筛选。
  void page.load(1) // 重新读取符合条件的第一页。
}

onMounted(() => page.load()) // 首次打开后台页面时自动加载。
</script>

<template>
  <!-- 贷款经办页放在管理端布局下，继承工作人员权限。 -->
  <section class="booking-section" aria-labelledby="loan-queue-title">
    <header class="page-heading">
      <h1 id="loan-queue-title">贷款办理</h1>
      <p>核对贷款申请，登记补充要求或办理结果。</p>
    </header>
    <LoanTable :page="page" :status="status" managed @update:status="filter" />
  </section>
</template>
