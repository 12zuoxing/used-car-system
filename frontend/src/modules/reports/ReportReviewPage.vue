<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { attachmentUrl, mediaApi } from '../../api/media.js'
import { useVehiclePage } from '../vehicles/useVehiclePage.js'
import { reportStatuses } from './reportLabels.js'

const status = ref('PENDING')
const target = ref(null)
const visible = ref(false)
const note = ref('')
const saving = ref(false)
const notice = ref('')
const queueWarning = ref('')
const reviewError = ref('')
const page = useVehiclePage((query) => mediaApi.queue({ ...query, status: status.value }), 10)

function inspect(report) {
  target.value = report
  note.value = ''
  reviewError.value = ''
  queueWarning.value = ''
  visible.value = true
}

async function review(decision) {
  if (saving.value) return
  const reason = note.value.trim()
  if (decision === 'REJECT' && !reason) {
    reviewError.value = '退回时请填写原因。'
    return
  }
  saving.value = true
  reviewError.value = ''
  try {
    const { id, revision } = target.value
    await mediaApi.review(id, { revision, decision, note: reason })
    visible.value = false
    notice.value = decision === 'APPROVE' ? '报告审核通过。' : '报告已退回，发布人可以查看原因。'
    await page.load(1)
  } catch (failure) {
    // 附件更新或报告已被其他经办人处理时，关闭旧快照并刷新队列。
    if (['STALE_REVIEW', 'REPORT_STATE_CONFLICT'].includes(failure.code)) {
      visible.value = false
      notice.value = ''
      queueWarning.value = failure.message
      await page.load(1)
    } else {
      reviewError.value = failure.message
    }
  } finally {
    saving.value = false
  }
}

onMounted(() => page.load())
</script>

<template>
  <section aria-labelledby="report-review-title">
    <header class="page-heading"><h1 id="report-review-title">车况报告审核</h1><p>核对报告来源、日期和附件。</p></header>
    <div class="vehicle-toolbar">
      <label>审核状态<select v-model="status" @change="page.load(1)"><option value="">全部</option><option v-for="(item, key) in reportStatuses"
        :key="key" :value="key">{{ item.label }}</option></select></label>
      <Button label="刷新" size="small" severity="secondary" variant="outlined" :loading="page.loading.value" @click="page.load(1)" />
    </div>
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <Message v-if="queueWarning" severity="warn" :closable="false">{{ queueWarning }}</Message>
    <Message v-if="page.error.value" severity="error" :closable="false">{{ page.error.value }}</Message>
    <DataTable :value="page.data.value.items" data-key="id" :loading="page.loading.value" :rows="page.data.value.pageSize"
      :first="(page.data.value.page - 1) * page.data.value.pageSize" :total-records="page.data.value.total"
      :table-style="{ minWidth: '560px' }" lazy paginator striped-rows scrollable
      paginator-template="PrevPageLink PageLinks NextPageLink" @page="page.load($event.page + 1)">
      <template #empty>暂无符合条件的报告。</template>
      <Column field="vehicleModel" header="车源" />
      <Column field="source" header="报告来源" />
      <Column field="reportDate" header="检测日期" />
      <Column header="状态"><template #body="{ data }"><Tag :value="reportStatuses[data.status].label" :severity="reportStatuses[data.status].severity" /></template></Column>
      <Column header="操作"><template #body="{ data }"><Button :label="data.status === 'PENDING' ? '审核' : '查看'" size="small" variant="text" @click="inspect(data)" /></template></Column>
    </DataTable>

    <Dialog v-model:visible="visible" :header="target?.status === 'PENDING' ? '审核报告' : '查看报告'" modal
      :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(550px, 94vw)' }">
      <div v-if="target" class="review-content">
        <h2>{{ target.source }}</h2>
        <p class="muted-text">{{ target.vehicleModel }} · {{ target.reportDate }}</p>
        <p class="vehicle-description">{{ target.summary }}</p>
        <ul v-if="target.attachments.length" class="report-files">
          <li v-for="file in target.attachments" :key="file.id"><a :href="attachmentUrl(file.id)">{{ file.name }}</a></li>
        </ul>
        <p v-else class="muted-text">此报告未附文件。</p>
        <label v-if="target.status === 'PENDING'" class="vehicle-dialog-label">审核说明
          <textarea v-model="note" rows="3" maxlength="500" placeholder="退回时需填写原因" />
        </label>
        <p v-else-if="target.reviewNote" class="vehicle-description">审核说明 {{ target.reviewNote }}</p>
        <Message v-if="reviewError" severity="error" :closable="false">{{ reviewError }}</Message>
      </div>
      <template #footer>
        <Button label="关闭" severity="secondary" variant="outlined" :disabled="saving" @click="visible = false" />
        <template v-if="target?.status === 'PENDING'">
          <Button label="退回报告" severity="danger" variant="outlined" :disabled="saving" @click="review('REJECT')" />
          <Button label="通过报告" :loading="saving" @click="review('APPROVE')" />
        </template>
      </template>
    </Dialog>
  </section>
</template>
