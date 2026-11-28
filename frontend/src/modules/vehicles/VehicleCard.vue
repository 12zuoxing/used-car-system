<!-- @author pangzhihao -->
<script setup>
import Tag from 'primevue/tag'
import { attachmentUrl } from '../../api/media.js'
import { formatMileage, formatPrice, fuels, saleModes, vehicleTitle } from './vehicleLabels.js'

defineProps({
  vehicle: { type: Object, required: true },
  marketQuery: { type: Object, default: undefined }, // 从市场进详情时带上已选条件，方便返回。
})
</script>

<template>
  <article class="vehicle-card">
    <RouterLink :to="{ name: 'vehicle-detail', params: { id: vehicle.id }, query: marketQuery }" class="vehicle-card-link">
      <div v-if="vehicle.coverPhotoId" class="vehicle-card-media">
        <img class="vehicle-card-photo" :src="attachmentUrl(vehicle.coverPhotoId)"
          :alt="vehicleTitle(vehicle)" loading="lazy" />
      </div>
      <div v-else class="vehicle-card-media vehicle-card-no-photo">暂无车源照片</div>
      <div class="vehicle-card-top">
        <span class="vehicle-brand">{{ vehicle.cityName }}</span>
        <Tag :value="saleModes[vehicle.saleMode]" severity="info" />
      </div>
      <h3>{{ vehicleTitle(vehicle) }}</h3>
      <p class="vehicle-card-meta">{{ vehicle.registrationYear }} 年 · {{ formatMileage(vehicle.mileage) }} · {{ fuels[vehicle.fuel] }}</p>
      <div class="vehicle-card-bottom">
        <strong>{{ formatPrice(vehicle.price) }}</strong>
        <span class="vehicle-card-arrow" aria-hidden="true">↗</span>
      </div>
    </RouterLink>
  </article>
</template>
