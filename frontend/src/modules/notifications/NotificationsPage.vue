<!-- @author pangzhihao -->
<script setup>
import { onMounted, reactive, ref, watch } from 'vue' // 在实时刷新时保留筛选和每行操作状态。
import Button from 'primevue/button' // 显示刷新和标为已读的动作。
import Message from 'primevue/message' // 反馈失败的已读操作和离线提醒。
import Paginator from 'primevue/paginator' // 切换服务端通知分页。
import Tag from 'primevue/tag' // 区分已经查看和仍未读的通知。
import { notificationApi } from '../../api/operations.js' // 使用授权收件箱和标记接口。
import { inbox } from './inbox.js' // 接受实时信号和更新全站未读数字。
import { timeLabel } from '../bookings/bookingLabels.js' // 与其他页面共用时间格式。
import { useVehiclePage } from '../vehicles/useVehiclePage.js' // 防止旧分页响应覆盖新记录。

const unreadOnly = ref(false) // 默认保留历史和未读两种状态。
const actionError = ref('') // 展示标记已读失败的原因。
const updating = reactive(new Set()) // 标记任意行时不会阻塞其他通知。
const page = useVehiclePage((query) => notificationApi.list({ ...query, unreadOnly: unreadOnly.value }), 10) // 服务端按会话和筛选分页。

function target(entry) {
  switch (entry.subjectType) {
    case 'ORDER': return { name: 'my-order-detail', params: { id: entry.subjectId } } // 打开买卖双方的订单详情。
    case 'VEHICLE': return { name: 'my-vehicle-detail', params: { id: entry.subjectId } } // 打开本人车源审核记录。
    case 'REPORT': return entry.vehicleId ? { name: 'my-vehicle-detail', params: { id: entry.vehicleId } } : null // 查看车源下的报告状态。
    case 'CONSIGNMENT': return entry.vehicleId ? { name: 'my-consignments', params: { id: entry.vehicleId } } : null // 查看相关车源的委托。
    case 'APPOINTMENT': return { name: 'my-appointments' } // 进入本人的看车预约列表。
    case 'LOAN': return { name: 'my-loans' } // 进入个人贷款办理进度。
    case 'REPAIR': return { name: 'my-repair-detail', params: { id: entry.subjectId } } // 打开该申请的工单详情。
    default: return null // 其他系统消息可能没有单独业务页面。
  }
}

async function markRead(id) {
  if (updating.has(id)) return // 同一行操作完成前避免重复写请求。
  updating.add(id) // 禁止这一行被重复提交。
  actionError.value = '' // 清除上一次操作反馈。
  try {
    await notificationApi.markRead(id) // 由服务端记录首次已读时刻。
    await inbox.refresh() // 同步通知页面与主导航未读数。
  } catch (failure) {
    actionError.value = failure.message // 展示服务端拒绝或网络错误。
  } finally {
    updating.delete(id) // 完成后清理本行提交标记。
  }
}

watch(() => inbox.state.revision, () => page.load(1)) // 收到实时、恢复连接或手动刷新提示时先显示最新消息。
onMounted(() => page.load()) // 直接访问通知地址时从数据库读取历史。
</script>

<template>
  <!-- 登录用户自己的通知收件箱。 -->
  <main class="public-width vehicle-page booking-page">
    <header class="page-heading">
      <h1>站内通知</h1>
      <p>未读 {{ inbox.state.unread }} 条</p>
    </header>
    <section class="vehicle-panel booking-section" aria-label="通知列表">
      <div class="vehicle-toolbar">
        <!-- 状态筛选与刷新都保留分页和账号权限。 -->
        <label class="notification-filter"><input v-model="unreadOnly" type="checkbox" @change="page.load(1)" /> 仅显示未读</label>
        <Button label="刷新通知" size="small" severity="secondary" variant="outlined" :loading="page.loading.value" @click="inbox.refresh()" />
      </div>
      <Message v-if="!inbox.state.connected && !page.error.value" severity="info" :closable="false">正在恢复实时连接，仍可手动刷新通知。</Message>
      <Message v-if="actionError || page.error.value" severity="error" :closable="false">{{ actionError || page.error.value }}</Message>
      <p v-if="page.loading.value && !page.ready.value" class="vehicle-empty" role="status">正在读取通知…</p>
      <p v-else-if="page.ready.value && !page.data.value.items.length" class="vehicle-empty">{{ unreadOnly ? '暂无未读通知。' : '暂无通知。' }}</p>
      <!-- 正文来自本人数据库记录，页面用文本插值展示。 -->
      <ul v-if="page.data.value.items.length" class="notification-items" :aria-busy="page.loading.value">
        <li v-for="entry in page.data.value.items" :key="entry.id">
          <div class="notification-copy">
            <div class="vehicle-actions">
              <Tag :value="entry.readAt ? '已读' : '未读'" :severity="entry.readAt ? 'secondary' : 'info'" />
              <time :datetime="entry.createdAt">{{ timeLabel(entry.createdAt) }}</time>
            </div>
            <p>{{ entry.message }}</p>
          </div>
          <div class="vehicle-actions">
            <RouterLink v-if="target(entry)" :to="target(entry)">查看关联记录</RouterLink>
            <Button v-if="!entry.readAt" label="标为已读" size="small" severity="secondary" variant="outlined"
              :loading="updating.has(entry.id)" @click="markRead(entry.id)" />
          </div>
        </li>
      </ul>
      <Paginator v-if="page.data.value.total > page.data.value.pageSize"
        :rows="page.data.value.pageSize" :total-records="page.data.value.total" :first="(page.data.value.page - 1) * page.data.value.pageSize"
        template="PrevPageLink PageLinks NextPageLink" @page="page.load($event.page + 1)" />
    </section>
  </main>
</template>
