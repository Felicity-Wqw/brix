<template>
  <header class="navbar">
    <div class="brix-container navbar__inner">
      <router-link to="/" class="navbar__logo">
        <span class="navbar__logo-mark">归</span>
        <span class="navbar__logo-text">物归校园</span>
      </router-link>

      <el-input
        v-model="keyword"
        class="navbar__search"
        placeholder="搜索教材、数码、生活用品…"
        clearable
        @keyup.enter="doSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <nav class="navbar__links">
        <router-link to="/" class="navbar__link">首页</router-link>
        <router-link v-if="auth.isLogin" to="/messages" class="navbar__link navbar__link--badge">
          私信
          <el-badge v-if="auth.unreadCount > 0" :value="auth.unreadCount" :max="99" class="navbar__badge" />
        </router-link>
        <router-link v-if="auth.isLogin" to="/my/items" class="navbar__link navbar__link--badge" @click="onClickComments">
          留言
          <el-badge v-if="auth.commentUnread > 0" :value="auth.commentUnread" :max="99" class="navbar__badge" />
        </router-link>
        <router-link v-if="auth.isLogin" to="/my/orders" class="navbar__link">我的订单</router-link>
        <el-button
          v-if="auth.role === 'USER'"
          type="primary"
          class="navbar__sell"
          @click="router.push('/publish')"
        >
          卖闲置
        </el-button>
      </nav>

      <div class="navbar__user">
        <template v-if="auth.isLogin">
          <el-dropdown trigger="click" @command="onCommand">
            <span class="navbar__user-trigger">
              <el-avatar :size="30" :src="resolveImage(auth.user?.avatar)">
                {{ nicknameInitial(auth.user?.nickname) }}
              </el-avatar>
              <span class="navbar__user-name">{{ auth.user?.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="myItems">我的商品</el-dropdown-item>
                <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button text @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchUnreadCount } from '@/api/chat'
import { fetchCommentUnreadSummary } from '@/api/comment'
import { resolveImage, nicknameInitial } from '@/utils/format'
import ws from '@/utils/ws'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const keyword = ref('')

// 首页 URL 带 keyword 时同步到搜索框
watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = typeof value === 'string' ? value : ''
  },
  { immediate: true }
)

onMounted(async () => {
  if (!auth.isLogin) return
  // 私信未读数与 WebSocket 连接（仅登录态）
  try {
    const data = await fetchUnreadCount()
    auth.setUnread(data?.count ?? 0)
  } catch (error) {
    // 未读数失败不阻断页面
  }
  ws.connect()
  // 留言未读数：立即拉取 + 60s 轮询
  await refreshCommentUnread()
  commentTimer = setInterval(refreshCommentUnread, 60000)
})

onBeforeUnmount(() => {
  if (commentTimer) {
    clearInterval(commentTimer)
    commentTimer = null
  }
})

let commentTimer = null

/** 拉取我收到的未读留言数 */
async function refreshCommentUnread() {
  try {
    const data = await fetchCommentUnreadSummary()
    auth.setCommentUnread(data?.count ?? 0)
  } catch (error) {
    // 静默失败，不影响导航
  }
}

/** 点「留言」：有未读直达最新留言商品，否则去我的商品 */
async function onClickComments() {
  if (auth.commentUnread <= 0) {
    return // 默认跳 /my/items
  }
  try {
    const data = await fetchCommentUnreadSummary()
    if (data?.latestItemId) {
      router.push(`/items/${data.latestItemId}`)
    }
  } catch (error) {
    // 保持默认跳转
  }
}

/** 搜索：把关键词写进 URL query */
function doSearch() {
  router.push({ path: '/', query: keyword.value ? { keyword: keyword.value } : {} })
}

/** 下拉菜单命令 */
function onCommand(command) {
  if (command === 'profile') router.push('/my/profile')
  if (command === 'myItems') router.push('/my/items')
  if (command === 'favorites') router.push('/my/favorites')
  if (command === 'logout') {
    ws.disconnect()
    auth.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 253, 249, 0.62);
  backdrop-filter: blur(18px) saturate(1.2);
  -webkit-backdrop-filter: blur(18px) saturate(1.2);
  border-bottom: 0.5px solid rgba(28, 42, 36, 0.08);
}

.navbar__inner {
  display: flex;
  align-items: center;
  height: 64px;
  gap: 24px;
}

.navbar__logo {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.navbar__logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--brix-primary);
  color: #fff;
  font-weight: 700;
  box-shadow: inset 0 -2px 0 rgba(0, 0, 0, 0.14);
}

.navbar__logo-text {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

.navbar__search {
  width: 320px;
}

.navbar__links {
  display: flex;
  align-items: center;
  gap: 24px;
  flex: 1;
}

.navbar__link {
  position: relative;
  color: var(--brix-text-secondary);
  font-size: 14px;
  font-weight: 500;
  padding: 4px 0;
  transition: color 0.2s cubic-bezier(0.25, 1, 0.5, 1);
}

/* 编辑式导航：active 用墨绿短横线指示 */
.navbar__link::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -4px;
  width: 0;
  height: 2px;
  border-radius: 2px;
  background: var(--brix-primary);
  transform: translateX(-50%);
  transition: width 0.24s cubic-bezier(0.25, 1, 0.5, 1);
}

.navbar__link:hover {
  color: var(--brix-primary);
}

.navbar__link.router-link-exact-active {
  color: var(--brix-primary);
  font-weight: 600;
}

.navbar__link.router-link-exact-active::after {
  width: 18px;
}

.navbar__link--badge {
  display: inline-flex;
  align-items: center;
}

.navbar__badge {
  margin-left: 4px;
  transform: translateY(-6px);
}

/* 「卖闲置」：核心转化动作，做成实心按钮从链接里跳出来 */
.navbar__sell {
  margin-left: 4px;
  --el-button-size: 34px;
  height: 34px;
  padding: 0 18px;
  border-radius: 999px;
  font-weight: 700;
  box-shadow: 0 4px 14px rgba(14, 122, 80, 0.32);
}

.navbar__user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.navbar__user-name {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
