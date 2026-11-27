<!-- @author pangzhihao -->
<script setup>
import { onMounted, reactive, ref } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Paginator from 'primevue/paginator'
import { vehicleApi } from '../../api/vehicles.js'
import VehicleCard from './VehicleCard.vue'
import { saleModes } from './vehicleLabels.js'
import { useVehiclePage } from './useVehiclePage.js'

const emptyFilters = { brandId: '', cityId: '', saleMode: '', minPrice: '', maxPrice: '',
  minYear: '', maxYear: '', minMileage: '', maxMileage: '' }
const form = reactive({ ...emptyFilters })
const applied = ref({ ...emptyFilters })
const catalog = ref({ brands: [], cities: [] })
const catalogError = ref('')
const page = useVehiclePage((query) => vehicleApi.search({ ...applied.value, ...query }), 12)

async function loadCatalog() {
  catalogError.value = ''
  try {
    catalog.value = await vehicleApi.catalog()
  } catch (error) {
    catalogError.value = error.message
  }
}

function applyFilters() {
  // 分页始终沿用上一次提交的条件，编辑中的表单不会改变当前结果。
  applied.value = { ...form }
  page.load(1)
}

function resetFilters() {
  Object.assign(form, emptyFilters)
  applyFilters()
}

onMounted(() => {
  loadCatalog()
  page.load()
})
</script>

<template>
  <main class="public-width vehicle-market">
    <section class="market-hero" aria-labelledby="market-title">
      <img src="/images/showroom.jpg" alt="" />
      <div>
        <h1 id="market-title">二手车市场</h1>
        <p>按品牌、城市和预算筛选在售车源</p>
      </div>
    </section>

    <form class="market-filters" aria-label="车源筛选" @submit.prevent="applyFilters">
      <Message v-if="catalogError" class="filter-notice" severity="warn" :closable="false">
        {{ catalogError }} <button class="text-button" type="button" @click="loadCatalog">重试</button>
      </Message>
      <label>品牌<select v-model="form.brandId"><option value="">不限</option><option v-for="item in catalog.brands" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
      <label>城市<select v-model="form.cityId"><option value="">不限</option><option v-for="item in catalog.cities" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
      <label>交易方式<select v-model="form.saleMode"><option value="">不限</option><option v-for="(text, key) in saleModes" :key="key" :value="key">{{ text }}</option></select></label>
      <fieldset class="market-range">
        <legend>售价（元）</legend>
        <div><input v-model="form.minPrice" type="number" min="0" step="0.01" aria-label="最低售价" placeholder="最低" /><span>至</span><input v-model="form.maxPrice" type="number" min="0" step="0.01" aria-label="最高售价" placeholder="最高" /></div>
      </fieldset>
      <fieldset class="market-range">
        <legend>登记年份</legend>
        <div><input v-model="form.minYear" type="number" min="1980" aria-label="最早登记年份" placeholder="最早" /><span>至</span><input v-model="form.maxYear" type="number" min="1980" aria-label="最晚登记年份" placeholder="最晚" /></div>
      </fieldset>
      <fieldset class="market-range">
        <legend>里程（公里）</legend>
        <div><input v-model="form.minMileage" type="number" min="0" aria-label="最低里程" placeholder="最低" /><span>至</span><input v-model="form.maxMileage" type="number" min="0" aria-label="最高里程" placeholder="最高" /></div>
      </fieldset>
      <div class="vehicle-actions">
        <Button type="submit" label="筛选车源" :loading="page.loading.value" />
        <Button type="button" label="重置" severity="secondary" variant="outlined" @click="resetFilters" />
      </div>
    </form>

    <section class="market-results" aria-labelledby="market-results-title" :aria-busy="page.loading.value">
      <div class="vehicle-section-head">
        <div><h2 id="market-results-title">在售车源</h2><p v-if="page.ready.value" aria-live="polite">共 {{ page.data.value.total }} 辆</p></div>
        <RouterLink class="solid-link" :to="{ name: 'vehicle-publish' }">发布车源</RouterLink>
      </div>
      <Message v-if="page.error.value" severity="error" :closable="false">{{ page.error.value }} <button class="text-button" type="button" @click="page.load()">重试</button></Message>
      <p v-else-if="!page.ready.value" class="vehicle-empty" role="status">正在查询车源…</p>
      <p v-else-if="!page.data.value.items.length" class="vehicle-empty">当前条件暂无在售车源。</p>
      <div v-else class="vehicle-grid">
        <VehicleCard v-for="vehicle in page.data.value.items" :key="vehicle.id" :vehicle="vehicle" />
      </div>
      <Paginator v-if="page.data.value.total > page.data.value.pageSize" :rows="page.data.value.pageSize"
        :first="(page.data.value.page - 1) * page.data.value.pageSize" :total-records="page.data.value.total"
        @page="page.load($event.page + 1)" />
    </section>
  </main>
</template>
