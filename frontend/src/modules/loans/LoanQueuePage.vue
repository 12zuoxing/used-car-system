<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue' // 记录筛选条件并在打开时读取贷款队列。
import { loanApi } from '../../api/loans.js' // 请求工作人员有权查看的队列。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 复用分页状态和错误反馈。
import LoanTable from './LoanTable.vue' // 展示状态、申请人和办理入口。

const status = ref('PENDING') // 工作人员入口优先查看待处理的登记。
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
