<template>
  <div class="admin-appeals">
    <div class="appeals__filter">
      <el-radio-group v-model="status" @change="reload">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">待处理</el-radio-button>
        <el-radio-button value="MAINTAINED">维持原判</el-radio-button>
        <el-radio-button value="OVERTURNED">已改判</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="rows" v-loading="loading" class="appeals__table">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="商品" min-width="180">
        <template #default="{ row }">
          <span class="appeals__item">{{ row.itemTitle || `#${row.itemId}` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="申诉人" width="150">
        <template #default="{ row }">{{ row.nickname }}（{{ row.username }}）</template>
      </el-table-column>
      <el-table-column prop="reason" label="申诉理由" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ row.statusDesc }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt, 'MM-DD HH:mm') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button size="small" :loading="reviewingId === row.id" @click="doReview(row)">AI 复核</el-button>
            <el-button size="small" type="primary" @click="openHandle(row)">处理</el-button>
          </template>
          <span v-else class="appeals__done">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="appeals__pager"
      layout="prev, pager, next"
      :total="page.total" :page-size="page.pageSize"
      :current-page="page.currentPage"
      @current-change="onPageChange"
    />

    <el-dialog v-model="handleVisible" title="处理申诉" width="560">
      <div v-if="current" class="handle__body">
        <p class="handle__item">商品：{{ current.itemTitle || `#${current.itemId}` }}</p>
        <p class="handle__reason"><strong>申诉理由：</strong>{{ current.reason }}</p>
        <div v-if="current.aiReview" class="handle__ai">
          <p class="handle__ai-title">AI 复核建议（仅供参考）</p>
          <p>{{ aiSuggestionText }}</p>
          <p class="handle__ai-reason">{{ aiReason }}</p>
        </div>
        <el-input
          v-model="handle.reply" type="textarea" :rows="3" maxlength="500" show-word-limit
          placeholder="处理回复（将展示给申诉人）"
        />
      </div>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="warning" plain :loading="handling" @click="submit('MAINTAINED')">维持原判</el-button>
        <el-button type="primary" :loading="handling" @click="submit('OVERTURNED')">改判恢复在售</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAppeals, handleAppeal, reviewAppeal } from '@/api/appeal'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const rows = ref([])
const status = ref('')
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })

const handleVisible = ref(false)
const current = ref(null)
const handling = ref(false)
const reviewingId = ref(null)
const handle = reactive({ reply: '' })

const aiParsed = computed(() => {
  const raw = current.value?.aiReview
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed?.suggestion ? parsed : null
  } catch {
    return null
  }
})
const aiSuggestionText = computed(() => {
  const p = aiParsed.value
  if (!p) return ''
  const label = { MAINTAIN: '建议维持原判', OVERTURN: '建议改判', UNCERTAIN: '材料不足难判断' }[p.suggestion] || p.suggestion
  return `${label}（置信度 ${Math.round((p.confidence || 0) * 100)}%）`
})
const aiReason = computed(() => aiParsed.value?.reason || '')

function statusType(status) {
  return { PENDING: 'warning', MAINTAINED: 'info', OVERTURNED: 'success' }[status] || 'info'
}

async function load() {
  loading.value = true
  try {
    const data = await fetchAppeals({ status: status.value || undefined, currentPage: page.currentPage, pageSize: page.pageSize })
    rows.value = data?.records || []
    page.total = Number(data?.total || 0)
  } catch (error) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function reload() {
  page.currentPage = 1
  load()
}

function onPageChange(current) {
  page.currentPage = current
  load()
}

async function doReview(row) {
  reviewingId.value = row.id
  try {
    const json = await reviewAppeal(row.id)
    row.aiReview = json
    openHandle(row)
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    reviewingId.value = null
  }
}

function openHandle(row) {
  current.value = row
  handle.reply = ''
  handleVisible.value = true
}

async function submit(action) {
  if (!handle.reply.trim()) {
    ElMessage.warning('请填写处理回复')
    return
  }
  handling.value = true
  try {
    await handleAppeal(current.value.id, { action, reply: handle.reply.trim() })
    ElMessage.success(action === 'OVERTURNED' ? '已改判，商品恢复在售' : '已维持原判')
    handleVisible.value = false
    load()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    handling.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.appeals__filter {
  margin-bottom: 14px;
}

.appeals__table {
  width: 100%;
}

.appeals__item {
  font-weight: 500;
}

.appeals__done {
  font-size: 12px;
  color: #8d99a8;
}

.appeals__pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.handle__item,
.handle__reason {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.6;
}

.handle__ai {
  margin: 10px 0;
  padding: 10px 12px;
  border: 0.5px dashed #9fe1cb;
  border-radius: 8px;
  background: #f2faf7;
  font-size: 12.5px;
  line-height: 1.7;
}

.handle__ai-title {
  margin: 0 0 4px;
  font-weight: 600;
  color: #0f6e56;
}

.handle__ai-reason {
  margin: 4px 0 0;
  color: #5f5e5a;
}
</style>
