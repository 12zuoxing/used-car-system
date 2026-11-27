import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backend = process.env.VITE_BACKEND || 'http://127.0.0.1:8080'
const proxy = { '/api': backend, '/ws': { target: backend, ws: true } }

export default defineConfig({
  plugins: [vue()],
  server: { proxy },
  preview: { proxy },
})
