/**
 * 三种车源列表共享分页请求行为。只采用最近一次请求的返回结果。
 * @author pangzhihao
 */
import { ref } from 'vue'

export function useVehiclePage(fetchPage, pageSize) {
  const data = ref({ items: [], total: 0, page: 1, pageSize })
  const ready = ref(false)
  const loading = ref(false)
  const error = ref('')
  let requestVersion = 0

  async function load(page = 1) {
    const version = ++requestVersion
    loading.value = true
    error.value = ''
    try {
      const result = await fetchPage({ page, pageSize })
      if (version === requestVersion) {
        data.value = result
        ready.value = true
      }
    } catch (failure) {
      if (version === requestVersion) error.value = failure.message
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  return { data, ready, loading, error, load }
}
