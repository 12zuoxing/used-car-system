<!-- @author pangzhihao -->
<script setup>
import Button from 'primevue/button' // 复用项目主题中的按钮。
import Message from 'primevue/message' // 展示分页请求失败或为空的反馈。
import Paginator from 'primevue/paginator' // 处理服务端分页的位置切换。

defineProps({
  page: { type: Object, required: true }, // 接收现有分页组合函数提供的状态和加载方法。
  columns: { type: Array, required: true }, // 由调用页面定义数据列的标题、字段和格式。
  statuses: { type: Object, default: null }, // 可选的状态名称列表。
  status: { type: String, default: '' }, // 由调用页面保存当前过滤状态。
  empty: { type: String, required: true }, // 每个列表提供具体的空结果提示。
})
defineEmits(['update:status']) // 变更筛选时由调用页面重新加载第一页。
</script>

<template>
  <!-- 列表容器限制窄屏时横向滚动的范围。 -->
  <div class="booking-table-section">
    <!-- 显示可选的状态筛选和统一刷新入口。 -->
    <div class="vehicle-toolbar">
      <label v-if="statuses">
        状态
        <!-- 选择不同状态时由父页面更新查询并回到第一页。 -->
        <select :value="status" @change="$emit('update:status', $event.target.value)">
          <option value="">全部</option>
          <option v-for="(entry, key) in statuses" :key="key" :value="key">{{ entry.label }}</option>
        </select>
      </label>
      <Button label="刷新" size="small" severity="secondary" variant="outlined" :loading="page.loading.value" @click="page.load(page.data.value.page)" />
    </div>
    <!-- 服务错误保留在列表附近。 -->
    <Message v-if="page.error.value" severity="error" :closable="false">{{ page.error.value }}</Message>
    <p v-if="page.loading.value && !page.ready.value" class="vehicle-empty" role="status">正在读取…</p>
    <p v-else-if="page.ready.value && !page.data.value.items.length" class="vehicle-empty">{{ empty }}</p>
    <!-- 列表含数据时展示各个业务字段和调用方提供的操作按钮。 -->
    <div v-else-if="page.data.value.items.length" class="booking-table-scroll" :aria-busy="page.loading.value">
      <table class="booking-table">
        <thead>
          <tr>
            <th v-for="column in columns" :key="column.key" scope="col">{{ column.label }}</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in page.data.value.items" :key="entry.id">
            <!-- 允许调用页面把普通文本替换为状态标签等控件。 -->
            <td v-for="column in columns" :key="column.key">
              <slot :name="column.key" :entry="entry">{{ column.format ? column.format(entry) : entry[column.key] }}</slot>
            </td>
            <td><slot name="actions" :entry="entry" /></td>
          </tr>
        </tbody>
      </table>
    </div>
    <!-- 分页器显示服务端记录总量，转换成 API 的从一开始的页码。 -->
    <Paginator v-if="page.data.value.total > page.data.value.pageSize"
      :rows="page.data.value.pageSize" :total-records="page.data.value.total"
      :first="(page.data.value.page - 1) * page.data.value.pageSize"
      template="PrevPageLink PageLinks NextPageLink" @page="page.load($event.page + 1)" />
  </div>
</template>
