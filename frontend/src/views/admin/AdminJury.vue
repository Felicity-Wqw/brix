<template>
  <div class="admin-jury">
    <!-- 列表态 -->
    <template v-if="!detail">
      <div class="admin-jury__bar">
        <span class="admin-jury__note">小法庭陪审期满的案件在此终审：请核对双方证据与用户投票，裁决后立即生效并通知双方</span>
      </div>
      <div class="admin-jury__body" v-loading="loading">
        <el-empty v-if="!loading && rows.length === 0" description="暂无待裁决案件" :image-size="90" />
        <el-table :data="rows" size="default" empty-text="暂无待裁决案件">
          <el-table-column label="案件" min-width="260">
            <template #default="{ row }">
              <div class="cell-item">
                <el-image :src="resolveImage(row.itemCover)" fit="cover" class="cell-item__cover">
                  <template #error>
                    <div class="cell-item__placeholder">无图</div>
                  </template>
                </el-image>
                <div class="cell-item__text">
                  <p class="cell-item__title">{{ row.caseNo }} · {{ row.itemTitle }}</p>
                  <p class="cell-item__meta">¥{{ row.amount }} · 发起方：{{ row.initiator === 'BUYER' ? '买家' : '卖家' }}</p>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="买家 / 卖家" min-width="170">
            <template #default="{ row }">
              <p class="cell-users">买家：{{ row.buyerNickname }}（{{ row.buyerUsername }}）</p>
              <p class="cell-users">卖家：{{ row.sellerNickname }}（{{ row.sellerUsername }}）</p>
            </template>
          </el-table-column>
          <el-table-column label="陪审投票" width="180">
            <template #default="{ row }">
              <p class="cell-vote">支持买家 {{ row.voteBuyerPercent }}%（{{ row.voteBuyer }} 票）</p>
              <p class="cell-vote">支持卖家 {{ row.voteSellerPercent }}%（{{ row.voteSeller }} 票）</p>
              <p class="cell-vote-sub">共 {{ row.voteTotal }} 人参与</p>
            </template>
          </el-table-column>
          <el-table-column label="陪审截止" width="112">
            <template #default="{ row }">
              <span class="cell-time">{{ formatTime(row.voteDeadline, 'MM-DD HH:mm') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="openDetail(row)">查看并裁决</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="admin-jury__pager" v-if="page.total > page.pageSize">
          <el-pagination
            v-model:current-page="page.currentPage"
            :page-size="page.pageSize"
            :total="page.total"
            layout="total, prev, pager, next"
            background
            @current-change="load"
          />
        </div>
      </div>
    </template>

    <!-- 详情/裁决态 -->
    <template v-else>
      <el-button link @click="detail = null">← 返回待裁决列表</el-button>
      <div class="admin-jury__detail" v-loading="detailLoading">
        <div class="detail-head">
          <p class="detail-head__no">{{ detail.caseNo }} · 待平台裁决</p>
          <h3 class="detail-head__title">{{ detail.itemTitle }}（¥{{ detail.amount }}）</h3>
          <p class="detail-head__users">
            买家：{{ detail.buyerNickname }}（{{ detail.buyerUsername }} · {{ detail.buyerSchool }}）
            &nbsp;vs&nbsp;
            卖家：{{ detail.sellerNickname }}（{{ detail.sellerUsername }} · {{ detail.sellerSchool }}）
          </p>
          <p class="detail-head__reason">纠纷事由（{{ detail.initiator === 'BUYER' ? '买家' : '卖家' }}发起）：{{ detail.reason }}</p>
        </div>

        <div class="evidence">
          <p class="evidence__title">双方证据（{{ detail.evidences?.length || 0 }} 条）</p>
          <div v-for="(ev, i) in detail.evidences" :key="i" class="evidence__item">
            <p class="evidence__side">
              {{ ev.side === 'BUYER' ? `当事人 A（买家 · ${detail.buyerNickname}）` : `当事人 B（卖家 · ${detail.sellerNickname}）` }}
            </p>
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
            <div class="verdict__col">
              <span>支持买家</span>
              <strong>{{ detail.voteBuyerPercent }}%</strong>
              <small>{{ detail.voteBuyer }} 票</small>
            </div>
            <div class="verdict__col">
              <span>支持卖家</span>
              <strong>{{ detail.voteSellerPercent }}%</strong>
              <small>{{ detail.voteSeller }} 票</small>
            </div>
            <div class="verdict__col">
              <span>参与人数</span>
              <strong>{{ detail.voteTotal }}</strong>
              <small>陪审期 48 小时</small>
            </div>
          </div>

          <el-input
            v-model="ruling.reason"
            type="textarea" :rows="3" maxlength="300" show-word-limit
            placeholder="裁决理由（必填，将随结果通知买卖双方）"
            style="margin-top: 14px"
          />
          <div class="verdict__actions">
            <el-button type="primary" :loading="ruling.loading" @click="confirmRule('BUYER')">裁决支持买家（执行退款）</el-button>
            <el-button type="warning" plain :loading="ruling.loading" @click="confirmRule('SELLER')">裁决支持卖家（关闭售后）</el-button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPendingJuryCases, fetchAdminJuryCaseDetail, ruleJuryCase } from '@/api/jury'
import { formatTime, resolveImage } from '@/utils/format'

const loading = ref(false)
const detailLoading = ref(false)
const rows = ref([])
const detail = ref(null)
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })
const ruling = reactive({ reason: '', loading: false })

async function load() {
  loading.value = true
  try {
    const data = await fetchPendingJuryCases({ currentPage: page.currentPage, pageSize: page.pageSize })
    rows.value = data?.records || []
    page.total = Number(data?.total || 0)
  } catch (error) {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detailLoading.value = true
  ruling.reason = ''
  detail.value = null
  try {
    detail.value = await fetchAdminJuryCaseDetail(row.id)
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    detailLoading.value = false
  }
}

async function confirmRule(result) {
  if (!ruling.reason.trim()) {
    ElMessage.warning('请填写裁决理由')
    return
  }
  ruling.loading = true
  try {
    await ruleJuryCase(detail.value.id, { result, reason: ruling.reason.trim() })
    ElMessage.success('裁决已提交并通知双方')
    detail.value = null
    load()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    ruling.loading = false
  }
}

onMounted(load)
</script>

<style scoped>
.admin-jury {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-jury__bar {
  display: flex;
  justify-content: flex-end;
}

.admin-jury__note {
  font-size: 12px;
  color: #9aa5b3;
}

.admin-jury__body {
  padding: 14px 16px 8px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
}

.admin-jury__pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}

.admin-jury__detail {
  padding: 14px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cell-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cell-item__cover {
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: 8px;
  background: #f4f6f8;
}

.cell-item__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #c0c8d2;
  font-size: 12px;
}

.cell-item__text {
  min-width: 0;
}

.cell-item__title {
  margin: 0;
  font-size: 13.5px;
  color: #1c2a24;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-item__meta {
  margin: 3px 0 0;
  font-size: 12px;
  color: #8d99a8;
}

.cell-users,
.cell-vote {
  margin: 0;
  font-size: 12.5px;
  color: #5c6b63;
  line-height: 1.7;
}

.cell-vote-sub {
  margin: 0;
  font-size: 11.5px;
  color: #9aa5b3;
}

.cell-time {
  font-size: 12.5px;
  color: #8d99a8;
}

.detail-head__no {
  margin: 0;
  font-size: 12px;
  color: #9aa5b3;
}

.detail-head__title {
  margin: 4px 0 0;
  font-size: 15px;
  color: #1c2a24;
}

.detail-head__users,
.detail-head__reason {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: #5c6b63;
  line-height: 1.7;
}

.evidence__title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1c2a24;
}

.evidence__item {
  padding: 10px 12px;
  border-radius: 10px;
  margin-bottom: 8px;
  background: #f7f9fa;
  border: 1px solid #eef1f5;
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
  padding: 12px;
  border-radius: 10px;
  background: #f7f9fa;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  font-size: 12.5px;
  color: #5c6b63;
}

.verdict__col strong {
  font-size: 20px;
  color: #1c2a24;
}

.verdict__actions {
  margin-top: 14px;
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
