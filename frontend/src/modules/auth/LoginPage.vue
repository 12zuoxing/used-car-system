<!-- @author pangzhihao -->
<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Message from 'primevue/message'
import AuthLayout from './AuthLayout.vue'
import { auth } from './session.js'

const route = useRoute()
const router = useRouter()
const form = reactive({
  username: typeof route.query.username === 'string' ? route.query.username : '',
  password: '',
})
const errorMessage = ref('')
const submitting = ref(false)

/** 只接受本站路径。后台回跳仍然会执行角色检查。 */
function destination() {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//') && !redirect.includes('\\')) {
    return redirect
  }
  return { name: auth.isManager() ? 'admin' : 'account' }
}

async function submit() {
  if (submitting.value) return
  errorMessage.value = ''
  submitting.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    form.password = ''
    await router.replace(destination())
  } catch (error) {
    errorMessage.value = error.message || '登录失败，请重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="登录">
    <template #intro>登录后可查看个人资料及可使用的管理功能。</template>
    <Message v-if="route.query.registered === '1'" severity="success" :closable="false">注册成功，请登录。</Message>
    <Message v-if="route.query.unavailable === '1'" severity="warn" :closable="false">
      暂时无法恢复登录状态。
      <button type="button" class="text-button" @click="router.replace(destination())">重试</button>
    </Message>
    <form class="auth-form" @submit.prevent="submit">
      <label for="login-username">用户名</label>
      <InputText id="login-username" v-model="form.username" autocomplete="username" maxlength="32" required />
      <label for="login-password">密码</label>
      <Password v-model="form.password" input-id="login-password" autocomplete="current-password" :feedback="false" toggle-mask fluid :input-props="{ required: true }" />
      <Message v-if="errorMessage" severity="error" :closable="false">{{ errorMessage }}</Message>
      <Button class="form-submit" type="submit" label="登录" :loading="submitting" />
    </form>
    <p class="auth-switch">还没有账号？<RouterLink :to="{ name: 'register' }">注册账号</RouterLink></p>
  </AuthLayout>
</template>
