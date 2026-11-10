<template>
  <div class="home">
    <section class="home__hero">
      <!-- 左 2/3：校园实景轮播 -->
      <div class="home__hero-banner">
        <!-- 背景轮播：淡入淡出，5s 一张 -->
        <div class="home__hero-bg" aria-hidden="true">
          <div
            v-for="(image, index) in heroImages"
            :key="image"
            class="home__hero-slide"
            :class="{ 'is-active': index === heroIndex }"
            :style="{ backgroundImage: `url(${image})` }"
          />
          <div class="home__hero-mask"></div>
        </div>
        <div class="home__hero-inner">
          <div class="home__hero-text">
            <h1>校园闲置，当面验货再付款</h1>
            <p>校内面交先验货后付款 · AI 帮你智能定价</p>
            <div class="home__hero-actions">
              <el-button v-if="auth.role === 'USER'" type="primary" size="large" @click="router.push('/publish')">
                发布闲置
              </el-button>
              <el-button v-else-if="!auth.isLogin" type="primary" size="large" @click="router.push('/register')">
                免费注册
              </el-button>
              <el-button size="large" plain @click="scrollToGrid">逛逛好物</el-button>
            </div>
          </div>
          <div class="home__hero-stats">
            <div class="home__stat">
              <span class="home__stat-value">{{ inSaleTotal }}</span>
              <span class="home__stat-label">在售好物</span>
            </div>
            <div class="home__stat">
              <span class="home__stat-value">{{ categories.length }}</span>
              <span class="home__stat-label">商品分类</span>
            </div>
            <div class="home__stat">
              <span class="home__stat-value">{{ schoolCount }}</span>
              <span class="home__stat-label">所学校</span>
            </div>
          </div>
        </div>
        <div class="home__hero-dots">
          <button
            v-for="(image, index) in heroImages"
            :key="'dot-' + index"
            type="button"
            class="home__hero-dot"
            :class="{ 'is-active': index === heroIndex }"
            :aria-label="`切换到第 ${index + 1} 张`"
            @click="onDotClick(index)"
          />
        </div>
      </div>

      <!-- 右 1/3：宣传海报 + 平台通知 -->
      <div class="home__hero-side">
        <!-- 宣传海报：替换 posterImage 常量即可换图 -->
        <div class="hero-poster">
          <img :src="posterImage" alt="平台宣传海报" />
        </div>
        <!-- 平台通知：运营端可维护，纯文字轮播 -->
        <div class="hero-notice">
          <p class="hero-notice__head">
            <el-icon><Bell /></el-icon>
            平台通知
          </p>
          <transition name="notice-fade" mode="out-in">
            <div v-if="notices.length" :key="noticeIndex" class="hero-notice__body">
              <p class="hero-notice__title">{{ notices[noticeIndex].title }}</p>
              <p class="hero-notice__content">{{ notices[noticeIndex].content }}</p>
            </div>
            <div v-else class="hero-notice__body">
              <p class="hero-notice__title">欢迎使用物归校园</p>
              <p class="hero-notice__content">校内面交先验货后付款，安心又省心。</p>
            </div>
          </transition>
          <div class="hero-notice__dots" v-if="notices.length > 1">
            <span
              v-for="(n, i) in notices"
              :key="'nd-' + i"
              class="hero-notice__dot"
              :class="{ 'is-active': i === noticeIndex }"
              @click="noticeIndex = i"
            />
          </div>
        </div>
      </div>
    </section>

    <section ref="gridRef" class="home__grid brix-container">
      <!-- 只做单向绑定：filters 是 reactive 常量，v-model 会生成 `filters = $event`
           的赋值语句并在运行时抛「Assignment to constant variable」，
           导致 change 事件永远发不出去、筛选点了没反应。条件回流统一走 @change。 -->
      <FilterBar :model-value="filters" :categories="allCategories" @change="onFilterChange" />

      <!-- 同校优先是排序偏好而非过滤，必须说明白，否则用户会以为排序坏了 -->
      <div v-if="preferSchoolName" class="home__prefer">
        <el-icon><School /></el-icon>
        <span>已优先展示 <strong>{{ preferSchoolName }}</strong> 的商品，其他学校的商品仍可继续下滑浏览</span>
      </div>

      <ItemGrid :items="items" :loading="loading" show-reset @reset="resetFilters" />
      <el-pagination
        v-if="total > query.pageSize"
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="query.pageSize"
        :current-page="query.currentPage"
        @current-change="onPageChange"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchItems } from '@/api/item'
import { fetchAnnouncements } from '@/api/admin'
import { fetchCategories } from '@/api/category'
import { findSchool, loadSchools } from '@/api/school'
import { useAuthStore } from '@/stores/auth'
import { DEFAULT_PAGE } from '@/constants'
import { Bell, School } from '@element-plus/icons-vue'
import FilterBar from '@/components/FilterBar.vue'
import ItemGrid from '@/components/ItemGrid.vue'

import heroGate from '@/assets/hero/hero-gate.jpg'
import heroCampus from '@/assets/hero/hero-campus.jpg'
import heroLibrary from '@/assets/hero/hero-library.jpg'
import posterWide from '@/assets/poster-wide.png'

/** hero 轮播图（5s 淡入淡出循环） */
const heroImages = [heroGate, heroCampus, heroLibrary]
const heroIndex = ref(0)
const HERO_INTERVAL = 5000
let heroTimer = null

/** 宣传海报：横版 3:2（内容与竖版原稿一致，已去水印） */
const posterImage = posterWide

/** 平台通知轮播（运营端维护，4s 切换） */
const notices = ref([])
const noticeIndex = ref(0)
const NOTICE_INTERVAL = 4000
let noticeTimer = null

function startHeroLoop() {
  stopHeroLoop()
  heroTimer = setInterval(() => {
    heroIndex.value = (heroIndex.value + 1) % heroImages.length
  }, HERO_INTERVAL)
}

function stopHeroLoop() {
  if (heroTimer) {
    clearInterval(heroTimer)
    heroTimer = null
  }
}

function startNoticeLoop() {
  stopNoticeLoop()
  noticeTimer = setInterval(() => {
    noticeIndex.value = (noticeIndex.value + 1) % notices.value.length
  }, NOTICE_INTERVAL)
}

function stopNoticeLoop() {
  if (noticeTimer) {
    clearInterval(noticeTimer)
    noticeTimer = null
  }
}

/** 手动切换圆点后重新计时，避免刚切就跳 */
function onDotClick(index) {
  heroIndex.value = index
  startHeroLoop()
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const categories = ref([])
/** 完整分类列表（一级+二级），供 FilterBar 二级联动 */
const allCategories = ref([])
const items = ref([])
const total = ref(0)
const inSaleTotal = ref(0)
/** 学校字典（含校区）：hero 统计位与「同校优先」提示共用 */
const schools = ref([])
/** 已收录学校数（hero 统计位） */
const schoolCount = ref(0)
const loading = ref(false)
const gridRef = ref(null)

// 分页参数（单独管理，条件变化时回到第 1 页）
const query = reactive({ ...DEFAULT_PAGE })

// 筛选条件（与 URL query 双向同步，可分享 / 后退复现）
const filters = reactive({
  keyword: '',
  categoryId: '',
  schoolId: '',
  campusId: '',
  minPrice: '',
  maxPrice: '',
  sort: 'latest'
})

/**
 * 同校优先的学校 ID。
 *
 * <p>只在「已登录 + 用户填过学校 + 自己没筛学校/校区」时生效：
 * 一旦用户主动筛了学校，结果本来就是该校的，再叠优先排序没有意义。</p>
 */
const preferSchoolId = computed(() => {
  if (filters.schoolId || filters.campusId) return ''
  return auth.user?.schoolId || ''
})

/** 提示条文案用的学校全称；字典没加载到时为空，不显示提示 */
const preferSchoolName = computed(() => {
  if (!preferSchoolId.value) return ''
  return findSchool(schools.value, preferSchoolId.value)?.name || ''
})

/** 从 URL query 恢复筛选条件 */
function syncFromRoute() {
  const q = route.query
  filters.keyword = typeof q.keyword === 'string' ? q.keyword : ''
  filters.categoryId = typeof q.categoryId === 'string' ? q.categoryId : ''
  filters.schoolId = typeof q.schoolId === 'string' ? q.schoolId : ''
  filters.campusId = typeof q.campusId === 'string' ? q.campusId : ''
  filters.minPrice = typeof q.minPrice === 'string' ? q.minPrice : ''
  filters.maxPrice = typeof q.maxPrice === 'string' ? q.maxPrice : ''
  filters.sort = typeof q.sort === 'string' ? q.sort : 'latest'
  query.currentPage = Number(q.currentPage) > 0 ? Number(q.currentPage) : 1
}

/** 拉取商品列表 */
async function load() {
  loading.value = true
  try {
    const params = {
      currentPage: query.currentPage,
      pageSize: query.pageSize,
      sort: filters.sort || 'latest'
    }
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.categoryId) params.categoryId = filters.categoryId
    // 校区优先用 campusId（精确），只选到学校时退化为 schoolId（全校聚合）
    if (filters.campusId) params.campusId = filters.campusId
    else if (filters.schoolId) params.schoolId = filters.schoolId
    if (filters.minPrice !== '' && filters.minPrice !== null) params.minPrice = filters.minPrice
    if (filters.maxPrice !== '' && filters.maxPrice !== null) params.maxPrice = filters.maxPrice
    // 同校优先：只改变排序（本校在前、他校在后），不过滤掉其他学校
    if (preferSchoolId.value) params.preferSchoolId = preferSchoolId.value

    const data = await fetchItems(params)
    items.value = data?.records || []
    total.value = Number(data?.total || 0)
    // hero 的「在售好物」是全站口径，只有无筛选时才用当前总数覆盖，
    // 否则搜索/筛选后会出现“在售好物 0”这种误导性数字
    const noFilter = !filters.keyword && !filters.categoryId && !filters.schoolId && !filters.campusId
      && (filters.minPrice === '' || filters.minPrice === null || filters.minPrice === undefined)
      && (filters.maxPrice === '' || filters.maxPrice === null || filters.maxPrice === undefined)
    if (query.currentPage === 1 && noFilter) inSaleTotal.value = total.value
  } finally {
    loading.value = false
  }
}

/**
 * 条件变化：先把 FilterBar 抛上来的条件写回本地，再回到第 1 页并写 URL。
 *
 * <p>这里必须显式吸收 payload。FilterBar 用的是单向 `:model-value` 绑定
 * （父级 `filters` 是 reactive 常量，用 v-model 会生成 `filters = $event`
 * 的非法赋值并在运行时抛错），父子回流全靠这个 change 事件；
 * 漏掉这一步的表现就是——下拉框选了学校，请求里却没有 schoolId，
 * 列表始终返回全部商品。</p>
 *
 * @param payload FilterBar 抛出的完整筛选条件
 */
function onFilterChange(payload) {
  if (payload) {
    filters.keyword = payload.keyword || ''
    filters.categoryId = payload.categoryId || ''
    filters.schoolId = payload.schoolId || ''
    filters.campusId = payload.campusId || ''
    filters.minPrice = payload.minPrice ?? ''
    filters.maxPrice = payload.maxPrice ?? ''
    filters.sort = payload.sort || 'latest'
  }
  query.currentPage = 1
  writeQuery()
}

/** 翻页 */
function onPageChange(page) {
  query.currentPage = page
  writeQuery()
}

/** 重置筛选 */
function resetFilters() {
  filters.keyword = ''
  filters.categoryId = ''
  filters.schoolId = ''
  filters.campusId = ''
  filters.minPrice = ''
  filters.maxPrice = ''
  filters.sort = 'latest'
  query.currentPage = 1
  writeQuery()
}

/** 把筛选条件写入 URL query（首页可分享、后退可复现） */
function writeQuery() {
  const q = {}
  if (filters.keyword) q.keyword = filters.keyword
  if (filters.categoryId) q.categoryId = filters.categoryId
  // 带了校区就必须同时带学校，否则刷新后学校下拉为空、校区选项拉不出来
  if (filters.schoolId) q.schoolId = String(filters.schoolId)
  if (filters.campusId) q.campusId = String(filters.campusId)
  if (filters.minPrice !== '' && filters.minPrice !== null) q.minPrice = filters.minPrice
  if (filters.maxPrice !== '' && filters.maxPrice !== null) q.maxPrice = filters.maxPrice
  if (filters.sort && filters.sort !== 'latest') q.sort = filters.sort
  if (query.currentPage > 1) q.currentPage = String(query.currentPage)
  router.replace({ path: '/', query: q })
}

function scrollToGrid() {
  gridRef.value?.scrollIntoView({ behavior: 'smooth' })
}

watch(
  () => route.query,
  () => {
    syncFromRoute()
    load()
  }
)

onMounted(async () => {
  startHeroLoop()
  syncFromRoute()
  load()
  try {
    // 后端 Long 序列化为字符串（"parentId": "0"），必须用 String 比较
    const list = (await fetchCategories()) || []
    allCategories.value = list
    // 首页统计与筛选条主列表只展示一级分类
    categories.value = list.filter((c) => !c.parentId || String(c.parentId) === '0')
  } catch (error) {
    categories.value = []
    allCategories.value = []
  }
  try {
    schools.value = (await loadSchools()) || []
    schoolCount.value = schools.value.length
  } catch (error) {
    // 学校字典不可用时不阻塞首页，统计位保持 0，「同校优先」也随之退化
    schools.value = []
    schoolCount.value = 0
  }
  try {
    notices.value = (await fetchAnnouncements()) || []
    if (notices.value.length > 1) startNoticeLoop()
  } catch (error) {
    // 公告接口不可用时显示默认欢迎语，不阻塞首页
    notices.value = []
  }
})

onUnmounted(() => {
  stopHeroLoop()
  stopNoticeLoop()
})
</script>

<style scoped>
/* hero：左 2/3 实景轮播 + 右 1/3 海报/通知，整体增高 */
.home__hero {
  display: flex;
  gap: 14px;
  align-items: stretch;
}

/* 左：实景轮播块（原 hero 主体） */
.home__hero-banner {
  position: relative;
  flex: 2;
  min-width: 0;
  min-height: 420px;
  border-radius: var(--brix-radius-lg);
  overflow: hidden;
  color: #fff;
  background: #10281e;
}

/* 轮播层：绝对定位铺满，淡入淡出 */
.home__hero-bg {
  position: absolute;
  inset: 0;
}

.home__hero-slide {
  position: absolute;
  inset: 0;
  background-position: center;
  background-size: cover;
  opacity: 0;
  transform: scale(1.04);
  transition: opacity 0.9s cubic-bezier(0.25, 1, 0.5, 1), transform 6s linear;
}

.home__hero-slide.is-active {
  opacity: 1;
  transform: scale(1);
}

/* 遮罩：左侧深保证文案可读，右侧透出照片 */
.home__hero-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    rgba(13, 34, 26, 0.9) 0%,
    rgba(13, 34, 26, 0.62) 52%,
    rgba(13, 34, 26, 0.28) 100%
  );
}

.home__hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
  height: 100%;
  padding: 52px 48px;
}

.home__hero-text h1 {
  font-size: 40px;
  line-height: 1.25;
  letter-spacing: 1px;
  margin: 0;
  font-weight: 700;
}

/* 价签橙红短横线：全站唯一强调色的克制出现 */
.home__hero-text h1::before {
  content: '';
  display: block;
  width: 44px;
  height: 5px;
  border-radius: 3px;
  background: var(--brix-accent);
  margin-bottom: 18px;
}

.home__hero-text p {
  margin-top: 14px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.78);
}

.home__hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

.home__hero-actions :deep(.el-button--primary) {
  background: #fff;
  border-color: #fff;
  color: var(--brix-primary);
  font-weight: 700;
}

.home__hero-actions :deep(.el-button--primary:hover) {
  background: var(--brix-primary-soft);
  border-color: var(--brix-primary-soft);
}

.home__hero-actions :deep(.el-button.is-plain) {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.45);
  color: #fff;
}

.home__hero-actions :deep(.el-button.is-plain:hover) {
  border-color: rgba(255, 255, 255, 0.8);
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}

/* 统计：去卡片盒子，改为行内数字组 + 细竖线分隔 */
.home__hero-stats {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* ---------- 右 1/3：海报 + 通知 ---------- */
.home__hero-side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 420px;
}

/* 宣传海报卡：与 3:2 海报图等比，完整展示不裁剪 */
.hero-poster {
  flex: 1.6;
  min-height: 0;
  border-radius: var(--brix-radius-lg);
  overflow: hidden;
  background: #fdf7e0;
  box-shadow: var(--brix-shadow-sm);
}

.hero-poster img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  display: block;
}

/* 平台通知卡：深绿渐变 + 文字轮播 */
.hero-notice {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: var(--brix-radius-lg);
  padding: 18px 20px;
  background:
    radial-gradient(320px 160px at 90% -20%, rgba(120, 210, 170, 0.28) 0%, transparent 65%),
    linear-gradient(150deg, #0e3a2a 0%, #0a2c20 60%, #08241b 100%);
  color: #fff;
  box-shadow: var(--brix-shadow-sm);
  overflow: hidden;
}

.hero-notice__head {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.92);
}

.hero-notice__head .el-icon {
  color: #9fd4bb;
}

.hero-notice__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.hero-notice__title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #eafaf0;
}

.hero-notice__content {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.72);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hero-notice__dots {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}

.hero-notice__dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.28);
  cursor: pointer;
  transition: background 0.2s, width 0.2s;
}

.hero-notice__dot.is-active {
  width: 16px;
  background: #9fd4bb;
}

/* 公告文字淡入淡出 */
.notice-fade-enter-active,
.notice-fade-leave-active {
  transition: opacity 0.45s ease, transform 0.45s ease;
}

.notice-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.notice-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.home__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 28px;
}

.home__stat + .home__stat {
  border-left: 0.5px solid rgba(255, 255, 255, 0.22);
}

.home__stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.home__stat-label {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.68);
  letter-spacing: 1px;
}

.home__grid {
  padding-top: 28px;
}

/* 同校优先提示：轻量条，别和筛选条抢视觉重心 */
.home__prefer {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: -10px 0 16px;
  padding: 9px 14px;
  border-radius: 10px;
  background: var(--brix-primary-soft);
  color: var(--brix-primary);
  font-size: 13px;
  line-height: 1.5;
}

.home__prefer strong {
  font-weight: 600;
}

/* 轮播指示点：右下角 */
.home__hero-dots {
  position: absolute;
  right: 22px;
  bottom: 18px;
  z-index: 2;
  display: flex;
  gap: 8px;
}

.home__hero-dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: width 0.3s cubic-bezier(0.25, 1, 0.5, 1), background 0.2s ease;
}

.home__hero-dot.is-active {
  width: 22px;
  background: #fff;
}
</style>
