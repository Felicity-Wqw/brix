<template>
  <div class="jury-page">
    <NavBar />
    <main class="brix-container jury-page__main">
      <!-- 页头 -->
      <div class="jury-hero">
        <span class="jury-hero__icon" aria-hidden="true">⚖️</span>
        <div class="jury-hero__text">
          <h1 class="jury-hero__title">小法庭</h1>
          <p class="jury-hero__sub">
            交易纠纷匿名公开评审：24 小时举证 → 48 小时陪审投票 → 平台终审。陪审完全自愿，每人每案仅限一票。
          </p>
        </div>
        <el-button plain @click="router.push('/')">返回首页</el-button>
      </div>

      <!-- 列表态 -->
      <template v-if="!detail">
        <div class="jury__body" v-loading="loading">
          <el-empty v-if="!loading && cases.length === 0" description="暂无进行中的案件" :image-size="90" />
          <div v-for="row in cases" :key="row.id" class="case-card" @click="openCase(row)">
            <div class="case-card__main">
              <p class="case-card__no">{{ row.caseNo }}</p>
              <p class="case-card__title">{{ row.itemTitle }}</p>
              <p class="case-card__reason">{{ row.reason }}</p>
            </div>
            <div class="case-card__side">
              <span class="state" :class="row.status === 'VOTING' ? 'state--voting' : 'state--evidence'">
                {{ row.status === 'VOTING' ? '投票中' : '举证中' }}
              </span>
              <span class="case-card__deadline">
                {{ row.status === 'VOTING' ? '投票截止 ' : '举证截止 ' }}{{ formatTime(row.status === 'VOTING' ? row.voteDeadline : row.evidenceDeadline, 'MM-DD HH:mm') }}
              </span>
              <el-button link type="primary" size="small">进入评审 →</el-button>
            </div>
          </div>
          <div class="jury__pager" v-if="page.total > page.pageSize">
            <el-pagination
              v-model:current-page="page.currentPage"
              :page-size="page.pageSize"
              :total="page.total"
              layout="prev, pager, next"
              background
              @current-change="load"
            />
          </div>
        </div>
      </template>

      <!-- 详情态 -->
      <template v-else>
        <el-button link @click="detail = null">← 返回案件列表</el-button>
        <div class="jury__detail" v-loading="detailLoading">
          <div class="detail-head">
            <p class="detail-head__no">{{ detail.caseNo }} · {{ detail.statusDesc }}</p>
            <h3 class="detail-head__title">{{ detail.itemTitle }}</h3>
            <p class="detail-head__meta">
              交易金额 ¥{{ detail.amount }} · 交易方式 {{ tradeTypeText(detail.tradeType) }} · 发起方：{{ detail.initiator === 'BUYER' ? '当事人 A（买家）' : '当事人 B（卖家）' }}
            </p>
            <p class="detail-head__reason">纠纷事由：{{ detail.reason }}</p>
            <p class="detail-head__deadline" v-if="detail.status === 'VOTING'">
              投票截止：{{ formatTime(detail.voteDeadline, 'MM-DD HH:mm') }}
            </p>
          </div>

          <div class="ai-preview" v-if="preview">
            <p class="ai-preview__head">
              <span class="ai-preview__title">AI 预审参考</span>
              <el-tag size="small" type="info" effect="plain">仅供参考，不影响计票</el-tag>
              <el-tag size="small" effect="plain" :type="preview.lean === 'NEUTRAL' ? 'info' : 'warning'">
                {{ preview.lean === 'BUYER' ? '倾向买家' : preview.lean === 'SELLER' ? '倾向卖家' : '倾向中立' }}
              </el-tag>
            </p>
            <ul class="ai-preview__points">
              <li v-for="(p, i) in preview.disputedPoints" :key="i">{{ p }}</li>
            </ul>
            <p class="ai-preview__summary"><strong>买方证据摘要：</strong>{{ preview.buyerSummary }}</p>
            <p class="ai-preview__summary"><strong>卖方证据摘要：</strong>{{ preview.sellerSummary }}</p>
            <p class="ai-preview__reason">{{ preview.leanReason }}</p>
          </div>

          <div class="evidence">
            <p class="evidence__title">双方证据（{{ detail.evidences?.length || 0 }} 条）</p>
            <div v-for="(ev, i) in detail.evidences" :key="i" class="evidence__item" :class="`evidence__item--${ev.side}`">
              <p class="evidence__side">{{ ev.sideLabel }}</p>
              <p class="evidence__content">{{ ev.content }}</p>
              <div class="evidence__images" v-if="ev.images && ev.images.length">
                <el-image
                  v-for="(img, j) in ev.images" :key="j"
                  :src="resolveImage(img)" fit="cover"
                  :preview-src-list="ev.images.map(resolveImage)"
                  :initial-index="j"
                  class="evidence__image"
                />
              </div>
            </div>
          </div>

          <div class="verdict">
            <div class="verdict__bar">
              <div class="verdict__col verdict__col--buyer">
                <span>支持买家（当事人 A）</span>
                <strong>{{ detail.voteBuyerPercent }}%</strong>
                <small>{{ detail.voteBuyer }} 票</small>
              </div>
              <div class="verdict__col verdict__col--seller">
                <span>支持卖家（当事人 B）</span>
                <strong>{{ detail.voteSellerPercent }}%</strong>
                <small>{{ detail.voteSeller }} 票</small>
              </div>
            </div>
            <p class="verdict__total">共 {{ detail.voteTotal }} 人参与陪审</p>

            <template v-if="detail.status === 'RULED'">
              <el-result
                :icon="detail.rulingResult === 'BUYER' ? 'success' : 'info'"
                :title="`平台终审：${detail.rulingResult === 'BUYER' ? '支持买家' : '支持卖家'}`"
                :sub-title="detail.rulingReason"
              />
            </template>
            <template v-else-if="detail.status === 'VOTING'">
              <div class="verdict__actions" v-if="detail.canVote">
                <el-button type="primary" @click="onVote('BUYER')">支持买家</el-button>
                <el-button type="warning" plain @click="onVote('SELLER')">支持卖家</el-button>
              </div>
              <p class="verdict__mine" v-else-if="detail.myVote">
                ✓ 你已投给「{{ detail.myVote === 'BUYER' ? '买家' : '卖家' }}」，感谢参与
              </p>
              <p class="verdict__mine" v-else>本案当事人不能投票</p>
            </template>
            <p class="verdict__mine" v-else>
              {{ detail.status === 'EVIDENCE' ? '举证中，投票尚未开始' : '陪审已结束，等待平台终审' }}
            </p>
          </div>
        </div>
      </template>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { onMounted, ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchJuryCases, fetchJuryCaseDetail, voteJuryCase, generateJuryAiPreview } from '@/api/jury'
import { formatTime, resolveImage } from '@/utils/format'
import NavBar from '@/layout/components/NavBar.vue'
import AppFooter from '@/layout/components/AppFooter.vue'

const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const cases = ref([])
const detail = ref(null)
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })

async function load() {
  loading.value = true
  try {
    const data = await fetchJuryCases({ currentPage: page.currentPage, pageSize: page.pageSize })
    cases.value = data?.records || []
    page.total = Number(data?.total || 0)
  } catch (error) {
    cases.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

async function openCase(row) {
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchJuryCaseDetail(row.id)
    // 陪审期案件缺 AI 预审时静默补触发一次（幂等；失败不提示，卡片不显示即可）
    if (detail.value?.status === 'VOTING' && !detail.value?.aiPreview) {
      try {
        const generated = await generateJuryAiPreview(row.id)
        if (generated) {
          detail.value = await fetchJuryCaseDetail(row.id)
        }
      } catch { /* 静默 */ }
    }
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    detailLoading.value = false
  }
}

/** AI 预审参考（JSON 落库，解析失败不展示卡片） */
const preview = computed(() => {
  const raw = detail.value?.aiPreview
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed?.lean ? parsed : null
  } catch {
    return null
  }
})

async function onVote(vote) {
  try {
    await voteJuryCase(detail.value.id, { vote })
    ElMessage.success('投票成功，感谢参与')
    detail.value = await fetchJuryCaseDetail(detail.value.id)
  } catch (error) {
    /* 拦截器已提示 */
  }
}

function tradeTypeText(code) {
  return { MEET: '面交', SHIP: '邮寄', BOTH: '面交/邮寄' }[code] || code
}

onMounted(load)
</script>

<style scoped>
.jury-page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.jury-page__main {
  flex: 1;
  padding: 24px 0 40px;
}

/* ---------- 页头 ---------- */
.jury-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px 26px;
  margin-bottom: 18px;
  border-radius: var(--brix-radius-lg, 16px);
  background: linear-gradient(120deg, #e7f3ec 0%, #f3f9f5 60%, #fdfdfb 100%);
  border: 1px solid #dcebe2;
}

.jury-hero__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  flex: none;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 6px 16px rgba(14, 122, 80, 0.14);
  font-size: 28px;
}

.jury-hero__text {
  flex: 1;
  min-width: 0;
}

.jury-hero__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1c2a24;
}

.jury-hero__sub {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #5c6b63;
}

/* ---------- 列表：透明容器 + 独立暖白卡片，透出全站浅绿渐变底 ---------- */
.jury__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 240px;
}

.jury__pager {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0 0;
}

.case-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-radius: var(--brix-radius-lg, 14px);
  background: var(--brix-surface, #fffdf9);
  border: 1px solid var(--brix-border, rgba(28, 42, 36, 0.08));
  box-shadow: var(--brix-shadow-sm);
  cursor: pointer;
  transition: transform 0.22s cubic-bezier(0.25, 1, 0.5, 1), box-shadow 0.22s ease, border-color 0.22s ease;
}

.case-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--brix-shadow-md);
  border-color: rgba(14, 122, 80, 0.32);
}

.case-card:hover .case-card__title {
  color: var(--brix-primary, #0e7a50);
}

.case-card__main {
  flex: 1;
  min-width: 0;
}

.case-card__no {
  margin: 0;
  font-size: 12px;
  color: #9aa5b3;
}

.case-card__title {
  margin: 4px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: #1c2a24;
}

.case-card__reason {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: #7c8894;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.case-card__side {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.case-card__deadline {
  font-size: 12px;
  color: #9aa5b3;
}

.state {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.state--voting {
  background: #fff5e6;
  color: #b8730b;
}

.state--evidence {
  background: #e8f1fd;
  color: #2b6cb0;
}

/* ---------- 详情 ---------- */
.jury__detail {
  padding: 20px 22px;
  border-radius: var(--brix-radius-lg, 14px);
  background: var(--brix-surface, #fffdf9);
  border: 1px solid var(--brix-border, rgba(28, 42, 36, 0.08));
  box-shadow: var(--brix-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-head {
  padding: 16px 18px;
  border-radius: 12px;
  background: linear-gradient(120deg, #e7f3ec 0%, #f5faf7 70%, #fdfdfb 100%);
  border: 1px solid #dcebe2;
}

.detail-head__no {
  margin: 0;
  font-size: 12px;
  color: #6f8478;
}

.detail-head__title {
  margin: 4px 0 0;
  font-size: 16px;
  color: #1c2a24;
}

.detail-head__meta,
.detail-head__reason,
.detail-head__deadline {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: #7c8894;
  line-height: 1.6;
}

.evidence__title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1c2a24;
}

.evidence__item {
  padding: 12px 14px;
  border-radius: 10px;
  margin-bottom: 8px;
}

.evidence__item--BUYER {
  background: #f4f9f6;
  border: 1px solid #e2eee8;
}

.evidence__item--SELLER {
  background: #fdf7f1;
  border: 1px solid #f2e8dc;
}

.evidence__side {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: #5c6b63;
}

.evidence__content {
  margin: 4px 0 0;
  font-size: 13px;
  color: #33413a;
  line-height: 1.7;
  white-space: pre-wrap;
}

.evidence__images {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.evidence__image {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  background: #f4f6f8;
}

.verdict {
  border-top: 1px solid #eef1f5;
  padding-top: 14px;
}

.verdict__bar {
  display: flex;
  gap: 12px;
}

.verdict__col {
  flex: 1;
  padding: 14px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  font-size: 12.5px;
  color: #5c6b63;
}

.verdict__col--buyer {
  background: #f4f9f6;
}

.verdict__col--seller {
  background: #fdf7f1;
}

.verdict__col strong {
  font-size: 22px;
  color: #1c2a24;
}

.verdict__total {
  margin: 8px 0 0;
  text-align: center;
  font-size: 12px;
  color: #9aa5b3;
}

.verdict__actions {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.verdict__mine {
  margin: 12px 0 0;
  text-align: center;
  font-size: 12.5px;
  color: #8d99a8;
}

.ai-preview {
  margin: 14px 0;
  padding: 12px 14px;
  border: 0.5px dashed #9fe1cb;
  border-radius: 10px;
  background: #f2faf7;
}

.ai-preview__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
}

.ai-preview__title {
  font-weight: 600;
  font-size: 13px;
  color: #0f6e56;
}

.ai-preview__points {
  margin: 0 0 8px;
  padding-left: 16px;
  list-style: decimal;
  font-size: 13px;
  line-height: 1.7;
  color: #2c2c2a;
}

.ai-preview__summary {
  margin: 4px 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: #444441;
}

.ai-preview__reason {
  margin: 8px 0 0;
  font-size: 12px;
  color: #5f5e5a;
}
</style>
