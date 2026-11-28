<!-- @author pangzhihao -->
<script setup>
import { computed, onMounted, ref } from 'vue' // 管理列表条件并在进入页面时读取工单。
import { useRoute } from 'vue-router' // 支持从后台统计进入指定状态队列。
import Tag from 'primevue/tag' // 呈现受理状态。
import { repairApi } from '../../api/repairs.js' // 按访问者视角使用个人或后台接口。
import BookingTable from '../bookings/BookingTable.vue' // 复用筛选、分页与窄屏滚动。
import { timeLabel } from '../bookings/bookingLabels.js' // 统一显示北京时间。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 跟踪分页加载和已失效请求。
import { repairActions, repairStages, repairStatuses } from './repairLabels.js' // 共享工单字典和处理动作。

const props = defineProps({ managed: { type: Boolean, default: false } }) // 后台视角需要查看申请人和经办入口。
const stage = ref('') // 默认同时展示售前和售后记录。
const initial = useRoute().query.status // 检查是否从工作台跳转。
const queueStatus = typeof initial === 'string' && Object.hasOwn(repairStatuses, initial) ? initial : 'PENDING' // 后台没有有效筛选时查看待受理。
const status = ref(props.managed ? queueStatus : '') // 个人默认看全部申请。
const page = useVehiclePage((query) => {
  const filters = { ...query, stage: stage.value, status: status.value } // 将类型和状态与分页参数一起提交。
  return props.managed ? repairApi.queue(filters) : repairApi.mine(filters) // 请求范围由对应接口验证。
}, 10)
const base = [
  { key: 'vehicleModel', label: '车辆 / 工单' }, // 展示车名和业务编号。
  { key: 'stage', label: '业务类型', format: (row) => repairStages[row.stage] }, // 将枚举转为中文类型。
  { key: 'visitAt', label: '预计到店', format: (row) => timeLabel(row.visitAt) }, // 展示计划到店时刻。
  { key: 'status', label: '状态' }, // 由下方插槽渲染状态标签。
]
const columns = computed(() => props.managed
  ? [base[0], { key: 'applicantName', label: '申请人' }, ...base.slice(1)] // 后台额外显示工单的发起人。
  : base) // 个人只展示自己的工单，无需重复显示姓名。

function updateStatus(value) {
  status.value = value // 保存当前状态条件。
  void page.load(1) // 切换筛选后从第一页读取数据。
}

onMounted(() => page.load()) // 首次进入工单页请服务器返回初始列表。
</script>

<template>
  <!-- 工单队列与个人历史共用相同的列布局。 -->
  <BookingTable :page="page" :columns="columns" :statuses="repairStatuses" :status="status" empty="暂无符合条件的工单。"
    @update:status="updateStatus">
    <!-- 阶段与状态可组合筛选。 -->
    <template #filters>
      <label>业务类型
        <select v-model="stage" @change="page.load(1)">
          <option value="">全部类型</option>
          <option v-for="(label, key) in repairStages" :key="key" :value="key">{{ label }}</option>
        </select>
      </label>
    </template>
    <template #vehicleModel="{ entry }">
      <strong>{{ entry.vehicleModel }}</strong>
      <small class="table-vehicle-meta">工单 #{{ entry.id }}</small>
    </template>
    <template #status="{ entry }"><Tag :value="repairStatuses[entry.status].label" :severity="repairStatuses[entry.status].severity" /></template>
    <!-- 工作人员可办理开放工单，终态只提供查看。 -->
    <template #actions="{ entry }">
      <RouterLink :to="{ name: managed ? 'admin-repair-detail' : 'my-repair-detail', params: { id: entry.id } }">
        {{ managed && repairActions[entry.status] ? '办理' : '查看' }}
      </RouterLink>
    </template>
  </BookingTable>
</template>
