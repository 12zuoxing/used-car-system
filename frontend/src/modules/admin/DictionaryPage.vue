<!-- @author pangzhihao -->
<script setup>
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { dictionaryApi } from '../../api/vehicles.js'

const kind = ref('BRAND')
const catalog = ref({ brands: [], cities: [] })
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const error = ref('')
const dialogError = ref('')
const editing = ref(null)
const form = ref({ name: '', active: true })
const items = computed(() => kind.value === 'BRAND' ? catalog.value.brands : catalog.value.cities)
const label = computed(() => kind.value === 'BRAND' ? '品牌' : '城市')

async function load() {
  loading.value = true
  error.value = ''
  try {
    catalog.value = await dictionaryApi.all()
  } catch (failure) {
    error.value = failure.message
  } finally {
    loading.value = false
  }
}

function edit(item = null) {
  editing.value = item
  form.value = { name: item?.name ?? '', active: item?.active ?? true }
  dialogError.value = ''
  visible.value = true
}

async function save() {
  if (saving.value) return
  if (!form.value.name.trim()) {
    dialogError.value = '请填写字典名称。'
    return
  }
  saving.value = true
  dialogError.value = ''
  try {
    await dictionaryApi.save(kind.value, editing.value?.id, { ...form.value, name: form.value.name.trim() })
    visible.value = false
    await load()
  } catch (failure) {
    dialogError.value = failure.message
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section aria-labelledby="dictionary-title">
    <header class="page-heading"><h1 id="dictionary-title">基础字典</h1><p>维护发布车源时可选择的品牌和城市。</p></header>
    <div class="vehicle-section-head">
      <div class="dictionary-tabs" aria-label="字典类型">
        <Button label="品牌" :variant="kind === 'BRAND' ? undefined : 'outlined'" :aria-pressed="kind === 'BRAND'" @click="kind = 'BRAND'" />
        <Button label="城市" :variant="kind === 'CITY' ? undefined : 'outlined'" :aria-pressed="kind === 'CITY'" @click="kind = 'CITY'" />
      </div>
      <Button :label="'新增' + label" :disabled="loading" @click="edit()" />
    </div>
    <Message v-if="error" severity="error" :closable="false">{{ error }} <button class="text-button" type="button" @click="load">重试</button></Message>
    <DataTable :value="items" :loading="loading" :rows="10" :paginator="items.length > 10" data-key="id" striped-rows>
      <template #empty>暂无{{ label }}。可新增后用于车源发布。</template>
      <Column field="name" :header="label + '名称'" />
      <Column header="状态"><template #body="{ data }"><Tag :value="data.active ? '启用' : '停用'" :severity="data.active ? 'success' : 'secondary'" /></template></Column>
      <Column header="操作"><template #body="{ data }"><Button label="编辑" size="small" variant="text" @click="edit(data)" /></template></Column>
    </DataTable>
    <Dialog v-model:visible="visible" :header="(editing ? '编辑' : '新增') + label" modal :closable="!saving"
      :close-on-escape="!saving" :style="{ width: 'min(420px, 92vw)' }">
      <form id="dictionary-form" class="vehicle-dialog-form" @submit.prevent="save">
        <label>{{ label }}名称<input v-model.trim="form.name" maxlength="40" required /></label>
        <label>状态<select v-model="form.active"><option :value="true">启用</option><option :value="false">停用</option></select></label>
        <p v-if="!form.active" class="muted-text">停用项将从发布选项移除，历史车源仍可引用。</p>
        <Message v-if="dialogError" severity="error" :closable="false">{{ dialogError }}</Message>
      </form>
      <template #footer>
        <Button label="取消" severity="secondary" variant="outlined" :disabled="saving" @click="visible = false" />
        <Button type="submit" form="dictionary-form" label="保存" :loading="saving" />
      </template>
    </Dialog>
  </section>
</template>
