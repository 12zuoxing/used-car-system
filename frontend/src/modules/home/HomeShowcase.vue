<!-- @author pangzhihao -->
<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import Dialog from 'primevue/dialog' // 沿用组件库的焦点约束、Esc 和关闭操作。

const slides = [
  { key: 'showroom', label: '精选车源', title: '找到下一辆好车', description: '从在售车源里，对比报价、里程和车况。' },
  { key: 'city', label: '城市通勤', title: '通勤用车，轻松选', description: '先定预算，再按品牌和城市缩小范围。' },
  { key: 'weekend', label: '周末出发', title: '周末出发，选对车', description: '先看车况资料，再预约到店看车。' },
] // 文字与图片一起切换，链接始终可进入真实车源市场。
const interval = 6500 // 让用户有足够时间阅读并操作当前画面。
const selected = ref(0)
const active = computed(() => slides[selected.value])
const auto = ref(false)
const hovered = ref(false)
const focused = ref(false)
const hidden = ref(false)
const filmOpen = ref(false)
const player = ref(null)
const prefersReducedMotion = ref(false)
const running = computed(() => auto.value && !hovered.value && !focused.value && !hidden.value && !filmOpen.value)
const announcement = ref('') // 仅播报手动切换，避免自动轮播反复打断读屏。
let preference
let touchStart

function show(index, manual = true) {
  selected.value = (index + slides.length) % slides.length // 从最后一张继续切回首张。
  if (manual) announcement.value = '正在展示' + active.value.label
}

watch([running, selected], ([enabled], _, onCleanup) => {
  if (!enabled) return
  const timer = window.setTimeout(() => show(selected.value + 1, false), interval)
  onCleanup(() => window.clearTimeout(timer)) // 暂停、换图或离开首页后回收计时器。
})

function direction(event) {
  if (!touchStart || !event.changedTouches.length) return
  const { clientX, clientY } = event.changedTouches[0]
  const distance = clientX - touchStart.clientX
  if (Math.abs(distance) >= 48 && Math.abs(distance) > Math.abs(clientY - touchStart.clientY)) {
    show(selected.value + (distance < 0 ? 1 : -1))
  }
  touchStart = null
}

function syncVisibility() {
  hidden.value = document.hidden // 切到其他标签页时暂停轮播。
}

function syncPreference() {
  prefersReducedMotion.value = preference.matches
  if (preference.matches) auto.value = false // 尊重系统减少动态效果的偏好。
}

onMounted(() => {
  preference = window.matchMedia('(prefers-reduced-motion: reduce)')
  syncPreference()
  auto.value = !preference.matches
  syncVisibility()
  preference.addEventListener('change', syncPreference)
  document.addEventListener('visibilitychange', syncVisibility)
})

onUnmounted(() => {
  preference?.removeEventListener('change', syncPreference)
  document.removeEventListener('visibilitychange', syncVisibility)
})

function playFilm() {
  if (!prefersReducedMotion.value) player.value?.play().catch(() => {}) // 自动播放不可用时仍可使用原生播放按钮。
}
</script>

<template>
  <section class="showcase" aria-label="首页精彩车源" aria-roledescription="轮播"
    @pointerenter="hovered = $event.pointerType === 'mouse'" @pointerleave="hovered = false"
    @focusin="focused = true" @focusout="focused = $event.currentTarget.contains($event.relatedTarget)"
    @keydown.left.prevent="show(selected - 1)" @keydown.right.prevent="show(selected + 1)"
    @touchstart.passive="touchStart = $event.touches[0]" @touchend.passive="direction" @touchcancel="touchStart = null">
    <div class="showcase-visual" aria-hidden="true">
      <!-- 保持图片节点存在，切换时只动透明度，不让景别切换触发布局抖动。 -->
      <img v-for="(slide, index) in slides" :key="slide.key" :class="{ 'showcase-image-current': index === selected }"
        :src="'/images/showcase/' + slide.key + '.jpg'" alt="" :fetchpriority="index === 0 ? 'high' : 'low'" />
    </div>
    <div class="showcase-stage">
      <div :key="active.key" class="showcase-copy">
        <h1>{{ active.title }}</h1>
        <p>{{ active.description }}</p>
      </div>
      <div class="showcase-actions">
        <RouterLink class="showcase-primary" :to="{ name: 'vehicle-market' }">
          浏览车源 <span aria-hidden="true">↗</span>
        </RouterLink>
        <button class="showcase-film" type="button" @click="filmOpen = true">
          <span class="showcase-play" aria-hidden="true"></span> 播放展车短片
        </button>
        <RouterLink class="showcase-publish" :to="{ name: 'vehicle-publish' }">发布车源</RouterLink>
      </div>
    </div>

    <div class="showcase-controls">
      <div class="showcase-tabs" aria-label="选择轮播画面">
        <button v-for="(slide, index) in slides" :key="slide.key" type="button" :aria-pressed="index === selected" @click="show(index)">
          {{ slide.label }}
          <span v-if="running && index === selected" class="showcase-progress" aria-hidden="true"
            :style="{ animationDuration: interval + 'ms' }"></span>
        </button>
      </div>
      <div class="showcase-pagination" aria-label="轮播操作">
        <button type="button" aria-label="上一张" @click="show(selected - 1)"><span aria-hidden="true">←</span></button>
        <button type="button" :aria-label="auto ? '暂停轮播' : '继续轮播'" @click="auto = !auto">
          <span :class="auto ? 'showcase-pause' : 'showcase-play'" aria-hidden="true"></span>
        </button>
        <button type="button" aria-label="下一张" @click="show(selected + 1)"><span aria-hidden="true">→</span></button>
      </div>
    </div>
    <span class="showcase-announcement" aria-live="polite">{{ announcement }}</span>
  </section>

  <Dialog v-model:visible="filmOpen" header="展车短片" modal :draggable="false"
    :style="{ width: 'min(960px, calc(100vw - 28px))' }" @show="playFilm" @hide="player?.pause()">
    <video ref="player" class="showcase-video" controls muted playsinline preload="metadata"
      poster="/images/showcase/showroom.jpg" aria-label="三种场景的汽车展车短片">
      <source src="/media/showcase.webm" type="video/webm" />
      当前浏览器无法播放此视频。
    </video>
    <p class="showcase-video-caption">由车图剪辑制作 · {{ slides.length }} 个展车场景</p>
  </Dialog>
</template>

<style scoped>
.showcase {
  --accent: #def795;
  --control: #eef9dc;
  position: relative;
  isolation: isolate;
  color: #fff;
  border-radius: 24px;
  overflow: hidden;
  background: #141e22;
  box-shadow: 0 18px 48px #101c2b23;
}

.showcase-visual, .showcase-visual::after, .showcase-visual img { position: absolute; inset: 0; width: 100%; height: 100%; }
.showcase-visual { z-index: -1; }
.showcase-visual img {
  object-fit: cover;
  object-position: 67% 55%;
  opacity: 0;
  transform: scale(1.035);
  transition: opacity .65s ease, transform 1.5s cubic-bezier(.16, 1, .3, 1);
}
.showcase-visual img.showcase-image-current { opacity: 1; transform: scale(1); }
.showcase-visual::after {
  content: "";
  background: linear-gradient(90deg, #0b1419f2, #0b1419d4 28%, #0b141953 58%, #0b14190c 88%),
    linear-gradient(0deg, #0b1419ec, #0b14192b 30%, transparent 56%);
}

.showcase-stage {
  min-height: clamp(390px, 42vw, 546px);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 25px;
  padding: clamp(25px, 5.1vw, 70px);
}
.showcase-copy { max-width: min(63%, 650px); animation: showcase-reveal .48s cubic-bezier(.22, .6, .36, 1) both; }
.showcase-copy h1 { font-size: clamp(38px, 5.8vw, 76px); line-height: 1.1; font-weight: 770; letter-spacing: -.055em; text-wrap: balance; }
.showcase-copy p { margin-top: 18px; font-size: clamp(15px, 1.45vw, 19px); color: #e8eeeb; }

.showcase-actions, .showcase-controls, .showcase-tabs, .showcase-pagination { display: flex; align-items: center; }
.showcase-actions { gap: 10px; flex-wrap: wrap; }
.showcase-actions :is(a, button) { min-height: 44px; display: inline-flex; align-items: center; justify-content: center; gap: 8px; font-weight: 700; }
.showcase-primary { background: var(--accent); color: #17251b; border-radius: 8px; padding: 10px 17px; }
.showcase-primary:hover { background: #edffbd; }
.showcase-film { color: #fff; background: #15221f9c; border: 1px solid #edf3e469; border-radius: 8px; padding: 9px 13px; cursor: pointer; }
.showcase-film:hover { background: #33443b; }
.showcase-publish { padding-inline: 7px; color: #f0f5f2; }
.showcase-publish:hover { text-decoration: underline; }
.showcase :is(a, button):focus-visible { outline-color: var(--accent); }
.showcase-play { display: inline-block; width: 0; height: 0; border-block: 5px solid transparent; border-left: 8px solid currentColor; }
.showcase-pause { display: inline-block; width: 9px; height: 11px; border-inline: 2px solid currentColor; }

.showcase-controls {
  justify-content: space-between;
  gap: 8px;
  margin-inline: clamp(18px, 5.1vw, 70px);
  padding-block: 13px 18px;
  border-top: 1px solid #eef9dc4a;
}
.showcase-tabs, .showcase-pagination { gap: 5px; }
.showcase-controls button {
  display: inline-grid;
  place-items: center;
  color: var(--control);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
}
.showcase-tabs button { position: relative; min-height: 40px; padding: 5px 12px; border-radius: 6px; white-space: nowrap; }
.showcase-tabs button:hover, .showcase-tabs button[aria-pressed="true"] { background: #e5f6bd23; }
.showcase-tabs button[aria-pressed="true"] { color: var(--accent); border-color: #f0ffcf42; font-weight: 700; }
.showcase-pagination button { width: 41px; height: 41px; border-color: #f0ffcf6a; border-radius: 50%; font-size: 18px; }
.showcase-pagination button:hover { background: #f0ffcf30; }
.showcase-progress {
  position: absolute;
  inset-inline: 5px;
  bottom: 2px;
  height: 1px;
  background: var(--accent);
  transform-origin: left;
  animation: showcase-timer linear both;
}
.showcase-announcement { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.showcase-video { display: block; width: 100%; border-radius: 8px; background: #111b20; }
.showcase-video-caption { margin-top: 10px; color: var(--muted); font-size: 13px; }

@keyframes showcase-reveal { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes showcase-timer { from { transform: scaleX(0); } to { transform: scaleX(1); } }

@media (max-width: 760px) {
  .showcase-stage { justify-content: flex-end; min-height: 470px; gap: 17px; padding: 22px; }
  .showcase-copy { max-width: 100%; }
  .showcase-copy p { margin-top: 11px; }
  .showcase-visual img { height: 78%; object-position: 76% 50%; }
  .showcase-visual::after {
    background: linear-gradient(0deg, #0b1419 11%, #0b1419e8 25%, #0b141978 48%, #0b141910 77%),
      linear-gradient(90deg, #0b14195e, transparent 57%);
  }
  .showcase-controls { margin-inline: 17px; }
}

@media (max-width: 440px) {
  .showcase { border-radius: 16px; }
  .showcase-stage { min-height: 410px; padding: 17px 14px 19px; }
  .showcase-copy h1 { font-size: clamp(30px, 9.7vw, 42px); }
  .showcase-copy p { font-size: 14px; }
  .showcase-actions { gap: 6px; }
  .showcase-actions :is(a, button) { font-size: 13px; min-height: 43px; }
  .showcase-primary, .showcase-film { padding-inline: 8px; }
  .showcase-controls { margin-inline: 10px; gap: 4px; padding-block: 10px 12px; }
  .showcase-tabs, .showcase-pagination { gap: 3px; }
  .showcase-tabs button { padding-inline: 4px; font-size: 12px; }
  .showcase-pagination button { width: 36px; height: 36px; }
}

@media (prefers-reduced-motion: reduce) {
  .showcase-visual img { transition: none; transform: none; }
  .showcase-copy, .showcase-progress { animation: none; }
}
</style>
