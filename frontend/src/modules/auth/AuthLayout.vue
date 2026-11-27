<!-- @author pangzhihao -->
<script setup>
defineProps({
  title: { type: String, required: true },
})
</script>

<template>
  <main class="public-width auth-main">
    <section class="auth-shell">
      <div class="auth-image" aria-hidden="true">
        <img src="/images/showroom.jpg" alt="" />
      </div>
      <div class="auth-content">
        <header class="auth-heading"><h1>{{ title }}</h1><slot name="intro" /></header>
        <slot />
      </div>
    </section>
  </main>
</template>
