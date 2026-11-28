<!-- @author pangzhihao -->
<script setup>
import { computed, ref } from 'vue' // 让表单和处理结果响应用户操作。
import { useRoute } from 'vue-router' // 保留未登录买家成功登录后的返回地址。
import Button from 'primevue/button' // 使用项目主题中的提交控件。
import Message from 'primevue/message' // 在表单旁边显示结果。
import { appointmentApi } from '../../api/bookings.js' // 向当前车辆的预约接口发送请求。
import { auth } from '../auth/session.js' // 根据当前会话决定显示表单还是登录入口。
import { businessInputTime } from '../bookings/bookingLabels.js' // 保证日期选择器的最早时间是北京时间。

const props = defineProps({ vehicleId: { type: Number, required: true } }) // 接收详情页已经加载的车辆编号。
const route = useRoute() // 读取来源页面供登录后跳回。
const visitAt = ref('') // 保存买家所选的北京时间。
const note = ref('') // 保存买家补充的现场看车需求。
const saving = ref(false) // 禁止请求完成前再次提交。
const error = ref('') // 保存用于表单反馈的错误说明。
const submitted = ref(false) // 预约提交成功后展示进度入口。
const loginLink = computed(() => ({ name: 'login', query: { redirect: route.fullPath } })) // 让游客登录后返回同一辆车。

async function reserve() {
  if (saving.value) return // 避免快速点击重复传输。
  saving.value = true // 在审批结果返回前锁定按钮。
  error.value = '' // 清除上一次尝试的提示。
  try {
    await appointmentApi.create(props.vehicleId, { visitAt: visitAt.value, note: note.value.trim() }) // 提交经过浏览器基础检查的表单。
    submitted.value = true // 切换到预约已提交的界面。
  } catch (failure) {
    error.value = failure.message // 展示诸如本人车源或车辆已下架的实际错误。
  } finally {
    saving.value = false // 允许失败后修改信息并重新提交。
  }
}
</script>

<template>
  <!-- 在公开详情页提供就近预约入口。 -->
  <section class="vehicle-panel booking-section" aria-labelledby="appointment-form-title">
    <h2 id="appointment-form-title">预约看车</h2>
    <template v-if="!auth.state.account">
      <p class="muted-text">登录后可选择到访时间，工作人员会处理你的预约。</p>
      <div><RouterLink :to="loginLink" class="solid-link">登录后预约</RouterLink></div>
    </template>
    <!-- 预约成功后只保留进度入口，避免在同一页面重复提交。 -->
    <template v-else-if="submitted">
      <Message severity="success" :closable="false">预约已提交，等待工作人员确认。</Message>
      <RouterLink :to="{ name: 'my-appointments' }">查看我的预约</RouterLink>
    </template>
    <!-- 已登录买家填写到访时间和可选说明。 -->
    <form v-else class="vehicle-form" @submit.prevent="reserve">
      <label>到访时间（北京时间）<input v-model="visitAt" type="datetime-local" :min="businessInputTime()" required /></label>
      <label class="vehicle-form-wide">看车备注<textarea v-model="note" placeholder="可填写想重点查看的车辆部位" maxlength="400" rows="3" /></label>
      <Message v-if="error" class="vehicle-form-wide" severity="error" :closable="false">{{ error }}</Message>
      <div class="vehicle-form-wide vehicle-actions">
        <Button type="submit" label="提交预约" :loading="saving" />
        <RouterLink :to="{ name: 'my-appointments' }">我的预约</RouterLink>
      </div>
    </form>
  </section>
</template>
