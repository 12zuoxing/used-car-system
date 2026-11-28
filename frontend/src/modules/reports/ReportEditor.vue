<!-- @author pangzhihao -->
<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { mediaApi } from '../../api/media.js'
import ReportFields from './ReportFields.vue'

const props = defineProps({ vehicleId: { type: Number, required: true } })
const emit = defineEmits(['saved', 'cancelled'])
const fields = ref({ source: '', reportDate: '', summary: '' })
const file = ref(null)
const saving = ref(false)
const error = ref('')

async function submit() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    // 元数据与可选附件在同一个请求中提交，失败时保留表单供修正。
    const report = await mediaApi.submitReport(props.vehicleId, {
      source: fields.value.source.trim(), reportDate: fields.value.reportDate, summary: fields.value.summary.trim(),
    }, file.value)
    emit('saved', report)
  } catch (failure) {
    error.value = failure.message || '登记失败，请重试。'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="vehicle-form report-editor" aria-label="登记车况报告" @submit.prevent="submit">
    <ReportFields v-model="fields" :disabled="saving" @file-selected="file = $event" />
    <Message v-if="error" class="vehicle-form-wide" severity="error" :closable="false">{{ error }}</Message>
    <div class="vehicle-form-wide vehicle-actions">
      <Button type="submit" label="提交报告审核" :loading="saving" />
      <Button type="button" label="取消" variant="text" :disabled="saving" @click="emit('cancelled')" />
    </div>
  </form>
</template>
