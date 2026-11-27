<!-- @author pangzhihao -->
<script setup>
import { auth, roleLabels } from '../auth/session.js'
</script>

<template>
  <section aria-labelledby="workspace-title">
    <header class="page-heading">
      <h1 id="workspace-title">管理工作台</h1>
      <p>{{ auth.state.account?.displayName }} · {{ auth.state.account?.roles.map((role) => roleLabels[role]).join('、') }}</p>
    </header>
    <RouterLink v-if="auth.isAdministrator()" :to="{ name: 'admin-users' }" class="solid-link">管理账号与角色</RouterLink>
  </section>
</template>
