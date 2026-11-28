<!-- @author pangzhihao -->
<script setup>
import { onMounted } from 'vue'
import { vehicleApi } from '../../api/vehicles.js'
import HomeShowcase from './HomeShowcase.vue' // 首页首屏展示轮播和独立短片播放器。
import VehicleCard from '../vehicles/VehicleCard.vue'
import { marketShortcuts } from '../vehicles/marketShortcuts.js' // 各入口使用市场真实支持的查询参数。
import { useVehiclePage } from '../vehicles/useVehiclePage.js'

const latestVehicles = useVehiclePage(vehicleApi.search, 3) // 按市场默认的最新上架顺序读取在售车源。
onMounted(() => latestVehicles.load())
</script>

<template>
  <main class="public-width home-content">
    <HomeShowcase />

    <section class="home-discovery" aria-labelledby="home-discovery-title">
      <div class="home-discovery-heading">
        <h2 id="home-discovery-title">按需求找车</h2>
        <RouterLink :to="{ name: 'vehicle-market' }">查看全部车源 <span aria-hidden="true">↗</span></RouterLink>
      </div>
      <div class="home-discovery-grid">
        <RouterLink v-for="item in marketShortcuts" :key="item.label" class="home-discovery-card"
          :to="{ name: 'vehicle-market', query: item.query }">
          <img :src="item.photo" alt="" loading="lazy" width="768" height="512" />
          <strong class="home-discovery-label">{{ item.label }}</strong>
          <span aria-hidden="true" class="home-discovery-arrow">↗</span>
        </RouterLink>
      </div>
    </section>

    <section v-if="latestVehicles.data.value.items.length" class="home-discovery home-latest" aria-labelledby="home-latest-title">
      <div class="home-discovery-heading">
        <h2 id="home-latest-title">近期上架</h2>
        <RouterLink :to="{ name: 'vehicle-market' }">进入车源市场 <span aria-hidden="true">↗</span></RouterLink>
      </div>
      <div class="vehicle-grid">
        <VehicleCard v-for="vehicle in latestVehicles.data.value.items" :key="vehicle.id" :vehicle="vehicle" />
      </div>
    </section>
  </main>
</template>

<style scoped>
.home-content {
  width: min(1260px, calc(100% - 48px));
  padding-block: clamp(18px, 2.8vw, 40px) 58px;
}

.home-discovery { margin-top: clamp(30px, 4.5vw, 60px); }
.home-discovery-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin-bottom: 17px; }
.home-discovery-heading h2 { font-size: clamp(23px, 3vw, 32px); letter-spacing: -.035em; margin: 0; color: #15222b; }
.home-discovery-heading > a { color: #34454d; font-weight: 650; white-space: nowrap; }
.home-discovery-heading > a:hover { text-decoration: underline; }
.home-discovery-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }

.home-discovery-card {
  position: relative;
  isolation: isolate;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  min-height: clamp(155px, 18vw, 215px);
  padding: 18px 20px;
  color: white;
  background: #222f29;
  border-radius: 16px;
  overflow: hidden;
}

.home-discovery-card img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 59%;
  z-index: -2;
  transition: transform .45s cubic-bezier(.22, .61, .36, 1);
}

.home-discovery-card::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(0deg, #0a121afd 0%, #0a121ac7 23%, #0a121a22 68%);
}

.home-discovery-label { font-size: clamp(18px, 2.1vw, 24px); line-height: 38px; }
.home-discovery-arrow {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  background: #f0f7e526;
  border: 1px solid #f0f7e563;
  color: #f0ffd1;
  border-radius: 50%;
  font-size: 19px;
  transition: color .2s ease, background .2s ease;
}
.home-discovery-card:is(:hover, :focus-visible) img { transform: scale(1.045); }
.home-discovery-card:is(:hover, :focus-visible) .home-discovery-arrow { background: #d9f78e; color: #15241d; }
.home-latest :deep(.vehicle-card) { border-radius: 14px; }

@media (max-width: 700px) {
  .home-content { width: calc(100% - 26px); }
  .home-discovery-grid { gap: 10px; }
  .home-discovery-card { padding: 12px 10px; }
}

@media (max-width: 520px) {
  .home-discovery-grid { grid-template-columns: 1fr; }
  .home-discovery-card { min-height: 155px; padding: 14px 16px; }
}

@media (prefers-reduced-motion: reduce) {
  .home-discovery-card img, .home-discovery-arrow { transition: none; }
  .home-discovery-card:is(:hover, :focus-visible) img { transform: none; }
}
</style>
