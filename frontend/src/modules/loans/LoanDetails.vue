<!-- @author pangzhihao -->
<script setup>
import Tag from 'primevue/tag' // 使用项目的状态标签显示进度。
import { timeLabel } from '../bookings/bookingLabels.js' // 将存储的北京时间显示为用户可读格式。
import { formatPrice } from '../vehicles/vehicleLabels.js' // 把订单与申请金额格式化为人民币。
import { loanStatuses } from './loanLabels.js' // 统一使用各页面相同的办理说明。

defineProps({
  loan: { type: Object, required: true }, // 接收后端已经按访问者授权的贷款详情。
  managed: { type: Boolean, default: false }, // 工作人员视图额外显示申请人名称。
})
</script>

<template>
  <!-- 展示申请核心信息，供申请人与工作人员核对同一份记录。 -->
  <div class="booking-section">
    <div class="vehicle-section-head">
      <h2>申请 #{{ loan.id }}</h2>
      <Tag :value="loanStatuses[loan.status].label" :severity="loanStatuses[loan.status].severity" />
    </div>
    <dl class="vehicle-facts">
      <div><dt>关联订单</dt><dd>#{{ loan.orderId }} · {{ loan.vehicleModel }}</dd></div>
      <div v-if="managed"><dt>申请人</dt><dd>{{ loan.applicantName }}</dd></div>
      <div><dt>订单金额</dt><dd>{{ formatPrice(loan.orderAmount) }}</dd></div>
      <div><dt>申请金额</dt><dd>{{ formatPrice(loan.amount) }}</dd></div>
      <div><dt>贷款期限</dt><dd>{{ loan.months }} 期</dd></div>
      <div><dt>提交时间</dt><dd>{{ timeLabel(loan.createdAt) }}</dd></div>
      <div v-if="loan.handlerName"><dt>最近经办人</dt><dd>{{ loan.handlerName }}</dd></div>
    </dl>
    <!-- 申请人备注和处理结果仅在授权后的详情内展示。 -->
    <p v-if="loan.note" class="vehicle-description">申请说明 {{ loan.note }}</p>
    <p v-if="loan.resultNote" class="vehicle-description">办理说明 {{ loan.resultNote }}</p>
  </div>
</template>
