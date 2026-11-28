<!-- @author pangzhihao -->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router' // URL 保存已提交的筛选，可从首页直达并支持浏览器返回。
import Button from 'primevue/button'
import Message from 'primevue/message'
import Paginator from 'primevue/paginator'
import { vehicleApi } from '../../api/vehicles.js'
import VehicleCard from './VehicleCard.vue'
import { marketShortcuts } from './marketShortcuts.js'
import { formatPrice, saleModes } from './vehicleLabels.js'
import { useVehiclePage } from './useVehiclePage.js'

const emptyFilters = { brandId: '', cityId: '', saleMode: '', minPrice: '', maxPrice: '',
  minYear: '', maxYear: '', minMileage: '', maxMileage: '' }
const route = useRoute()
const router = useRouter()
const form = reactive({ ...emptyFilters })
const applied = ref({ ...emptyFilters })
const catalog = ref({ brands: [], cities: [] })
const catalogError = ref('')
const filtersOpen = ref(false) // 手机端按需展开筛选，桌面端保持筛选栏可见。
const page = useVehiclePage((query) => vehicleApi.search({ ...applied.value, ...query }), 12)

const filterLabels = {
  brandId: (value) => catalog.value.brands.find((item) => String(item.id) === value)?.name ?? '品牌 #' + value,
  cityId: (value) => catalog.value.cities.find((item) => String(item.id) === value)?.name ?? '城市 #' + value,
  saleMode: (value) => saleModes[value] ?? value,
  minPrice: (value) => formatPrice(value) + ' 起',
  maxPrice: (value) => formatPrice(value) + ' 以内',
  minYear: (value) => value + ' 年及以后',
  maxYear: (value) => value + ' 年及以前',
  minMileage: (value) => value + ' 公里起',
  maxMileage: (value) => value + ' 公里以内',
}
const selectedFilters = computed(() => Object.entries(applied.value)
  .filter(([, value]) => value !== '')
  .map(([key, value]) => ({ key, label: filterLabels[key](value) }))) // 仅显示实际应用到结果的筛选条件。

async function loadCatalog() {
  catalogError.value = ''
  try {
    catalog.value = await vehicleApi.catalog()
  } catch (error) {
    catalogError.value = error.message
  }
}

function filtersMatch(values) {
  return Object.keys(emptyFilters).every((key) => String(values[key] ?? '') === applied.value[key])
}

function applyFilters() {
  filtersOpen.value = false
  if (filtersMatch(form)) {
    page.load(1) // 条件相同也允许重新加载第一页。
    return
  }
  const query = queryFor(form)
  router.push({ name: 'vehicle-market', query }) // 由路由变化触发唯一一次查询。
}

function removeFilter(key) {
  // 删除已应用的单个条件时保留其余条件，未提交的输入不会被带进查询。
  router.push({ name: 'vehicle-market', query: queryFor({ ...applied.value, [key]: '' }) })
}

function queryFor(filters) {
  return Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))
}

watch(() => route.query, (query) => {
  if (route.name !== 'vehicle-market') return
  // 只读取列表定义的字段，避免其他路由参数进入请求。
  const filters = Object.fromEntries(Object.keys(emptyFilters).map((key) => [
    key, typeof query[key] === 'string' ? query[key] : '',
  ]))
  Object.assign(form, filters)
  applied.value = filters // 分页使用这次提交的条件，编辑表单时不影响已加载的结果。
  page.load(1)
}, { immediate: true })

function resetFilters() {
  Object.assign(form, emptyFilters)
  applyFilters()
}

function goToPage(index) {
  page.load(index)
  document.getElementById('market-results-title')?.scrollIntoView({ block: 'start' }) // 翻页后从新结果区开始浏览。
}

onMounted(loadCatalog)
</script>

<template>
  <main class="public-width vehicle-market market-page">
    <section class="market-hero" aria-labelledby="market-title">
      <img src="/images/showcase/city.jpg" alt="" fetchpriority="high" />
      <div>
        <h1 id="market-title">二手车市场</h1>
        <p>筛选在售车源，核对车况后再约看车。</p>
      </div>
      <RouterLink class="market-publish" :to="{ name: 'vehicle-publish' }">发布车源 <span aria-hidden="true">↗</span></RouterLink>
    </section>

    <nav class="market-shortcuts" aria-label="快捷筛选">
      <RouterLink v-for="item in marketShortcuts" :key="item.label" :to="{ name: 'vehicle-market', query: item.query }"
        custom v-slot="{ href, navigate }">
        <a :href="href" :aria-current="filtersMatch(item.query) ? 'true' : null" @click="navigate">
          {{ item.label }} <span aria-hidden="true">↗</span>
        </a>
      </RouterLink>
    </nav>

    <button class="market-filter-toggle" type="button" aria-controls="market-filter-panel" :aria-expanded="filtersOpen"
      @click="filtersOpen = !filtersOpen">
      <span>{{ filtersOpen ? '收起筛选' : '筛选车源' }}<template v-if="selectedFilters.length"> · {{ selectedFilters.length }} 项</template></span>
      <span aria-hidden="true">{{ filtersOpen ? '−' : '+' }}</span>
    </button>

    <div class="market-layout">
      <form id="market-filter-panel" class="market-filters" :class="{ 'market-filters-open': filtersOpen }"
        aria-label="车源筛选" @submit.prevent="applyFilters">
        <div class="market-filter-heading"><h2>筛选车源</h2><button class="text-button" type="button" @click="resetFilters">清空</button></div>
        <Message v-if="catalogError" class="filter-notice" severity="warn" :closable="false">
          {{ catalogError }} <button class="text-button" type="button" @click="loadCatalog">重试</button>
        </Message>
        <label>品牌<select v-model="form.brandId"><option value="">不限</option><option v-for="item in catalog.brands" :key="item.id" :value="String(item.id)">{{ item.name }}</option></select></label>
        <label>城市<select v-model="form.cityId"><option value="">不限</option><option v-for="item in catalog.cities" :key="item.id" :value="String(item.id)">{{ item.name }}</option></select></label>
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
        </div>
      </form>

      <section class="market-results" aria-labelledby="market-results-title" :aria-busy="page.loading.value">
        <div class="market-results-head">
          <h2 id="market-results-title">在售车源</h2>
          <p v-if="page.ready.value && !page.loading.value && !page.error.value" class="market-count" aria-live="polite">共 {{ page.data.value.total }} 辆</p>
        </div>
        <ul v-if="selectedFilters.length" class="market-active-filters" aria-label="已选筛选条件">
          <li v-for="filter in selectedFilters" :key="filter.key">
            <button type="button" :aria-label="'移除条件：' + filter.label" @click="removeFilter(filter.key)">
              {{ filter.label }} <span aria-hidden="true">×</span>
            </button>
          </li>
          <li><button class="market-clear-filters" type="button" @click="resetFilters">清空全部</button></li>
        </ul>
        <Message v-if="page.error.value" severity="error" :closable="false">{{ page.error.value }} <button class="text-button" type="button" @click="page.load()">重试</button></Message>
        <p v-else-if="page.loading.value || !page.ready.value" class="vehicle-empty" role="status">正在查询车源…</p>
        <div v-else-if="!page.data.value.items.length" class="vehicle-empty market-empty">
          <p>{{ selectedFilters.length ? '当前条件暂无在售车源。' : '暂无在售车源。' }}</p>
          <button v-if="selectedFilters.length" class="text-button" type="button" @click="resetFilters">查看全部车源</button>
        </div>
        <div v-else class="vehicle-grid">
          <VehicleCard v-for="(vehicle, index) in page.data.value.items" :key="vehicle.id" :vehicle="vehicle"
            :market-query="route.query"
            :style="{ '--vehicle-enter-delay': Math.min(index, 8) * 35 + 'ms' }" />
        </div>
        <Paginator v-if="!page.loading.value && !page.error.value && page.data.value.total > page.data.value.pageSize" :rows="page.data.value.pageSize"
          :first="(page.data.value.page - 1) * page.data.value.pageSize" :total-records="page.data.value.total"
          @page="goToPage($event.page + 1)" />
      </section>
    </div>
  </main>
</template>
