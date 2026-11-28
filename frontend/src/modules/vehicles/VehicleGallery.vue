<!-- @author pangzhihao -->
<script setup>
import { computed, ref, watch } from 'vue'
import { attachmentUrl } from '../../api/media.js'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false },
})
const activeId = ref(null)
const active = computed(() => props.photos.find((photo) => photo.id === activeId.value))
watch(() => props.photos, (photos) => {
  if (!photos.some((photo) => photo.id === activeId.value)) activeId.value = photos[0]?.id ?? null
}, { immediate: true })
</script>

<template>
  <div class="vehicle-gallery" :class="{ 'vehicle-gallery-compact': compact }">
    <img v-if="active" class="vehicle-gallery-main" :src="attachmentUrl(active.id)" :alt="active.name" />
    <p v-else class="muted-text">暂无车辆照片。</p>
    <div v-if="photos.length > 1" class="vehicle-thumbnails" aria-label="车辆照片">
      <button v-for="(photo, index) in photos" :key="photo.id" type="button" :aria-pressed="activeId === photo.id"
        :aria-label="'查看第 ' + (index + 1) + ' 张照片'" @click="activeId = photo.id">
        <img :src="attachmentUrl(photo.id)" alt="" loading="lazy" />
      </button>
    </div>
  </div>
</template>
