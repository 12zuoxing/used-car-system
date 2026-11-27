<!-- @author pangzhihao -->
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { auth } from './modules/auth/session.js'

const router = useRouter()
const leaving = ref(false)
const notice = ref('')

/** 退出成功后以首页替换当前位置，浏览器回退时也会重新经过路由权限检查。 */
async function logout() {
  leaving.value = true
  notice.value = ''
  try {
    await auth.logout()
    await router.replace({ name: 'home' })
  } catch (error) {
    notice.value = error.message || '退出失败，请重试。'
  } finally {
    leaving.value = false
  }
}
</script>

<template>
  <header class="main-header">
    <div class="public-width header-inner">
      <RouterLink class="brand" :to="{ name: 'home' }">
        <span class="brand-mark" aria-hidden="true">车</span>
        <span>二手车交易</span>
      </RouterLink>
      <nav class="header-links" aria-label="主导航">
        <RouterLink :to="{ name: 'home' }" class="nav-text">首页</RouterLink>
        <template v-if="auth.state.account">
          <RouterLink :to="{ name: 'account' }" class="nav-text">个人中心</RouterLink>
          <RouterLink v-if="auth.isManager()" :to="{ name: 'admin' }" class="nav-text">管理端</RouterLink>
          <Button label="退出登录" severity="secondary" size="small" variant="outlined" :loading="leaving" @click="logout" />
        </template>
        <template v-else>
          <RouterLink :to="{ name: 'login' }" class="nav-text">登录</RouterLink>
          <RouterLink :to="{ name: 'register' }" class="solid-link">注册</RouterLink>
        </template>
      </nav>
    </div>
  </header>
  <div v-if="notice" class="public-width global-notice" role="alert">{{ notice }}</div>
  <RouterView />
</template>
