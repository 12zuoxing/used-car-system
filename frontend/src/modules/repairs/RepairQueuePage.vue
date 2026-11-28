<!-- @author pangzhihao -->
<script setup>
import RepairTable from './RepairTable.vue' // 以后台权限读取可办理的工单。
</script>

<template>
  <!-- 工单队列沿用管理端侧边导航和权限规则。 -->
  <section class="booking-section" aria-labelledby="repair-queue-title">
    <header class="page-heading">
      <h1 id="repair-queue-title">整备与送修</h1>
      <p>按业务类型查看工单，核对到店信息并登记处理结果。</p>
    </header>
    <RepairTable managed />
  </section>
</template>
