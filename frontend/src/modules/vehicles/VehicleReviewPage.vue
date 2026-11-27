<!-- @author pangzhihao -->
<script setup>
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { vehicleApi } from '../../api/vehicles.js'
import { formatMileage, formatPrice, fuels, saleModes, statuses, transmissions, vehicleTitle } from './vehicleLabels.js'
import { useVehiclePage } from './useVehiclePage.js'

const status = ref('PENDING')
const target = ref(null)
const showReview = ref(false)
const note = ref('')
const saving = ref(false)
const notice = ref('')
const queueWarning = ref('')
const reviewError = ref('')
const page = useVehiclePage((query) => vehicleApi.queue({ ...query, status: status.value }), 10)

function openReview(vehicle) {
  target.value = vehicle
  note.value = ''
  reviewError.value = ''
  queueWarning.value = ''
  showReview.value = true
}

async function review(decision) {
  if (saving.value) return
  if (decision === 'REJECT' && !note.value.trim()) {
    reviewError.value = '退回时请填写原因。'
    return
  }
  saving.value = true
  reviewError.value = ''
  try {
    // 版本来自这一次查看的待审车源，后台会拒绝用旧资料作出审核决定。
    await vehicleApi.review(target.value.id, { revision: target.value.revision, decision, note: note.value.trim() })
    showReview.value = false
    notice.value = decision === 'APPROVE' ? '审核通过，车源已上架。' : '已退回，卖家可以查看原因并重新提交。'
    await page.load(1)
  } catch (failure) {
    if (['STALE_REVIEW', 'VEHICLE_STATE_CONFLICT'].includes(failure.code)) {
      // 当前弹窗对应旧版本。回到已刷新的队列，从最新车源重新打开审核。
      showReview.value = false
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
  <section aria-labelledby="vehicle-review-title">
    <header class="page-heading"><h1 id="vehicle-review-title">车源审核</h1><p>核对车辆资料，审核通过后在市场上架。</p></header>
    <div class="vehicle-toolbar">
      <label>审核状态<select v-model="status" @change="page.load(1)"><option value="">全部</option><option v-for="(item, key) in statuses" :key="key" :value="key">{{ item.label }}</option></select></label>
      <Button label="刷新" size="small" severity="secondary" variant="outlined" :loading="page.loading.value" @click="page.load(1)" />
    </div>
    <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
    <Message v-if="queueWarning" severity="warn" :closable="false">{{ queueWarning }}</Message>
    <Message v-if="page.error.value" severity="error" :closable="false">{{ page.error.value }}</Message>
    <DataTable :value="page.data.value.items" data-key="id" :loading="page.loading.value"
      :rows="page.data.value.pageSize" :first="(page.data.value.page - 1) * page.data.value.pageSize"
      :total-records="page.data.value.total" :table-style="{ minWidth: '630px' }"
      lazy paginator striped-rows scrollable paginator-template="PrevPageLink PageLinks NextPageLink" @page="page.load($event.page + 1)">
      <template #empty>没有符合条件的车源。</template>
      <Column header="车辆">
        <template #body="{ data }"><strong>{{ vehicleTitle(data) }}</strong><small class="table-vehicle-meta">{{ data.cityName }} · {{ data.registrationYear }} 年</small></template>
      </Column>
      <Column field="ownerName" header="发布人" />
      <Column header="经营方式"><template #body="{ data }">{{ saleModes[data.saleMode] }}</template></Column>
      <Column header="报价"><template #body="{ data }">{{ formatPrice(data.price) }}</template></Column>
      <Column header="状态"><template #body="{ data }"><Tag :value="statuses[data.status].label" :severity="statuses[data.status].severity" /></template></Column>
      <Column header="操作"><template #body="{ data }"><Button :label="data.status === 'PENDING' ? '审核' : '查看'" size="small" variant="text" @click="openReview(data)" /></template></Column>
    </DataTable>

    <Dialog v-model:visible="showReview" :header="target?.status === 'PENDING' ? '审核车源' : '查看车源'" modal
      :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(550px, 94vw)' }">
      <div v-if="target" class="review-content">
        <h2>{{ vehicleTitle(target) }}</h2>
        <p class="muted-text">{{ target.cityName }} · {{ target.registrationYear }} 年 · {{ formatMileage(target.mileage) }}</p>
        <dl class="vehicle-facts">
          <div><dt>发布人</dt><dd>{{ target.ownerName }}</dd></div><div><dt>售价</dt><dd>{{ formatPrice(target.price) }}</dd></div>
          <div><dt>能源与变速箱</dt><dd>{{ fuels[target.fuel] }} · {{ transmissions[target.transmission] }}</dd></div>
          <div><dt>经营方式</dt><dd>{{ saleModes[target.saleMode] }}</dd></div>
        </dl>
        <p class="vehicle-description">{{ target.description }}</p>
        <template v-if="target.status === 'PENDING'">
          <label class="vehicle-dialog-label">审核说明<textarea v-model="note" rows="3" maxlength="500" placeholder="退回时必须填写原因" /></label>
          <Message v-if="target.saleMode === 'CONSIGN'" severity="warn" :closable="false">代销车源需完成委托后才能上架。</Message>
        </template>
        <p v-else-if="target.reviewNote" class="vehicle-description">审核说明：{{ target.reviewNote }}</p>
        <Message v-if="reviewError" severity="error" :closable="false">{{ reviewError }}</Message>
      </div>
      <template #footer>
        <Button label="关闭" severity="secondary" variant="outlined" :disabled="saving" @click="showReview = false" />
        <template v-if="target?.status === 'PENDING'">
          <Button label="退回修改" severity="danger" variant="outlined" :disabled="saving" @click="review('REJECT')" />
          <Button label="通过并上架" :loading="saving" :disabled="target.saleMode === 'CONSIGN'" @click="review('APPROVE')" />
        </template>
      </template>
    </Dialog>
  </section>
</template>
