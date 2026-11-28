<!-- @author pangzhihao -->
<script setup>
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import { attachmentUrl } from '../../api/media.js'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false },
  allowPreview: { type: Boolean, default: false }, // 公开详情提供大图，后台仍使用紧凑相册。
})
const activeId = ref(null)
const previewOpen = ref(false)
const activeIndex = computed(() => props.photos.findIndex((photo) => photo.id === activeId.value))
const active = computed(() => props.photos[activeIndex.value])
watch(() => props.photos, (photos) => {
  if (!photos.some((photo) => photo.id === activeId.value)) activeId.value = photos[0]?.id ?? null
  if (!photos.length) previewOpen.value = false
}, { immediate: true })

function move(step) {
  const total = props.photos.length
  if (total < 2) return
  activeId.value = props.photos[(activeIndex.value + step + total) % total].id // 两端回绕，缩略图同时更新。
}
</script>

<template>
  <div class="vehicle-gallery" :class="{ 'vehicle-gallery-compact': compact }">
    <div v-if="active && allowPreview" class="vehicle-gallery-stage" @keydown.left.prevent="move(-1)" @keydown.right.prevent="move(1)">
      <button class="vehicle-gallery-open" type="button" aria-label="查看车辆大图" @click="previewOpen = true">
        <img class="vehicle-gallery-main" :src="attachmentUrl(active.id)" :alt="active.name" />
        <span class="vehicle-gallery-zoom" aria-hidden="true">查看大图 ↗</span>
      </button>
      <template v-if="photos.length > 1">
        <button class="vehicle-gallery-step vehicle-gallery-prev" type="button" aria-label="上一张照片" @click="move(-1)">←</button>
        <button class="vehicle-gallery-step vehicle-gallery-next" type="button" aria-label="下一张照片" @click="move(1)">→</button>
        <span class="vehicle-gallery-count" aria-live="polite">{{ activeIndex + 1 }} / {{ photos.length }}</span>
      </template>
    </div>
    <img v-else-if="active" class="vehicle-gallery-main" :src="attachmentUrl(active.id)" :alt="active.name" />
    <p v-else class="muted-text">暂无车辆照片。</p>
    <div v-if="photos.length > 1" class="vehicle-thumbnails" aria-label="车辆照片">
      <button v-for="(photo, index) in photos" :key="photo.id" type="button" :aria-pressed="activeId === photo.id"
        :aria-label="'查看第 ' + (index + 1) + ' 张照片'" @click="activeId = photo.id">
        <img :src="attachmentUrl(photo.id)" alt="" loading="lazy" />
      </button>
    </div>
    <Dialog v-if="allowPreview" v-model:visible="previewOpen" header="车辆照片" modal :draggable="false"
      :style="{ width: 'min(1040px, calc(100vw - 28px))' }">
      <div v-if="active" class="vehicle-gallery-preview" @keydown.left.prevent="move(-1)" @keydown.right.prevent="move(1)">
        <img :src="attachmentUrl(active.id)" :alt="active.name" />
        <div v-if="photos.length > 1" class="vehicle-gallery-preview-actions">
          <button type="button" aria-label="上一张照片" @click="move(-1)">←</button>
          <span aria-live="polite">{{ activeIndex + 1 }} / {{ photos.length }}</span>
          <button type="button" aria-label="下一张照片" @click="move(1)">→</button>
        </div>
      </div>
    </Dialog>
  </div>
</template>
