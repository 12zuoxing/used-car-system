/**
 * 会话状态供导航、登录、个人中心和后台共同使用。
 * 登录凭据由服务端会话 Cookie 管理，浏览器本地只保存当前的公开账号信息。
 * @author pangzhihao
 */
import { reactive, readonly } from 'vue'
import { http } from '../../api/http.js'

const session = reactive({ account: null, ready: false })
let pending
let version = 0

function replaceSession(account) {
  // 登录和退出完成后，之前发出的旧会话请求不可覆盖新的界面状态。
  version += 1
  pending = undefined
  session.account = account
  session.ready = true
  http.clearCsrfToken()
}

async function restoreSession(force = false) {
  if (pending) return pending
  if (!force && session.ready) return session.account

  const started = version
  const request = http.request('/auth/me')
    .catch((error) => {
      if (error.status === 401) return null
      // 查询失败时旧身份已不能作为路由依据。保留服务端 Cookie，下次导航重新查询。
      if (version === started) {
        session.account = null
        session.ready = false
      }
      throw error
    })
    .then((account) => {
      if (version === started) {
        session.account = account
        session.ready = true
      }
      return session.account
    })
    .finally(() => {
      if (pending === request) pending = undefined
    })
  pending = request
  return request
}

async function login(body) {
  const account = await http.request('/auth/login', { method: 'POST', body })
  replaceSession(account)
  return account
}

async function logout() {
  await http.request('/auth/logout', { method: 'POST' })
  replaceSession(null)
}

const isManager = () => Boolean(session.account?.roles.some((role) => ['ADMIN', 'STAFF'].includes(role)))
const isAdministrator = () => Boolean(session.account?.roles.includes('ADMIN'))

export const auth = {
  state: readonly(session),
  restoreSession,
  register: (body) => http.request('/auth/register', { method: 'POST', body }),
  login,
  logout,
  isManager,
  isAdministrator,
}

export const roleLabels = { USER: '普通用户', STAFF: '工作人员', ADMIN: '管理员' }
