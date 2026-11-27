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
