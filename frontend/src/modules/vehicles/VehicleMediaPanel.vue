<!-- @author pangzhihao -->
<script setup>
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { attachmentUrl, mediaApi } from '../../api/media.js'
import AttachmentUpload from '../reports/AttachmentUpload.vue'
import ReportEditor from '../reports/ReportEditor.vue'
import { reportStatuses } from '../reports/reportLabels.js'
import VehicleGallery from './VehicleGallery.vue'

const props = defineProps({
  vehicle: { type: Object, required: true },
  managed: { type: Boolean, default: false },
})
const photos = ref([])
const reports = ref([])
const loading = ref(true)
const error = ref('')
const writing = ref(false)
const notice = ref('')
let version = 0

const canAddPhoto = computed(() => props.managed && ['PENDING', 'REJECTED', 'OFFLINE'].includes(props.vehicle.status))
const canAddReport = computed(() => props.managed && !['LOCKED', 'SOLD'].includes(props.vehicle.status))

async function load() {
  const request = ++version
  loading.value = true
  error.value = ''
  const [images, records] = await Promise.allSettled([
    mediaApi.photos(props.vehicle.id), mediaApi.reports(props.vehicle.id),
  ])
  if (request !== version) return
  photos.value = images.status === 'fulfilled' ? images.value : []
  // 即使卖家或工作人员访问市场详情，也按公开视图展示已通过的报告。
  reports.value = records.status === 'fulfilled'
    ? records.value.filter((report) => props.managed || report.status === 'APPROVED')
    : []
  if (images.status === 'rejected' || records.status === 'rejected') error.value = '部分车源资料读取失败。'
  loading.value = false
}

function reportSaved() {
  writing.value = false
  notice.value = '报告已提交，审核通过后会在公开详情展示。'
  load()
}

watch(() => [props.vehicle.id, props.managed], () => {
  writing.value = false
  notice.value = ''
  photos.value = []
  reports.value = []
  load()
}, { immediate: true })
</script>

<template>
  <section class="vehicle-panel">
    <h2>车源照片</h2>
    <p v-if="loading && !photos.length" class="muted-text" role="status">正在读取照片…</p>
    <VehicleGallery v-else :photos="photos" />
    <AttachmentUpload v-if="canAddPhoto" :vehicle-id="vehicle.id" @uploaded="load" />
  </section>
  <section class="vehicle-panel">
    <div class="vehicle-section-head">
      <h2>车况报告</h2>
      <Button v-if="canAddReport && !writing" label="登记报告" size="small" variant="outlined" @click="writing = true" />
    </div>
    <Message v-if="error" severity="error" :closable="false">
      {{ error }} <button class="text-button" type="button" @click="load">重试</button>
    </Message>
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <ReportEditor v-if="writing && canAddReport" :vehicle-id="vehicle.id" @saved="reportSaved" @cancelled="writing = false" />
    <p v-if="loading && !reports.length" class="muted-text" role="status">正在读取报告…</p>
    <p v-else-if="!reports.length && !writing" class="muted-text">暂无车况报告。</p>
    <article v-for="report in reports" :key="report.id" class="vehicle-report-item">
      <div class="vehicle-section-head">
        <h3>{{ report.source }}</h3>
        <Tag v-if="managed || report.status === 'APPROVED'" :value="reportStatuses[report.status].label"
          :severity="reportStatuses[report.status].severity" />
      </div>
      <p class="muted-text">检测日期 {{ report.reportDate }}</p>
      <p class="vehicle-description">{{ report.summary }}</p>
      <p v-if="managed && report.reviewNote" class="muted-text">审核说明 {{ report.reviewNote }}</p>
      <ul v-if="report.attachments.length" class="report-files">
        <li v-for="file in report.attachments" :key="file.id"><a :href="attachmentUrl(file.id)">{{ file.name }}</a></li>
      </ul>
      <AttachmentUpload v-if="canAddReport && report.status === 'PENDING'" :vehicle-id="vehicle.id"
        :report-id="report.id" @uploaded="load" />
    </article>
  </section>
</template>
