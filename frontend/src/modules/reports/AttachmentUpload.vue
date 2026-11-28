<!-- @author pangzhihao -->
<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { acceptedPhotos, acceptedReports, mediaApi } from '../../api/media.js'

const props = defineProps({
  vehicleId: { type: Number, required: true },
  reportId: { type: Number, default: null },
})
const emit = defineEmits(['uploaded'])
const file = ref(null)
const field = ref(null)
const busy = ref(false)
const error = ref('')

async function upload() {
  if (!file.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const saved = await mediaApi.upload(props.vehicleId, file.value, props.reportId)
    file.value = null
    if (field.value) field.value.value = ''
    emit('uploaded', saved)
  } catch (failure) {
    error.value = failure.message || '上传失败，请重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form class="attachment-upload" @submit.prevent="upload">
    <label>{{ reportId ? '补充报告附件' : '补充车辆照片' }}
      <input ref="field" type="file" :accept="reportId ? acceptedReports : acceptedPhotos"
        :disabled="busy" @change="file = $event.target.files[0] ?? null" />
    </label>
    <Button type="submit" label="上传" size="small" :disabled="!file" :loading="busy" />
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
  </form>
</template>
