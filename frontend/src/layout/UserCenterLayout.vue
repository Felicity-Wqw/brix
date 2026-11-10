<template>
  <div class="user-center">
    <NavBar />
    <main class="brix-container user-center__main">
      <aside class="user-center__aside brix-card">
        <div class="user-center__profile">
          <el-avatar :size="52" :src="resolveImage(auth.user?.avatar)">
            {{ nicknameInitial(auth.user?.nickname) }}
          </el-avatar>
          <div class="user-center__name">{{ auth.user?.nickname || '未登录' }}</div>
          <div class="user-center__credit">
            信用分
            <span class="user-center__credit-value">{{ auth.user?.creditScore ?? '-' }}</span>
          </div>
        </div>
        <ul class="user-center__menu">
          <li
            v-for="item in menus"
            :key="item.path"
            :class="['user-center__menu-item', { 'is-active': route.path === item.path }]"
            @click="router.push(item.path)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </li>
        </ul>
      </aside>

      <section class="user-center__content">
        <router-view v-slot="{ Component }">
          <transition name="brix-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </section>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import NavBar from './components/NavBar.vue'
import AppFooter from './components/AppFooter.vue'
import { useAuthStore } from '@/stores/auth'
import { resolveImage, nicknameInitial } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 个人中心二级菜单
const menus = [
  { path: '/my/items', label: '我的商品', icon: 'Goods' },
  { path: '/my/favorites', label: '我的收藏', icon: 'Star' },
  { path: '/my/history', label: '浏览历史', icon: 'View' },
  { path: '/my/orders', label: '我的订单', icon: 'List' },
  { path: '/my/refunds', label: '我的售后', icon: 'Service' },
  { path: '/my/analysis', label: '数据分析', icon: 'TrendCharts' },
  { path: '/my/profile', label: '个人资料', icon: 'User' }
]
</script>

<style scoped>
.user-center {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.user-center__main {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 24px 0 0;
  align-items: flex-start;
}

.user-center__aside {
  width: 220px;
  flex-shrink: 0;
  overflow: hidden;
}

.user-center__profile {
  padding: 24px 16px 16px;
  text-align: center;
  border-bottom: 0.5px solid var(--brix-border);
}

.user-center__name {
  margin-top: 10px;
  font-weight: 600;
}

.user-center__credit {
  margin-top: 4px;
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.user-center__credit-value {
  color: var(--brix-primary);
  font-weight: 600;
}

.user-center__menu {
  padding: 8px;
}

.user-center__menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--brix-text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.user-center__menu-item:hover {
  background: #f5f7f9;
  color: var(--brix-text);
}

.user-center__menu-item.is-active {
  background: var(--brix-primary-soft);
  color: var(--brix-primary);
  font-weight: 600;
}

.user-center__content {
  flex: 1;
  min-width: 0;
}
</style>
