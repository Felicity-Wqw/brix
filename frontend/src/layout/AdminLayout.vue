<template>
  <div class="admin-layout">
    <!-- 侧栏：只渲染当前角色有权限的菜单（体验层；真正的拦截在后端 SecurityConfig） -->
    <aside class="admin-side">
      <div class="admin-side__brand">
        <span class="admin-side__mark">归</span>
        <div class="admin-side__brand-text">
          <p class="admin-side__name">物归校园</p>
          <p class="admin-side__sub">管理后台</p>
        </div>
      </div>

      <nav class="admin-side__nav">
        <template v-for="group in visibleGroups" :key="group.title">
          <p class="admin-side__group">{{ group.title }}</p>
          <router-link
            v-for="menu in group.menus"
            :key="menu.path"
            :to="menu.path"
            class="admin-side__item"
            :class="{ 'admin-side__item--active': isActive(menu.path) }"
          >
            <el-icon class="admin-side__icon"><component :is="menu.icon" /></el-icon>
            <span>{{ menu.title }}</span>
            <el-badge
              v-if="menu.badgeKey && badgeOf(menu.badgeKey) > 0"
              :value="badgeOf(menu.badgeKey)"
              class="admin-side__badge"
            />
          </router-link>
        </template>
      </nav>

      <div class="admin-side__foot">
        <p class="admin-side__foot-line">
          角色：<strong>{{ auth.user?.nickname || '-' }}</strong>
        </p>
        <p class="admin-side__foot-line admin-side__foot-line--muted">
          {{ roleLabel }} · 权限由服务端强制
        </p>
      </div>
    </aside>

    <div class="admin-body">
      <!-- 顶栏 -->
      <header class="admin-head">
        <div class="admin-head__left">
          <h2 class="admin-head__title">{{ pageTitle }}</h2>
          <span v-if="pageSub" class="admin-head__sub">{{ pageSub }}</span>
        </div>
        <div class="admin-head__right">
          <span class="admin-head__role" :class="`admin-head__role--${auth.role.toLowerCase()}`">
            {{ roleLabel }}
          </span>
          <el-button link class="admin-head__action" @click="goStorefront">
            <el-icon><Shop /></el-icon>
            前台
          </el-button>
          <el-button link class="admin-head__action" @click="logout">
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </header>

      <main class="admin-main">
        <router-view v-slot="{ Component }">
          <transition name="admin-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchAdminBadges } from '@/api/admin'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/**
 * 菜单表：menus 的 roles 与后端 SecurityConfig 的 URL 规则严格对齐。
 * 只写「谁能进」，不写「谁不能进」——新增角色时扩这里即可。
 */
const MENU_GROUPS = [
  {
    title: '经营概览',
    menus: [
      { title: '数据看板', path: '/admin/dashboard', icon: 'Odometer', roles: ['OPERATOR'] }
    ]
  },
  {
    title: '内容治理',
    menus: [
      { title: '商品审核', path: '/admin/items', icon: 'Checked', roles: ['REVIEWER'], badgeKey: 'pendingItems' },
      { title: '举报处理', path: '/admin/reports', icon: 'Warning', roles: ['REVIEWER'], badgeKey: 'pendingReports' },
      { title: '审核日志', path: '/admin/audit-logs', icon: 'Document', roles: ['REVIEWER'] },
      { title: '小法庭裁决', path: '/admin/jury', icon: 'Stamp', roles: ['REVIEWER'], badgeKey: 'pendingJury' }
    ]
  },
  {
    title: '平台经营',
    menus: [
      { title: '用户管理', path: '/admin/users', icon: 'UserFilled', roles: ['OPERATOR'] },
      { title: '订单管理', path: '/admin/orders', icon: 'Tickets', roles: ['OPERATOR'] },
      { title: '公告管理', path: '/admin/announcements', icon: 'Bell', roles: ['OPERATOR'] }
    ]
  }
]

/** 侧栏待办角标：审核员关心两个队列，运营不显示 */
const badges = ref({ pendingItems: 0, pendingReports: 0 })

const visibleGroups = computed(() =>
  MENU_GROUPS.map((group) => ({
    ...group,
    menus: group.menus.filter((menu) => menu.roles.includes(auth.role))
  })).filter((group) => group.menus.length > 0)
)

const roleLabel = computed(() => {
  if (auth.isReviewer) return '审核员'
  if (auth.isOperator) return '运营'
  return auth.user?.roleDesc || '未授权'
})

const pageTitle = computed(() => route.meta.title || '管理后台')
const pageSub = computed(() => route.meta.sub || '')

function isActive(path) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function badgeOf(key) {
  return Number(badges.value?.[key] || 0)
}

/** 顶栏「前台」：后台角色不具备 USER 权限，只提示不可交易 */
function goStorefront() {
  ElMessage.info('后台账号不含买家/卖家权限，如需体验交易请退出后用 student01 登录')
}

async function logout() {
  try {
    await ElMessageBox.confirm('退出后需重新登录后台，确定退出？', '退出后台', {
      confirmButtonText: '退出',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch (error) {
    return
  }
  auth.clear()
  router.replace('/admin/login')
}

onMounted(async () => {
  // 角标失败不阻塞页面（后端尚未实现时静默为 0）
  try {
    const data = await fetchAdminBadges()
    badges.value = { pendingItems: 0, pendingReports: 0, ...(data || {}) }
  } catch (error) {
    /* 忽略 */
  }
})
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: #f4f6f8;
}

/* ---------------- 侧栏 ---------------- */
.admin-side {
  flex: none;
  width: 224px;
  display: flex;
  flex-direction: column;
  background: #0f1319;
  color: #cdd6e0;
}

.admin-side__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 22px 20px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.admin-side__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #0e7a50;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
}

.admin-side__brand-text {
  line-height: 1.3;
}

.admin-side__name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #eef2f7;
}

.admin-side__sub {
  margin: 0;
  font-size: 11.5px;
  color: #6b7787;
}

.admin-side__nav {
  flex: 1;
  padding: 14px 12px;
  overflow-y: auto;
}

.admin-side__group {
  margin: 14px 8px 6px;
  font-size: 11px;
  letter-spacing: 1px;
  color: #5a6472;
}

.admin-side__group:first-child {
  margin-top: 0;
}

.admin-side__item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 12px;
  margin-bottom: 2px;
  border-radius: 9px;
  font-size: 13.5px;
  color: #b8c2ce;
  transition: background 0.16s ease, color 0.16s ease;
}

.admin-side__item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #eef2f7;
}

.admin-side__item--active {
  background: rgba(14, 122, 80, 0.2);
  color: #6fd2a6;
  font-weight: 600;
}

.admin-side__icon {
  font-size: 15px;
}

.admin-side__badge {
  margin-left: auto;
}

.admin-side__badge :deep(.el-badge__content) {
  background: #ff4d2e;
  border: none;
}

.admin-side__foot {
  padding: 14px 20px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.admin-side__foot-line {
  margin: 0;
  font-size: 12px;
  color: #9aa5b3;
}

.admin-side__foot-line--muted {
  margin-top: 3px;
  font-size: 11.5px;
  color: #5f6a78;
}

/* ---------------- 右侧主体 ---------------- */
.admin-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin-head {
  flex: none;
  height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: #fff;
  border-bottom: 1px solid #e8ecf1;
}

.admin-head__left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.admin-head__title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #1c2a24;
}

.admin-head__sub {
  font-size: 12.5px;
  color: #8d99a8;
}

.admin-head__right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.admin-head__role {
  margin-right: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.admin-head__role--reviewer {
  background: #fdece8;
  color: #c9411f;
}

.admin-head__role--operator {
  background: var(--brix-primary-soft);
  color: var(--brix-primary);
}

.admin-head__action {
  font-size: 13px;
  color: #5c6b63;
}

.admin-main {
  flex: 1;
  padding: 22px 28px 32px;
  min-width: 0;
}

.admin-fade-enter-active,
.admin-fade-leave-active {
  transition: opacity 0.16s ease;
}

.admin-fade-enter-from,
.admin-fade-leave-to {
  opacity: 0;
}
</style>
