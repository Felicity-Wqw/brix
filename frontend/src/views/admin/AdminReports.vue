<template>
  <div class="reports">
    <div class="reports__bar">
      <el-tabs v-model="activeStatus" class="reports__tabs" @tab-change="reload">
        <el-tab-pane label="待处理" name="PENDING" />
        <el-tab-pane label="已处理" name="HANDLED" />
        <el-tab-pane label="全部" name="all" />
      </el-tabs>
      <span class="reports__note">举报成立即下架商品；不成立则只关闭举报，商品不受影响</span>
    </div>

    <div class="reports__body" v-loading="loading">
      <el-table :data="rows" size="default" empty-text="暂无举报记录">
        <el-table-column label="被举报商品" min-width="230">
          <template #default="{ row }">
            <div class="cell-item">
              <el-image :src="resolveImage(row.itemCover)" fit="cover" class="cell-item__cover">
                <template #error>
                  <div class="cell-item__placeholder"><el-icon><Picture /></el-icon></div>
                </template>
              </el-image>
              <div class="cell-item__text">
                <p class="cell-item__title" :title="row.itemTitle">{{ row.itemTitle || '商品已删除' }}</p>
                <p class="cell-item__meta">举报人：{{ row.reporterNickname || '—' }}</p>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="举报原因" width="110">
          <template #default="{ row }">
            <span class="cell-reason-tag">{{ row.reasonDesc }}</span>
          </template>
        </el-table-column>

        <el-table-column label="补充说明" min-width="190">
          <template #default="{ row }">
            <span class="cell-desc" :title="row.description">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <span class="state" :class="row.status === 'HANDLED' ? 'state--done' : 'state--pending'">
              {{ row.statusDesc }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="处理结果" width="136">
          <template #default="{ row }">
            <template v-if="row.status === 'HANDLED'">
              <p class="cell-handle">{{ row.handleActionDesc }}</p>
              <p class="cell-handle-sub">{{ row.handlerNickname }} · {{ formatTime(row.handledAt, 'MM-DD HH:mm') }}</p>
            </template>
            <span v-else class="cell-handle-sub">—</span>
          </template>
        </el-table-column>

        <el-table-column label="举报时间" width="112">
          <template #default="{ row }">
            <span class="cell-time">{{ formatTime(row.createdAt, 'MM-DD HH:mm') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="164" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link size="small" class="op-offline" @click="openHandle(row, 'OFFLINE')">成立并下架</el-button>
              <el-button link size="small" @click="openHandle(row, 'REJECT')">驳回举报</el-button>
            </template>
            <span v-else class="cell-handle-sub">{{ row.handleRemark || '已处理' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="reports__pager">
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

    <el-dialog v-model="handle.visible" :title="handle.title" width="440px">
      <p class="handle__hint">{{ handle.hint }}</p>
      <el-input
        v-model="handle.remark"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="处理备注（会留存在举报记录里，便于复盘）"
      />
      <template #footer>
        <el-button @click="handle.visible = false">取消</el-button>
        <el-button :type="handle.action === 'OFFLINE' ? 'danger' : 'primary'" :loading="handle.loading" @click="confirm">
          {{ handle.action === 'OFFLINE' ? '确认下架' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { fetchAdminReports, handleReport } from '@/api/admin'
import { formatTime, resolveImage } from '@/utils/format'

const activeStatus = ref('PENDING')
const loading = ref(false)
const rows = ref([])
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })
const handle = reactive({
  visible: false,
  loading: false,
  action: '',
  remark: '',
  target: null,
  title: '',
  hint: ''
})

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminReports({
      status: activeStatus.value === 'all' ? '' : activeStatus.value,
      currentPage: page.currentPage,
      pageSize: page.pageSize
    })
    rows.value = data?.records || []
    page.total = Number(data?.total || 0)
  } catch (error) {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  page.currentPage = 1
  load()
}

function openHandle(row, action) {
  handle.target = row
  handle.action = action
  handle.remark = ''
  handle.visible = true
  if (action === 'OFFLINE') {
    handle.title = '举报成立 · 下架商品'
    handle.hint = '商品会被置为「已下架」，卖家可在「我的商品」看到下架原因。'
  } else {
    handle.title = '举报不成立 · 驳回'
    handle.hint = '仅关闭这条举报，商品保持原状态；请确认举报内容确实不成立。'
  }
}

async function confirm() {
  handle.loading = true
  try {
    await handleReport(handle.target.id, { action: handle.action, remark: handle.remark.trim() })
    ElMessage.success('举报已处理')
    handle.visible = false
    load()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    handle.loading = false
  }
}

onMounted(load)
</script>

<style scoped>
.reports {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reports__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reports__tabs {
  flex: 1;
  min-width: 0;
}

.reports__tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.reports__note {
  font-size: 12px;
  color: #9aa5b3;
}

.reports__body {
  padding: 14px 16px 8px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
}

.reports__pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
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

.cell-reason-tag {
  padding: 1px 9px;
  border-radius: 999px;
  background: #fdece8;
  color: #c9411f;
  font-size: 12px;
}

.cell-desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12.5px;
  color: #5c6b63;
  line-height: 1.6;
}

.cell-handle {
  margin: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: #1c2a24;
}

.cell-handle-sub {
  margin: 0;
  font-size: 11.5px;
  color: #9aa5b3;
}

.cell-time {
  font-size: 12.5px;
  color: #8d99a8;
}

.state {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.state--pending {
  background: #fff5e6;
  color: #b8730b;
}

.state--done {
  background: #f0f3f5;
  color: #5c6b63;
}

.op-offline {
  color: #c9411f;
}

.handle__hint {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #8d99a8;
}
</style>
