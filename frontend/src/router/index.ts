import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由表（12 页 + 404）。
 *
 * - 使用 hash 路由：无需服务端 history 配置（PRD 部署形态为本地裸机双进程）。
 * - 守卫仅作体验层拦截，真正的安全边界在后端接口（架构 5.6）。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { guestOnly: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: { guestOnly: true, title: '注册' }
  },
  {
    path: '/',
    component: () => import('@/layout/BasicLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        meta: { requiresAuth: true, allowGuest: true, title: '首页' }
      },
      {
        path: 'items/:id',
        name: 'item-detail',
        component: () => import('@/views/ItemDetail.vue'),
        meta: { requiresAuth: true, allowGuest: true, title: '商品详情' }
      },
      {
        path: 'publish',
        name: 'publish',
        component: () => import('@/views/Publish.vue'),
        meta: { requiresAuth: true, roles: ['USER'], title: '发布商品' }
      },
      {
        path: 'orders/:id',
        name: 'order-detail',
        component: () => import('@/views/OrderDetail.vue'),
        meta: { requiresAuth: true, title: '订单详情' }
      },
      {
        path: 'checkout/:orderId',
        name: 'checkout',
        component: () => import('@/views/Checkout.vue'),
        meta: { requiresAuth: true, roles: ['USER'], title: '收银台' }
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/Messages.vue'),
        meta: { requiresAuth: true, title: '私信' }
      }
    ]
  },
  {
    // 小法庭：独立于个人中心的顶层页面（社区共治入口在首页分类下方）
    path: '/jury',
    name: 'jury-hall',
    component: () => import('@/views/JuryHall.vue'),
    meta: { requiresAuth: true, title: '小法庭' }
  },
  {
    // 求购墙：买家发需求、卖家私信接单
    path: '/want-wall',
    name: 'want-wall',
    component: () => import('@/views/WantWall.vue'),
    meta: { requiresAuth: true, title: '求购墙' }
  },
  {
    path: '/my',
    component: () => import('@/layout/UserCenterLayout.vue'),
    children: [
      {
        path: 'items',
        name: 'my-items',
        component: () => import('@/views/MyItems.vue'),
        meta: { requiresAuth: true, roles: ['USER'], title: '我的商品' }
      },
      {
        path: 'favorites',
        name: 'my-favorites',
        component: () => import('@/views/MyFavorites.vue'),
        meta: { requiresAuth: true, title: '我的收藏' }
      },
      {
        path: 'history',
        name: 'my-history',
        component: () => import('@/views/MyBrowseHistory.vue'),
        meta: { requiresAuth: true, title: '浏览历史' }
      },
      {
        path: 'orders',
        name: 'my-orders',
        component: () => import('@/views/MyOrders.vue'),
        meta: { requiresAuth: true, title: '我的订单' }
      },
      {
        path: 'refunds',
        name: 'my-refunds',
        component: () => import('@/views/MyRefunds.vue'),
        meta: { requiresAuth: true, title: '我的售后' }
      },
      {
        path: 'analysis',
        name: 'my-analysis',
        component: () => import('@/views/MyAnalysis.vue'),
        meta: { requiresAuth: true, title: '数据分析' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { requiresAuth: true, title: '个人中心' }
      }
    ]
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/admin/AdminLogin.vue'),
    meta: { title: '后台登录' }
  },
  {
    path: '/admin/screen',
    name: 'admin-screen',
    component: () => import('@/views/admin/AdminScreen.vue'),
    meta: { requiresAuth: true, roles: ['OPERATOR'], title: '数据大屏' }
  },
  {
    path: '/admin',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/AdminDashboard.vue'),
        meta: { title: '数据看板', sub: '平台经营大盘 · 支持 7 / 30 天切换', roles: ['OPERATOR'] }
      },
      {
        path: 'items',
        name: 'admin-items',
        component: () => import('@/views/admin/AdminItems.vue'),
        meta: { title: '商品审核', sub: '三级分流队列 · 低风险直发 / 中风险待审 / 高风险驳回', roles: ['REVIEWER'] }
      },
      {
        path: 'reports',
        name: 'admin-reports',
        component: () => import('@/views/admin/AdminReports.vue'),
        meta: { title: '举报处理', sub: '成立即下架，不成立则仅关闭举报', roles: ['REVIEWER'] }
      },
      {
        path: 'audit-logs',
        name: 'admin-audit-logs',
        component: () => import('@/views/admin/AdminAuditLogs.vue'),
        meta: { title: '审核日志', sub: '只增不改 · 商品与卖家快照 + 两道闸判定 + 审核时间', roles: ['REVIEWER'] }
      },
      {
        path: 'jury',
        name: 'admin-jury',
        component: () => import('@/views/admin/AdminJury.vue'),
        meta: { title: '小法庭裁决', sub: '陪审期满案件终审 · 双方证据 + 用户投票统计', roles: ['REVIEWER'] }
      },
      {
        path: 'appeals',
        name: 'admin-appeals',
        component: () => import('@/views/admin/AdminAppeals.vue'),
        meta: { title: '申诉处理', sub: '卖家对审核/处置结果的申诉 · AI 复核建议仅供参考', roles: ['REVIEWER'] }
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/AdminUsers.vue'),
        meta: { title: '用户管理', sub: '封禁 / 解封 · 后台账号不受此约束', roles: ['OPERATOR'] }
      },
      {
        path: 'announcements',
        name: 'admin-announcements',
        component: () => import('@/views/admin/AdminAnnouncements.vue'),
        meta: { title: '公告管理', sub: '首页平台通知轮播文字 · 运营端维护', roles: ['OPERATOR'] }
      },
      {
        path: 'orders',
        name: 'admin-orders',
        component: () => import('@/views/admin/AdminOrders.vue'),
        meta: { title: '订单管理', sub: '全平台订单只读排查', roles: ['OPERATOR'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 })
})

/** 后台角色的默认落点（审核员没有看板权限，不能给 /admin/dashboard） */
function adminHomeOf(role: string): string {
  return role === 'OPERATOR' ? '/admin/dashboard' : '/admin/items'
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 1. 有 token 但没有用户信息 → 拉一次 /auth/me
  if (auth.token && !auth.user) {
    try {
      await auth.fetchMe()
    } catch (error) {
      auth.clear()
    }
  }

  const inAdminArea = to.path === '/admin/login' || to.path.startsWith('/admin/')

  // 2. 后台登录页：已是后台角色就直接进后台，普通用户仍可在此页重新登录
  if (to.path === '/admin/login') {
    if (auth.isAdmin) {
      return adminHomeOf(auth.role)
    }
    document.title = '后台登录 · 物归校园'
    return true
  }

  // 3. 后台路由：必须登录且必须是后台角色
  //    这里只是体验层拦截，真正的边界是后端 SecurityConfig 的 URL 规则
  if (inAdminArea) {
    if (!auth.isLogin) {
      return { path: '/admin/login', query: { redirect: encodeURIComponent(to.fullPath) } }
    }
    if (!auth.isAdmin) {
      return { path: '/admin/login', query: { redirect: encodeURIComponent(to.fullPath) } }
    }
    // 3.1 角色不匹配的菜单（运营点进审核队列 / 审核员点进看板）→ 送回自己首页
    const roles = to.meta.roles as string[] | undefined
    if (roles && roles.length > 0 && !roles.includes(auth.role)) {
      return adminHomeOf(auth.role)
    }
    document.title = `${(to.meta.title as string) || '管理后台'} · 物归校园`
    return true
  }

  // 4. 后台角色访问前台：直接送回后台，避免在看板与商城之间来回切换身份
  if (auth.isAdmin) {
    return adminHomeOf(auth.role)
  }

  // 5. 仅游客可访问（登录 / 注册）
  if (to.meta.guestOnly && auth.isLogin) {
    return '/'
  }

  // 6. 需要登录：
  //    - 普通页面（发布/订单/个人中心等）：未登录一律先登录
  //    - 浏览页面（meta.allowGuest：首页/详情）：登录页点过「游客逛一逛」的可直接浏览
  if (to.meta.requiresAuth && !auth.isLogin) {
    if (to.meta.allowGuest && auth.isGuestMode) {
      return true
    }
    return { path: '/login', query: { redirect: encodeURIComponent(to.fullPath) } }
  }

  // 7. 设置标题
  const title = (to.meta.title as string) || '物归校园'
  document.title = `${title} · 物归校园`
  return true
})

export default router
