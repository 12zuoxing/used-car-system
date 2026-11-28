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
import { canRequestPreSale } from '../repairs/repairLabels.js' // 根据本人车源状态显示售前整备入口。
import { canEdit, formatPrice, saleModes, statuses, vehicleTitle } from './vehicleLabels.js'
import { useVehiclePage } from './useVehiclePage.js'

const status = ref('')
const target = ref(null)
const confirming = ref(false)
const saving = ref(false)
const notice = ref('')
const actionError = ref('')
const page = useVehiclePage((query) => vehicleApi.mine({ ...query, status: status.value }), 10)

function askOffline(vehicle) {
  target.value = vehicle
  actionError.value = ''
  confirming.value = true
}

async function offline() {
  if (saving.value) return
  saving.value = true
  actionError.value = ''
  try {
    await vehicleApi.offline(target.value.id)
    confirming.value = false
    notice.value = '已下架。编辑资料并重新提交后可再次审核上架。'
    // 下架可能让当前筛选结果减少，从第一页重新查询以避免停在空页。
    await page.load(1)
  } catch (failure) {
    actionError.value = failure.message
  } finally {
    saving.value = false
  }
}

onMounted(() => page.load())
</script>

<template>
  <main class="public-width vehicle-page">
    <section class="vehicle-panel">
      <div class="vehicle-section-head">
        <header class="page-heading"><h1>我的车源</h1><p>查看审核结果、维护已发布的车辆。</p></header>
        <RouterLink class="solid-link" :to="{ name: 'vehicle-publish' }">发布车源</RouterLink>
      </div>
      <div class="vehicle-toolbar">
        <label>车源状态<select v-model="status" @change="page.load(1)"><option value="">全部</option><option v-for="(item, key) in statuses" :key="key" :value="key">{{ item.label }}</option></select></label>
      </div>
      <Message v-if="notice" severity="success" :closable="false">{{ notice }}</Message>
      <Message v-if="page.error.value" severity="error" :closable="false">{{ page.error.value }} <button class="text-button" type="button" @click="page.load()">重试</button></Message>
      <DataTable :value="page.data.value.items" data-key="id" :loading="page.loading.value"
        :rows="page.data.value.pageSize" :first="(page.data.value.page - 1) * page.data.value.pageSize"
        :total-records="page.data.value.total" :table-style="{ minWidth: '650px' }" lazy paginator scrollable
        paginator-template="PrevPageLink PageLinks NextPageLink" @page="page.load($event.page + 1)">
        <template #empty>暂无车源。</template>
        <Column header="车辆">
          <template #body="{ data }"><RouterLink class="table-vehicle-title" :to="{ name: 'my-vehicle-detail', params: { id: data.id } }">{{ vehicleTitle(data) }}</RouterLink><small class="table-vehicle-meta">{{ data.cityName }} · {{ data.registrationYear }} 年</small></template>
        </Column>
        <Column header="经营方式"><template #body="{ data }">{{ saleModes[data.saleMode] }}</template></Column>
        <Column header="报价"><template #body="{ data }">{{ formatPrice(data.price) }}</template></Column>
        <Column header="状态">
          <template #body="{ data }"><Tag :value="statuses[data.status].label" :severity="statuses[data.status].severity" /></template>
        </Column>
        <Column header="操作">
          <template #body="{ data }">
            <div class="table-actions">
              <RouterLink :to="{ name: 'my-vehicle-detail', params: { id: data.id } }">详情</RouterLink>
              <!-- 创建记录时服务端会再次验证车源归属和实时状态。 -->
              <RouterLink v-if="canRequestPreSale(data)" :to="{ name: 'pre-sale-repair', params: { id: data.id } }">售前整备</RouterLink>
              <RouterLink v-if="canEdit(data)" :to="{ name: 'vehicle-edit', params: { id: data.id } }">修改并重提</RouterLink>
              <Button v-if="data.status === 'LISTED'" label="下架" size="small" severity="danger" variant="text" @click="askOffline(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </section>

    <Dialog v-model:visible="confirming" header="下架车源" modal :closable="!saving" :close-on-escape="!saving" :style="{ width: 'min(420px, 92vw)' }">
      <p v-if="target">确定下架 {{ vehicleTitle(target) }}？下架后买家将无法在市场中查看该车。</p>
      <Message v-if="actionError" severity="error" :closable="false">{{ actionError }}</Message>
      <template #footer>
        <Button label="取消" severity="secondary" variant="outlined" :disabled="saving" @click="confirming = false" />
        <Button label="确认下架" severity="danger" :loading="saving" @click="offline" />
      </template>
    </Dialog>
  </main>
</template>
