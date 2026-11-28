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
    // 车主登录后可直接打开指定车辆的委托表单和审核历史。
    { path: '/account/vehicles/:id(\\d+)/consignments', name: 'my-consignments', component: () => import('../modules/consignments/ConsignmentPage.vue'), meta: { requiresAuth: true } },
    // 个人中心的预约列表只展示当前会话的买家记录。
    { path: '/account/appointments', name: 'my-appointments', component: () => import('../modules/appointments/MyAppointmentsPage.vue'), meta: { requiresAuth: true } },
    // 个人交易列表与详情都要求登录，具体订单归属由服务端核对。
    { path: '/account/orders', name: 'my-orders', component: () => import('../modules/orders/MyOrdersPage.vue'), meta: { requiresAuth: true } },
    { path: '/account/orders/:id(\\d+)', name: 'my-order-detail', component: () => import('../modules/orders/OrderDetailPage.vue'), meta: { requiresAuth: true } },
    // 买家可从订单打开其贷款登记，个人中心提供申请列表。
    { path: '/account/orders/:id(\\d+)/loan', name: 'loan-application', component: () => import('../modules/loans/LoanApplicationPage.vue'), meta: { requiresAuth: true } },
    { path: '/account/loans', name: 'my-loans', component: () => import('../modules/loans/MyLoansPage.vue'), meta: { requiresAuth: true } },
    {
      path: '/admin',
      component: () => import('../modules/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, manager: true },
      children: [
        { path: '', name: 'admin', component: () => import('../modules/admin/WorkspacePage.vue') },
        { path: 'users', name: 'admin-users', component: () => import('../modules/admin/AdminUsersPage.vue'), meta: { administrator: true } },
        { path: 'vehicles', name: 'vehicle-review', component: () => import('../modules/vehicles/VehicleReviewPage.vue') },
        { path: 'reports', name: 'report-review', component: () => import('../modules/reports/ReportReviewPage.vue') },
        // 继承管理端的角色检查，再延迟加载委托和预约队列。
        { path: 'consignments', name: 'consignment-review', component: () => import('../modules/consignments/ConsignmentReviewPage.vue') },
        { path: 'appointments', name: 'appointment-queue', component: () => import('../modules/appointments/AppointmentQueuePage.vue') },
        // 后台订单路由继承管理端权限，详情切换到工作人员的动作和数据。
        { path: 'orders', name: 'order-queue', component: () => import('../modules/orders/OrderQueuePage.vue') },
        { path: 'orders/:id(\\d+)', name: 'admin-order-detail', component: () => import('../modules/orders/OrderDetailPage.vue'), props: { managed: true } },
        // 贷款经办列表和详情继承管理端的工作人员角色规则。
        { path: 'loans', name: 'loan-queue', component: () => import('../modules/loans/LoanQueuePage.vue') },
        { path: 'loans/:id(\\d+)', name: 'admin-loan-detail', component: () => import('../modules/loans/LoanReviewPage.vue') },
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
