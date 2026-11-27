<!-- @author pangzhihao -->
<script setup>
import { auth } from '../auth/session.js'
</script>

<template>
  <main class="public-width home-content">
    <section class="home-hero" aria-labelledby="home-title">
      <img src="/images/showroom.jpg" alt="" class="hero-photo" />
      <div class="home-hero-copy">
        <h1 id="home-title">二手车交易管理系统</h1>
        <p>买车、卖车，共用一个账号。</p>
        <RouterLink v-if="auth.state.account" class="solid-link" :to="{ name: 'account' }">进入个人中心</RouterLink>
        <RouterLink v-else class="solid-link" :to="{ name: 'register' }">创建账号</RouterLink>
      </div>
    </section>
  </main>
</template>
