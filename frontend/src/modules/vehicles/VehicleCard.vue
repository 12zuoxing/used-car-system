<!-- @author pangzhihao -->
<script setup>
import Tag from 'primevue/tag'
import { formatMileage, formatPrice, fuels, saleModes, vehicleTitle } from './vehicleLabels.js'

defineProps({ vehicle: { type: Object, required: true } })
</script>

<template>
  <article class="vehicle-card">
    <RouterLink :to="{ name: 'vehicle-detail', params: { id: vehicle.id } }" class="vehicle-card-link">
      <div class="vehicle-card-top">
        <span class="vehicle-brand">{{ vehicle.brandName }}</span>
        <Tag :value="saleModes[vehicle.saleMode]" severity="info" />
      </div>
      <h3>{{ vehicleTitle(vehicle) }}</h3>
      <p class="vehicle-card-meta">{{ vehicle.registrationYear }} 年 · {{ formatMileage(vehicle.mileage) }} · {{ vehicle.cityName }}</p>
      <div class="vehicle-card-bottom">
        <strong>{{ formatPrice(vehicle.price) }}</strong>
        <span>{{ fuels[vehicle.fuel] }} <span aria-hidden="true">›</span></span>
      </div>
    </RouterLink>
  </article>
</template>
