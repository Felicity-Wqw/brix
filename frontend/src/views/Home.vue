<template>
  <div class="home">
    <section class="home__hero">
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
      <div class="home__hero-inner brix-container">
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
            <span class="home__stat-value">3</span>
            <span class="home__stat-label">个校区</span>
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
    </section>

    <section ref="gridRef" class="home__grid brix-container">
      <FilterBar v-model="filters" :categories="allCategories" @change="onFilterChange" />
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
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchItems } from '@/api/item'
import { fetchCategories } from '@/api/category'
import { useAuthStore } from '@/stores/auth'
import { DEFAULT_PAGE } from '@/constants'
import FilterBar from '@/components/FilterBar.vue'
import ItemGrid from '@/components/ItemGrid.vue'

import heroGate from '@/assets/hero/hero-gate.jpg'
import heroCampus from '@/assets/hero/hero-campus.jpg'
import heroLibrary from '@/assets/hero/hero-library.jpg'

/** hero 轮播图（5s 淡入淡出循环） */
const heroImages = [heroGate, heroCampus, heroLibrary]
const heroIndex = ref(0)
const HERO_INTERVAL = 5000
let heroTimer = null

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
const loading = ref(false)
const gridRef = ref(null)

// 分页参数（单独管理，条件变化时回到第 1 页）
const query = reactive({ ...DEFAULT_PAGE })

// 筛选条件（与 URL query 双向同步，可分享 / 后退复现）
const filters = reactive({
  keyword: '',
  categoryId: '',
  campus: '',
  minPrice: '',
  maxPrice: '',
  sort: 'latest'
})

/** 从 URL query 恢复筛选条件 */
function syncFromRoute() {
  const q = route.query
  filters.keyword = typeof q.keyword === 'string' ? q.keyword : ''
  filters.categoryId = typeof q.categoryId === 'string' ? q.categoryId : ''
  filters.campus = typeof q.campus === 'string' ? q.campus : ''
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
    if (filters.campus) params.campus = filters.campus
    if (filters.minPrice !== '' && filters.minPrice !== null) params.minPrice = filters.minPrice
    if (filters.maxPrice !== '' && filters.maxPrice !== null) params.maxPrice = filters.maxPrice

    const data = await fetchItems(params)
    items.value = data?.records || []
    total.value = Number(data?.total || 0)
    if (query.currentPage === 1) inSaleTotal.value = total.value
  } finally {
    loading.value = false
  }
}

/** 条件变化：回到第 1 页并把条件写入 URL */
function onFilterChange() {
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
  filters.campus = ''
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
  if (filters.campus) q.campus = filters.campus
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
})

onUnmounted(stopHeroLoop)
</script>

<style scoped>
/* hero：校园实景轮播背景 + 墨绿遮罩，市集海报气质 */
.home__hero {
  position: relative;
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
