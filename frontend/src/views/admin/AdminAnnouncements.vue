<template>
  <div class="ann">
    <div class="ann__toolbar">
      <span class="ann__hint">首页右侧「平台通知」卡片的轮播文字，按排序号从小到大轮播，仅上架的会展示。</span>
      <el-button type="primary" @click="openDlg()">新增公告</el-button>
    </div>

    <div class="ann__body" v-loading="loading">
      <el-empty v-if="!loading && rows.length === 0" description="还没有公告，新增一条吧" :image-size="90" />
      <div v-for="row in rows" :key="row.id" class="ann-item">
        <div class="ann-item__main">
          <p class="ann-item__title">
            {{ row.title }}
            <span class="ann-item__state" :class="row.enabled ? 'ann-item__state--on' : 'ann-item__state--off'">
              {{ row.enabled ? '已上架' : '已下架' }}
            </span>
          </p>
          <p class="ann-item__content">{{ row.content }}</p>
          <p class="ann-item__meta">排序 {{ row.sort }} · 更新于 {{ formatTime(row.updatedAt, 'YYYY-MM-DD HH:mm') }}</p>
        </div>
        <div class="ann-item__ops">
          <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="onToggle(row)" />
          <el-button link size="small" @click="openDlg(row)">编辑</el-button>
          <el-button link size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dlg.visible" :title="dlg.id ? '编辑公告' : '新增公告'" width="480px">
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="dlg.title" maxlength="60" show-word-limit placeholder="如：开学季活动" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="dlg.content" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="首页轮播展示的文字，建议一句话说清楚" />
        </el-form-item>
        <el-form-item label="排序（越小越靠前）">
          <el-input-number v-model="dlg.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="是否上架">
          <el-switch v-model="dlg.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.loading" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAdminAnnouncements, createAnnouncement, updateAnnouncement, deleteAnnouncement
} from '@/api/admin'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const rows = ref([])

const dlg = reactive({
  visible: false, loading: false, id: null,
  title: '', content: '', enabled: 1, sort: 0
})

async function load() {
  loading.value = true
  try {
    rows.value = (await fetchAdminAnnouncements()) || []
  } catch (error) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function openDlg(row) {
  dlg.id = row?.id || null
  dlg.title = row?.title || ''
  dlg.content = row?.content || ''
  dlg.enabled = row?.enabled ?? 1
  dlg.sort = row?.sort ?? 0
  dlg.visible = true
}

async function submit() {
  if (!dlg.title.trim() || !dlg.content.trim()) {
    ElMessage.warning('标题和内容都要填')
    return
  }
  dlg.loading = true
  try {
    const payload = { title: dlg.title.trim(), content: dlg.content.trim(), enabled: dlg.enabled, sort: dlg.sort }
    if (dlg.id) {
      await updateAnnouncement(dlg.id, payload)
    } else {
      await createAnnouncement(payload)
    }
    ElMessage.success('已保存')
    dlg.visible = false
    load()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    dlg.loading = false
  }
}

async function onToggle(row) {
  try {
    await updateAnnouncement(row.id, { title: row.title, content: row.content, enabled: row.enabled, sort: row.sort })
    ElMessage.success(row.enabled ? '已上架，首页轮播会展示' : '已下架，首页不再展示')
  } catch (error) {
    load()
  }
}

function onDelete(row) {
  ElMessageBox.confirm(`删除后首页轮播不再展示「${row.title}」。确认删除？`, '删除公告', { type: 'warning' })
    .then(async () => {
      await deleteAnnouncement(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.ann {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ann__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ann__hint {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--brix-text-secondary, #5c6b63);
}

.ann__body {
  padding: 6px 18px;
  border-radius: 12px;
  background: var(--brix-surface, #fffdf9);
  border: 1px solid var(--brix-border, rgba(28, 42, 36, 0.08));
  min-height: 220px;
}

.ann-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 4px;
  border-bottom: 1px dashed var(--brix-border, rgba(28, 42, 36, 0.08));
}

.ann-item:last-of-type {
  border-bottom: none;
}

.ann-item__main {
  flex: 1;
  min-width: 0;
}

.ann-item__title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 700;
  color: #1c2a24;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ann-item__state {
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.ann-item__state--on {
  background: var(--brix-primary-soft, #e7f3ec);
  color: var(--brix-primary, #0e7a50);
}

.ann-item__state--off {
  background: #f0f3f5;
  color: #5c6b63;
}

.ann-item__content {
  margin: 4px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--brix-text-secondary, #5c6b63);
}

.ann-item__meta {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: var(--brix-text-placeholder, #96a29b);
}

.ann-item__ops {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
