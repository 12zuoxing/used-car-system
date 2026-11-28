<!-- @author pangzhihao -->
<script setup>
import { onMounted, onUnmounted, ref } from 'vue' // 跟踪根布局与通知连接的生命周期。
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { auth } from './modules/auth/session.js'
import { inbox, installNotificationInbox } from './modules/notifications/inbox.js' // 在登录会话下管理实时提示和未读数。

const router = useRouter()
const leaving = ref(false)
const notice = ref('')
let stopInbox // 保存根布局卸载时需要运行的清理函数。
onMounted(() => { stopInbox = installNotificationInbox() }) // 页面和会话都准备好后开始监听。
onUnmounted(() => stopInbox?.()) // 关闭连接及定时回调，避免重复注册。

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
        <RouterLink :to="{ name: 'vehicle-market' }" class="nav-text">车源市场</RouterLink>
        <template v-if="auth.state.account">
          <!-- 未读计数同步反映本人收件箱，链接可直接打开通知。 -->
          <RouterLink :to="{ name: 'notifications' }" class="nav-text notification-link"
            :aria-label="inbox.state.unread ? '通知，' + inbox.state.unread + ' 条未读' : '通知'">
            通知<span v-if="inbox.state.unread" class="notification-count" aria-hidden="true">{{ inbox.state.unread > 99 ? '99+' : inbox.state.unread }}</span>
          </RouterLink>
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
