/**
 * 前端启动入口。用户端与管理端共用 PrimeVue Aura 的组件主题。
 * @author pangzhihao
 */
import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import { definePreset } from '@primeuix/themes'
import Aura from '@primeuix/themes/aura'
import App from './App.vue'
import { router } from './router/index.js'
import './styles/app.css'
import './styles/vehicles.css'

// 主色统一放在主题配置中。业务页面无需重复修改组件内部样式。
const UsedCarTheme = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{blue.50}', 100: '{blue.100}', 200: '{blue.200}', 300: '{blue.300}',
      400: '{blue.400}', 500: '{blue.600}', 600: '{blue.700}', 700: '{blue.800}',
      800: '{blue.900}', 900: '{blue.950}', 950: '{blue.950}',
    },
  },
})

createApp(App)
  .use(PrimeVue, { theme: { preset: UsedCarTheme, options: { darkModeSelector: false } } })
  .use(router)
  .mount('#app')
