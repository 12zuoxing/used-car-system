<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue' // 首次进入时读取登记结果并保留筛选项。
import { loanApi } from '../../api/loans.js' // 从个人接口取得本人申请。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 管理分页并防止过期结果覆盖。
import LoanTable from './LoanTable.vue' // 在表格中展示状态与补充入口。

const status = ref('') // 个人中心默认展示所有申请状态。
const page = useVehiclePage((query) => loanApi.mine({ ...query, status: status.value }), 10) // 状态和分页由服务端处理。

function filter(value) {
  status.value = value // 保存选中的办理状态。
  void page.load(1) // 切换状态时回到第一页。
}

onMounted(() => page.load()) // 页面挂载后读取初始列表。
</script>

<template>
  <!-- 买家可从个人中心查询贷款办理进度。 -->
  <main class="public-width vehicle-page booking-page">
    <RouterLink class="vehicle-back" :to="{ name: 'account' }">← 返回个人中心</RouterLink>
    <section class="vehicle-panel booking-section" aria-labelledby="my-loans-title">
      <header class="page-heading">
        <h1 id="my-loans-title">我的贷款</h1>
        <p>查看申请进度、补充要求和办理结果。</p>
      </header>
      <LoanTable :page="page" :status="status" @update:status="filter" />
    </section>
  </main>
</template>
