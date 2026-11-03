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

  // 2. 仅游客可访问（登录 / 注册）
  if (to.meta.guestOnly && auth.isLogin) {
    return '/'
  }

  // 3. 需要登录：
  //    - 普通页面（发布/订单/个人中心等）：未登录一律先登录
  //    - 浏览页面（meta.allowGuest：首页/详情）：登录页点过「游客逛一逛」的可直接浏览
  if (to.meta.requiresAuth && !auth.isLogin) {
    if (to.meta.allowGuest && auth.isGuestMode) {
      return true
    }
    return { path: '/login', query: { redirect: encodeURIComponent(to.fullPath) } }
  }

  // 4. 设置标题
  const title = (to.meta.title as string) || '物归校园'
  document.title = `${title} · 物归校园`
  return true
})

export default router
