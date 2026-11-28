<!-- @author pangzhihao -->
<script setup>
import { auth } from '../auth/session.js'
</script>

<template>
  <main class="public-width admin-container">
    <div class="admin-shell">
      <aside class="admin-sidebar">
        <span class="admin-brand">交易管理</span>
        <nav aria-label="管理端导航">
          <RouterLink :to="{ name: 'admin' }">工作台</RouterLink>
          <RouterLink :to="{ name: 'vehicle-review' }">车源审核</RouterLink>
          <RouterLink :to="{ name: 'report-review' }">车况报告</RouterLink>
          <!-- 业务队列继承管理布局的工作人员权限。 -->
          <RouterLink :to="{ name: 'consignment-review' }">代销委托</RouterLink>
          <RouterLink :to="{ name: 'appointment-queue' }">看车预约</RouterLink>
          <!-- 已模拟支付的交易从订单队列办理过户。 -->
          <RouterLink :to="{ name: 'order-queue' }">交易订单</RouterLink>
          <!-- 工作人员使用独立队列处理贷款申请。 -->
          <RouterLink :to="{ name: 'loan-queue' }">贷款办理</RouterLink>
          <!-- 工作人员从工单队列统一办理售前和售后服务。 -->
          <RouterLink :to="{ name: 'repair-queue' }">整备与送修</RouterLink>
          <!-- 工作人员可查询业务摘要，管理员可查询完整日志。 -->
          <RouterLink :to="{ name: 'audit-logs' }">操作日志</RouterLink>
          <RouterLink v-if="auth.isAdministrator()" :to="{ name: 'admin-dictionaries' }">基础字典</RouterLink>
          <RouterLink v-if="auth.isAdministrator()" :to="{ name: 'admin-users' }">账号管理</RouterLink>
          <RouterLink :to="{ name: 'account' }">返回个人中心</RouterLink>
        </nav>
      </aside>
      <section class="admin-content">
        <RouterView />
      </section>
    </div>
  </main>
</template>
