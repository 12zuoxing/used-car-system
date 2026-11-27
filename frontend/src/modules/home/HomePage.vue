<!-- @author pangzhihao -->
<template>
  <main class="public-width home-content">
    <section class="home-hero" aria-labelledby="home-title">
      <img src="/images/showroom.jpg" alt="" class="hero-photo" />
      <div class="home-hero-copy">
        <h1 id="home-title">二手车交易管理系统</h1>
        <p>买车、卖车，共用一个账号。</p>
        <div class="vehicle-actions">
          <RouterLink class="solid-link" :to="{ name: 'vehicle-market' }">浏览车源</RouterLink>
          <RouterLink class="solid-link" :to="{ name: 'vehicle-publish' }">发布车源</RouterLink>
        </div>
      </div>
    </section>
  </main>
</template>
