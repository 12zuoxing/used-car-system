<!-- @author pangzhihao -->
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { vehicleApi } from '../../api/vehicles.js'
import { canEdit, fuels, saleModes, transmissions } from './vehicleLabels.js'

const route = useRoute()
const router = useRouter()
const editing = computed(() => route.name === 'vehicle-edit')
const id = computed(() => route.params.id)
const yearNow = new Date().getFullYear()
const defaults = { brandId: '', cityId: '', model: '', registrationYear: yearNow,
  mileage: '', price: '', transmission: 'AUTO', fuel: 'PETROL', saleMode: 'DIRECT', description: '' }
const form = reactive({ ...defaults })
const catalog = ref({ brands: [], cities: [] })
const ready = ref(false)
const preparing = ref(false)
const saving = ref(false)
const error = ref('')
const changedDictionary = ref(false)
let loadVersion = 0

function applyCatalog(options) {
  catalog.value = options
  // 字典在填写期间也可能被管理员停用。刷新时只清理失效选项，保留车辆资料。
  for (const [key, list] of [['brandId', options.brands], ['cityId', options.cities]]) {
    if (form[key] !== '' && !list.some((entry) => entry.id === form[key])) {
      form[key] = ''
      changedDictionary.value = true
    }
  }
}

async function prepare() {
  const version = ++loadVersion
  ready.value = false
  preparing.value = true
  error.value = ''
  changedDictionary.value = false
  Object.assign(form, defaults)
  try {
    const [options, previous] = await Promise.all([
      vehicleApi.catalog(), editing.value ? vehicleApi.myDetail(id.value) : Promise.resolve(null),
    ])
    if (version !== loadVersion) return
    if (previous && !canEdit(previous)) {
      error.value = '该车辆当前无法修改。'
      return
    }
    if (previous) {
      // 映射可写字段，避免把状态、审核备注等只读字段提交到后端。
      for (const field of Object.keys(defaults)) form[field] = previous[field]
    }
    applyCatalog(options)
    ready.value = true
  } catch (failure) {
    if (version === loadVersion) error.value = failure.message
  } finally {
    if (version === loadVersion) preparing.value = false
  }
}

async function submit() {
  if (!ready.value || saving.value) return
  const version = loadVersion
  const editingId = editing.value ? id.value : null
  saving.value = true
  error.value = ''
  try {
    const payload = { ...form, model: form.model.trim(), description: form.description.trim() }
    const vehicle = await (editingId ? vehicleApi.resubmit(editingId, payload) : vehicleApi.publish(payload))
    if (version === loadVersion) await router.replace({ name: 'my-vehicle-detail', params: { id: vehicle.id } })
  } catch (failure) {
    if (version === loadVersion) {
      error.value = failure.message
      if (failure.code === 'DICTIONARY_INACTIVE') {
        try {
          const options = await vehicleApi.catalog()
          if (version === loadVersion) applyCatalog(options)
        } catch {
          if (version === loadVersion) error.value += ' 暂时无法刷新选项，请重试。'
        }
      }
    }
  } finally {
    saving.value = false
  }
}

watch(() => route.fullPath, prepare, { immediate: true })
</script>

<template>
  <main class="public-width vehicle-page">
    <RouterLink class="vehicle-back" :to="{ name: 'my-vehicles' }">← 我的车源</RouterLink>
    <section class="vehicle-panel publish-panel">
      <header class="page-heading"><h1>{{ editing ? '修改车源' : '发布车源' }}</h1><p>资料提交后进入工作人员审核队列。</p></header>
      <p v-if="preparing" role="status">正在读取车辆资料…</p>
      <Message v-if="error" severity="error" :closable="false">
        {{ error }} <button v-if="!ready" class="text-button" type="button" @click="prepare">重试</button>
      </Message>
      <Message v-if="changedDictionary" severity="warn" :closable="false">原品牌或城市已停用，请重新选择。</Message>
      <form v-if="ready" class="vehicle-form" @submit.prevent="submit">
        <Message v-if="!catalog.brands.length || !catalog.cities.length" class="vehicle-form-wide" severity="warn" :closable="false">
          暂无可用品牌或城市，请联系管理员维护基础字典后再发布。
        </Message>
        <label>品牌<select v-model="form.brandId" required><option value="" disabled>请选择</option><option v-for="item in catalog.brands" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
        <label>车型<input v-model.trim="form.model" placeholder="如 卡罗拉 1.8L" maxlength="80" required /></label>
        <label>城市<select v-model="form.cityId" required><option value="" disabled>请选择</option><option v-for="item in catalog.cities" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
        <label>登记年份<input v-model.number="form.registrationYear" type="number" min="1980" :max="yearNow" required /></label>
        <label>里程（公里）<input v-model.number="form.mileage" type="number" min="0" max="2147483647" step="1" required /></label>
        <label>售价（元）<input v-model.number="form.price" type="number" min="0.01" max="9999999999.99" step="0.01" required /></label>
        <label>变速箱<select v-model="form.transmission"><option v-for="(text, key) in transmissions" :key="key" :value="key">{{ text }}</option></select></label>
        <label>能源类别<select v-model="form.fuel"><option v-for="(text, key) in fuels" :key="key" :value="key">{{ text }}</option></select></label>
        <label>经营方式<select v-model="form.saleMode"><option v-for="(text, key) in saleModes" :key="key" :value="key">{{ text }}</option></select></label>
        <p v-if="form.saleMode === 'CONSIGN'" class="vehicle-form-wide muted-text">代销车源需完成委托流程才能上架。</p>
        <label class="vehicle-form-wide">车辆描述<textarea v-model.trim="form.description" rows="5" maxlength="1000" required /></label>
        <div class="vehicle-form-wide vehicle-actions">
          <Button type="submit" :label="editing ? '重新提交审核' : '提交审核'" :loading="saving"
            :disabled="!catalog.brands.length || !catalog.cities.length" />
          <RouterLink :to="{ name: 'my-vehicles' }">取消</RouterLink>
        </div>
      </form>
    </section>
  </main>
</template>
