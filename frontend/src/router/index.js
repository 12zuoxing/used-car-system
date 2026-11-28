/**
 * 页面路由与访问检查。前端守卫帮助用户进入正确页面，数据权限仍由后端处理。
 * @author pangzhihao
 */
import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../modules/home/HomePage.vue'
import { auth } from '../modules/auth/session.js'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/login', name: 'login', component: () => import('../modules/auth/LoginPage.vue'), meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: () => import('../modules/auth/RegisterPage.vue'), meta: { guestOnly: true } },
    { path: '/account', name: 'account', component: () => import('../modules/auth/AccountPage.vue'), meta: { requiresAuth: true } },
    { path: '/vehicles', name: 'vehicle-market', component: () => import('../modules/vehicles/VehicleListPage.vue') },
    { path: '/vehicles/:id(\\d+)', name: 'vehicle-detail', component: () => import('../modules/vehicles/VehicleDetailPage.vue') },
    { path: '/vehicles/publish', name: 'vehicle-publish', component: () => import('../modules/vehicles/PublishVehiclePage.vue'), meta: { requiresAuth: true } },
    { path: '/account/vehicles', name: 'my-vehicles', component: () => import('../modules/vehicles/MyVehiclesPage.vue'), meta: { requiresAuth: true } },
    { path: '/account/vehicles/:id(\\d+)', name: 'my-vehicle-detail', component: () => import('../modules/vehicles/VehicleDetailPage.vue'), props: { managed: true }, meta: { requiresAuth: true } },
    { path: '/account/vehicles/:id(\\d+)/edit', name: 'vehicle-edit', component: () => import('../modules/vehicles/PublishVehiclePage.vue'), meta: { requiresAuth: true } },
    {
      path: '/admin',
      component: () => import('../modules/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, manager: true },
      children: [
        { path: '', name: 'admin', component: () => import('../modules/admin/WorkspacePage.vue') },
        { path: 'users', name: 'admin-users', component: () => import('../modules/admin/AdminUsersPage.vue'), meta: { administrator: true } },
        { path: 'vehicles', name: 'vehicle-review', component: () => import('../modules/vehicles/VehicleReviewPage.vue') },
        { path: 'reports', name: 'report-review', component: () => import('../modules/reports/ReportReviewPage.vue') },
        { path: 'dictionaries', name: 'admin-dictionaries', component: () => import('../modules/admin/DictionaryPage.vue'), meta: { administrator: true } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'home' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  // 直接打开受保护地址和刷新页面时，必须等待后端恢复会话。
  try {
    await auth.restoreSession(Boolean(to.meta.requiresAuth))
  } catch {
    if (to.meta.requiresAuth) {
      return { name: 'login', query: { redirect: to.fullPath, unavailable: '1' } }
    }
    return true
  }

  if (to.meta.requiresAuth && !auth.state.account) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.meta.manager && !auth.isManager()) || (to.meta.administrator && !auth.isAdministrator())) {
    return { name: 'account' }
  }
  if (to.meta.guestOnly && auth.state.account) {
    return { name: 'account' }
  }
  return true
})
