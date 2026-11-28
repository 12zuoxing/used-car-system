<!-- @author pangzhihao -->
<script setup>
import RepairTable from './RepairTable.vue' // 复用个人工单的分页历史。
</script>

<template>
  <!-- 个人中心工单页只读取当前申请人名下的记录。 -->
  <main class="public-width vehicle-page booking-page">
    <RouterLink class="vehicle-back" :to="{ name: 'account' }">← 返回个人中心</RouterLink>
    <section class="vehicle-panel booking-section" aria-labelledby="my-repairs-title">
      <header class="page-heading">
        <h1 id="my-repairs-title">我的工单</h1>
        <p>查看售前整备和售后送修的安排与办理结果。</p>
      </header>
      <RepairTable />
    </section>
  </main>
</template>
