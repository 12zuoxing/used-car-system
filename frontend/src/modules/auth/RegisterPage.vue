<!-- @author pangzhihao -->
<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Message from 'primevue/message'
import AuthLayout from './AuthLayout.vue'
import { auth } from './session.js'

const router = useRouter()
const form = reactive({ username: '', displayName: '', password: '', confirmation: '' })
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  if (submitting.value) return
  if (form.password !== form.confirmation) {
    errorMessage.value = '两次输入的密码不一致。'
    return
  }
  if (new TextEncoder().encode(form.password).length > 72) {
    errorMessage.value = '密码不能超过 72 个 UTF-8 字节。'
    return
  }
  errorMessage.value = ''
  submitting.value = true
  try {
    const account = await auth.register({
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
    })
    // 注册成功后进入登录页，密码和确认密码都不会进入 URL。
    await router.replace({ name: 'login', query: { registered: '1', username: account.username } })
  } catch (error) {
    errorMessage.value = error.message || '注册失败，请重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="注册账号">
    <form class="auth-form" @submit.prevent="submit">
      <label for="register-username">用户名</label>
      <InputText id="register-username" v-model="form.username" autocomplete="username" minlength="3" maxlength="32" pattern="[a-zA-Z0-9_]{3,32}" aria-describedby="username-help" required />
      <small id="username-help">3–32 位字母、数字或下划线</small>
      <label for="register-display-name">显示名称</label>
      <InputText id="register-display-name" v-model="form.displayName" autocomplete="nickname" maxlength="40" required />
      <label for="register-password">密码</label>
      <Password v-model="form.password" input-id="register-password" autocomplete="new-password" :feedback="false" toggle-mask fluid :input-props="{ required: true, minlength: 8, maxlength: 72, 'aria-describedby': 'password-help' }" />
      <small id="password-help">至少 8 个字符，最长 72 个 UTF-8 字节</small>
      <label for="register-confirmation">确认密码</label>
      <Password v-model="form.confirmation" input-id="register-confirmation" autocomplete="new-password" :feedback="false" fluid :input-props="{ required: true }" />
      <Message v-if="errorMessage" severity="error" :closable="false">{{ errorMessage }}</Message>
      <Button class="form-submit" type="submit" label="注册" :loading="submitting" />
    </form>
    <p class="auth-switch">已有账号？<RouterLink :to="{ name: 'login' }">去登录</RouterLink></p>
  </AuthLayout>
</template>
