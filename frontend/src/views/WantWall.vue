<template>
  <div class="want-page">
    <NavBar />
    <main class="brix-container want-page__main">
      <!-- 页头：多彩渐变 + 装饰 -->
      <div class="want-hero">
        <span class="want-hero__deco want-hero__deco--a" aria-hidden="true"></span>
        <span class="want-hero__deco want-hero__deco--b" aria-hidden="true"></span>
        <span class="want-hero__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M5 8h14l-1.2 12.2a1.8 1.8 0 0 1-1.8 1.8H8a1.8 1.8 0 0 1-1.8-1.8L5 8Z" />
            <path d="M8.5 10V6.5a3.5 3.5 0 0 1 7 0V10" />
          </svg>
        </span>
        <div class="want-hero__text">
          <h1 class="want-hero__title">求购墙</h1>
          <p class="want-hero__sub">
            想要的还没人卖？喊一嗓子，让卖家来找你
          </p>
        </div>
        <el-button round plain @click="router.push('/')">返回首页</el-button>
      </div>

      <div class="want-layout">
        <!-- 左：发布卡 -->
        <div class="want-publish">
          <p class="want-publish__title">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="vertical-align: -2px;">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5Z" />
            </svg>
            发布求购
          </p>
          <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="想要什么？如：收一台九成新 iPad" />
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="补充细节：型号、成色要求、配件…写得越清楚，卖家越好接单"
          />
          <div class="want-publish__row">
            <el-select v-model="form.schoolId" class="want-publish__school" placeholder="学校" clearable filterable @change="onSchoolChange">
              <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
            <el-select
              v-model="form.campusId"
              class="want-publish__campus"
              :placeholder="form.schoolId ? '校区' : '先选学校'"
              :disabled="!form.schoolId"
              clearable
              @change="syncCampusName"
            >
              <el-option v-for="c in campusOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </div>
          <el-input v-model="budgetText" class="want-publish__budget" placeholder="预算（可不填 = 面议）" @input="budgetText = budgetText.replace(/[^\d.]/g, '')">
            <template #prefix>¥</template>
          </el-input>
          <el-button type="primary" class="want-publish__submit" :loading="publishing" @click="submit">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="margin-right: 4px;">
              <path d="M3 11 21 4v13L3 14v-3Z" />
              <path d="M11.6 16.8a3 3 0 1 1-5.8-1.6" />
            </svg>
            发布求购
          </el-button>
          <p class="want-publish__hint">发布后卖家会私信联系你，谈妥了记得回来关闭求购～</p>
        </div>

        <!-- 右：求购列表 -->
        <div class="want-list-wrap">
          <div class="want-list-head">
            <el-radio-group v-model="status" size="small" @change="reload">
              <el-radio-button value="OPEN">求购中</el-radio-button>
              <el-radio-button value="CLOSED">已结束</el-radio-button>
            </el-radio-group>
            <span class="want-list-head__total">共 {{ total }} 条</span>
          </div>

          <div v-loading="loading" class="want-list">
            <el-empty v-if="!loading && rows.length === 0" description="还没有求购，来发第一条吧" :image-size="90" />
            <div v-for="(row, index) in rows" :key="row.id" class="want-card" :class="[`want-card--c${index % 4}`, { 'want-card--closed': row.status === 'CLOSED' }]">
              <div class="want-card__head">
                <el-avatar :size="36" :src="resolveImage(row.avatar)">{{ nicknameInitial(row.nickname) }}</el-avatar>
                <div class="want-card__user">
                  <span class="want-card__name">
                    {{ row.nickname }}
                    <span v-if="row.mine" class="want-card__mine">我的求购</span>
                  </span>
                  <span class="want-card__time">{{ formatTime(row.createdAt, 'MM-DD HH:mm') }}</span>
                </div>
                <span class="want-card__state" :class="row.status === 'CLOSED' ? 'want-card__state--closed' : 'want-card__state--open'">
                  {{ row.statusDesc }}
                </span>
              </div>
              <p class="want-card__title">{{ row.title }}</p>
              <p v-if="row.description" class="want-card__desc">{{ row.description }}</p>
              <div class="want-card__tags">
                <span v-if="row.schoolName || row.campus" class="want-card__tag">
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M3 21h18" />
                    <path d="M5 21V7l7-4 7 4v14" />
                    <path d="M9 21v-6h6v6" />
                    <path d="M12 3v4" />
                  </svg>
                  {{ [row.schoolName, row.campus].filter(Boolean).join(' · ') }}
                </span>
                <span v-else class="want-card__tag want-card__tag--muted">
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M12 21s-7-5.5-7-11a7 7 0 0 1 14 0c0 5.5-7 11-7 11Z" />
                    <circle cx="12" cy="10" r="2.5" />
                  </svg>
                  校区未填
                </span>
              </div>
              <div class="want-card__foot">
                <span class="want-card__budget">
                  <template v-if="row.budget">预算 <strong>¥{{ row.budget }}</strong></template>
                  <template v-else>价格 <strong>面议</strong></template>
                </span>
                <span class="want-card__ops">
                  <el-button v-if="row.mine && row.status === 'OPEN'" link size="small" type="info" @click="onClose(row)">结束求购</el-button>
                  <el-button
                    v-if="row.status === 'OPEN' && !row.mine"
                    type="primary" size="small" round
                    @click="router.push(`/messages?peerId=${row.userId}`)"
                  >
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="margin-right: 3px;">
                      <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-3.8-.8L3 20l1-4.1a8.4 8.4 0 1 1 17-4.4Z" />
                    </svg>
                    私信 TA
                  </el-button>
                </span>
              </div>
            </div>
          </div>

          <div class="want-pager" v-if="total > pageSize">
            <el-pagination
              v-model:current-page="page"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              background
              @current-change="load"
            />
          </div>
        </div>
      </div>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchWants, publishWant, closeWant } from '@/api/want'
import { loadSchools } from '@/api/school'
import { formatTime, resolveImage, nicknameInitial } from '@/utils/format'
import NavBar from '@/layout/components/NavBar.vue'
import AppFooter from '@/layout/components/AppFooter.vue'

const router = useRouter()
const loading = ref(false)
const publishing = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const status = ref('OPEN')

/** 学校字典（含校区），发布时两级联动 */
const schools = ref([])

const form = reactive({ title: '', description: '', campus: '', schoolId: '', campusId: '' })
const budgetText = ref('')

const campusOptions = computed(() => {
  if (!form.schoolId) return []
  const school = schools.value.find((s) => String(s.id) === String(form.schoolId))
  return school?.campuses || []
})

/** 切学校清校区；不选校区时直接用学校名 */
function onSchoolChange() {
  form.campusId = ''
  form.campus = ''
}

/** 校区选择同步为文本（存展示名） */
function syncCampusName() {
  const c = campusOptions.value.find((c) => String(c.id) === String(form.campusId))
  form.campus = c ? c.name : ''
}

async function load() {
  loading.value = true
  try {
    const data = await fetchWants({ status: status.value, currentPage: page.value, pageSize })
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写求购标题')
    return
  }
  publishing.value = true
  try {
    await publishWant({
      title: form.title.trim(),
      description: form.description.trim(),
      budget: budgetText.value === '' ? null : Number(budgetText.value),
      campus: form.campus,
      schoolId: form.schoolId || null
    })
    ElMessage.success('求购已发布，等卖家来找你')
    form.title = ''
    form.description = ''
    form.campus = ''
    form.schoolId = ''
    form.campusId = ''
    budgetText.value = ''
    status.value = 'OPEN'
    reload()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    publishing.value = false
  }
}

function onClose(row) {
  ElMessageBox.confirm('谈妥了？关闭后卖家将不能再私信该求购。', '结束求购', { type: 'warning' })
    .then(async () => {
      await closeWant(row.id)
      ElMessage.success('已结束')
      load()
    })
    .catch(() => {})
}

onMounted(async () => {
  load()
  try {
    schools.value = (await loadSchools()) || []
  } catch (error) {
    schools.value = []
  }
})
</script>

<style scoped>
.want-page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.want-page__main {
  flex: 1;
  padding: 24px 0 40px;
}

/* ---------- 页头：多彩渐变，青春感 ---------- */
.want-hero {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 28px;
  margin-bottom: 18px;
  border-radius: 20px;
  background: linear-gradient(115deg, #ffe3c2 0%, #ffd6e0 38%, #e7d8ff 72%, #d9f2ff 100%);
  border: 1px solid #ffdcb8;
}

.want-hero__deco {
  position: absolute;
  border-radius: 999px;
  opacity: 0.55;
  pointer-events: none;
}

.want-hero__deco--a {
  width: 120px;
  height: 120px;
  right: -30px;
  top: -40px;
  background: radial-gradient(circle, rgba(255, 170, 90, 0.5), transparent 70%);
}

.want-hero__deco--b {
  width: 90px;
  height: 90px;
  right: 150px;
  bottom: -50px;
  background: radial-gradient(circle, rgba(255, 120, 160, 0.45), transparent 70%);
}

.want-hero__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(230, 130, 60, 0.22);
  font-size: 28px;
}

.want-hero__text {
  flex: 1;
  min-width: 0;
}

.want-hero__title {
  margin: 0;
  font-size: 21px;
  font-weight: 900;
  color: #1c2a24;
  letter-spacing: 1px;
}

.want-hero__sub {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #6b6157;
}

/* ---------- 双栏 ---------- */
.want-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 18px;
  align-items: start;
}

/* ---------- 发布卡 ---------- */
.want-publish {
  position: sticky;
  top: 84px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px 18px 16px;
  border-radius: 18px;
  background: var(--brix-surface, #fffdf9);
  border: 1px solid var(--brix-border, rgba(28, 42, 36, 0.08));
  box-shadow: var(--brix-shadow-sm);
}

.want-publish__title {
  margin: 0 2px 2px;
  font-size: 14px;
  font-weight: 800;
  color: #1c2a24;
}

.want-publish__row {
  display: flex;
  gap: 8px;
}

.want-publish__school {
  flex: 1.4;
  min-width: 0;
}

.want-publish__campus {
  flex: 1;
  min-width: 0;
}

.want-publish__submit {
  border-radius: 999px;
  font-weight: 800;
  letter-spacing: 1px;
}

.want-publish__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--brix-text-placeholder, #96a29b);
}

/* ---------- 列表 ---------- */
.want-list-wrap {
  min-width: 0;
}

.want-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.want-list-head__total {
  font-size: 12.5px;
  color: var(--brix-text-placeholder, #96a29b);
}

.want-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  min-height: 200px;
}

/* 卡片：顶部彩条四色循环 */
.want-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 16px 12px;
  border-radius: 16px;
  background: var(--brix-surface, #fffdf9);
  border: 1px solid var(--brix-border, rgba(28, 42, 36, 0.08));
  box-shadow: var(--brix-shadow-sm);
  overflow: hidden;
  transition: transform 0.24s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.24s ease, border-color 0.24s ease;
}

.want-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
}

.want-card--c0::before { background: linear-gradient(90deg, #ffb36b, #ffcf8b); }
.want-card--c1::before { background: linear-gradient(90deg, #7fd8a8, #b8ecc9); }
.want-card--c2::before { background: linear-gradient(90deg, #8fb7ff, #c2d8ff); }
.want-card--c3::before { background: linear-gradient(90deg, #ff9fb0, #ffc9d4); }

.want-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--brix-shadow-md);
  border-color: rgba(255, 150, 80, 0.4);
}

.want-card--closed {
  opacity: 0.6;
}

.want-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.want-card__user {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.want-card__name {
  font-size: 13px;
  font-weight: 600;
  color: #1c2a24;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.want-card__mine {
  padding: 0 7px;
  border-radius: 999px;
  background: linear-gradient(90deg, #ffe0b3, #ffd0da);
  color: #a2611c;
  font-size: 10.5px;
  font-weight: 700;
}

.want-card__time {
  font-size: 11px;
  color: var(--brix-text-placeholder, #96a29b);
}

.want-card__state {
  flex: none;
  padding: 1px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.want-card__state--open {
  background: linear-gradient(90deg, #d9f5e5, #eafaf0);
  color: var(--brix-primary, #0e7a50);
  border: 1px solid #bfe8d1;
}

.want-card__state--closed {
  background: #f0f3f5;
  color: #5c6b63;
}

.want-card__title {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  color: #1c2a24;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.want-card__desc {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--brix-text-secondary, #5c6b63);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.want-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.want-card__tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(28, 42, 36, 0.05);
  font-size: 11.5px;
  color: var(--brix-text-secondary, #5c6b63);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.want-card__tag--muted {
  opacity: 0.7;
}

.want-card__foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--brix-border, rgba(28, 42, 36, 0.08));
}

.want-card__budget {
  font-size: 12px;
  color: var(--brix-text-secondary, #5c6b63);
}

.want-card__budget strong {
  color: var(--brix-accent, #ff4d2e);
  font-size: 18px;
  font-weight: 900;
  margin-left: 2px;
}

.want-card__ops {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
}

.want-pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
</style>
