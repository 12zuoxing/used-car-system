/**
 * 同源接口客户端。会话 Cookie 由浏览器管理，写请求使用服务端签发的 CSRF 令牌。
 * @author pangzhihao
 */
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

async function readResponse(response) {
  if (response.status === 204) return null
  const isJson = response.headers.get('content-type')?.includes('application/json')
  const body = isJson ? await response.json() : null
  if (!response.ok) {
    throw new ApiError(response.status, body?.code, body?.message || '请求失败，请稍后再试。')
  }
  return body
}

/** fetch 可注入，便于在不进入浏览器的情况下验证令牌刷新和请求行为。 */
export function createHttpClient(fetchRequest = globalThis.fetch.bind(globalThis)) {
  let csrfRequest

  function clearCsrfToken() {
    csrfRequest = undefined
  }

  function getCsrfToken() {
    if (!csrfRequest) {
      const pending = fetchRequest('/api/auth/csrf', {
        credentials: 'same-origin',
        headers: new Headers({ Accept: 'application/json' }),
      }).then(readResponse)
      csrfRequest = pending
      // 取令牌失败时清除缓存，让下一次请求可以重新获取。
      pending.catch(() => {
        if (csrfRequest === pending) clearCsrfToken()
      })
    }
    return csrfRequest
  }

  async function request(path, { method = 'GET', body, headers: requestHeaders } = {}, retryCsrf = true) {
    const headers = new Headers(requestHeaders) // 复制调用方提供的幂等标识等业务请求头。
    headers.set('Accept', 'application/json') // 统一接收后端的 JSON 响应。
    const writes = !['GET', 'HEAD', 'OPTIONS'].includes(method)
    const pending = writes ? getCsrfToken() : undefined
    if (pending) {
      const { headerName, token } = await pending
      headers.set(headerName, token)
    }
    // 让浏览器为文件表单生成 Content-Type 和 boundary，CSRF 头仍照常附加。
    const multipart = body instanceof FormData
    if (body !== undefined && !multipart) headers.set('Content-Type', 'application/json')

    try {
      const response = await fetchRequest('/api' + path, {
        method,
        headers,
        credentials: 'same-origin',
        body: body === undefined || multipart ? body : JSON.stringify(body),
      })
      return await readResponse(response)
    } catch (error) {
      // CSRF_INVALID 来自进入业务控制器之前的过滤器，原请求未执行业务。
      // 普通 403、网络失败和业务冲突不能自动重放。
      if (retryCsrf && writes && error.code === 'CSRF_INVALID') {
        if (csrfRequest === pending) clearCsrfToken()
        return request(path, { method, body, headers: requestHeaders }, false) // 刷新令牌时继续使用本次请求原有的业务头。
      }
      throw error
    }
  }

  return { request, clearCsrfToken }
}

export const http = createHttpClient()
