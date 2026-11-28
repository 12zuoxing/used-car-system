<!-- @author pangzhihao -->
<script setup>
import Tag from 'primevue/tag'
import { auth, roleLabels } from './session.js'
</script>

<template>
  <main v-if="auth.state.account" class="public-width account-page">
    <section class="account-panel" aria-labelledby="account-title">
      <h1 id="account-title">个人中心</h1>
      <dl class="profile-details">
        <div><dt>显示名称</dt><dd>{{ auth.state.account.displayName }}</dd></div>
        <div><dt>用户名</dt><dd>{{ auth.state.account.username }}</dd></div>
        <div>
          <dt>当前角色</dt>
          <dd class="role-list"><Tag v-for="role in auth.state.account.roles" :key="role" :value="roleLabels[role]" severity="info" /></dd>
        </div>
      </dl>
      <div class="vehicle-actions">
        <RouterLink class="solid-link" :to="{ name: 'my-vehicles' }">我的车源</RouterLink>
        <!-- 买家进入个人预约页查看确认进度与处理说明。 -->
        <RouterLink :to="{ name: 'my-appointments' }">我的预约</RouterLink>
        <RouterLink v-if="auth.isManager()" :to="{ name: 'admin' }">打开管理端</RouterLink>
      </div>
    </section>
  </main>
</template>
