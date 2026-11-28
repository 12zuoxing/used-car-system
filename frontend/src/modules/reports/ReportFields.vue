<!-- @author pangzhihao -->
<script setup>
import { acceptedReports } from '../../api/media.js'

const fields = defineModel({ type: Object, required: true })
defineProps({ disabled: { type: Boolean, default: false } })
const emit = defineEmits(['fileSelected'])
</script>

<template>
  <fieldset class="vehicle-form vehicle-form-wide report-fields" :disabled="disabled">
    <legend>报告资料</legend>
    <label>报告来源<input v-model="fields.source" placeholder="查博士或其他检测机构" maxlength="100" required /></label>
    <label>报告日期<input v-model="fields.reportDate" type="date" required /></label>
    <label class="vehicle-form-wide">报告摘要<textarea v-model="fields.summary" rows="4" maxlength="1200" required /></label>
    <label class="vehicle-form-wide">报告附件（可选）
      <input type="file" :accept="acceptedReports" @change="emit('fileSelected', $event.target.files[0] ?? null)" />
      <small class="muted-text">支持 PDF、JPEG、PNG 和 WebP，默认单文件上限为 5 MB。</small>
    </label>
  </fieldset>
</template>
