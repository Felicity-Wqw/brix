<template>
  <div class="comment-list brix-card">
    <div class="brix-section-title">
      <span>留言问答（{{ total }}）</span>
      <span class="brix-section-title__sub">仅支持一层回复，请文明交流</span>
    </div>

    <div v-if="isLogin" class="comment-list__editor">
      <el-input
        v-model="content"
        type="textarea"
        :rows="2"
        maxlength="500"
        show-word-limit
        placeholder="想问点什么？比如成色、配件、能否面交…"
      />
      <div class="comment-list__editor-actions">
        <el-button type="primary" size="small" :loading="submitting" @click="submit(null)">发表留言</el-button>
      </div>
    </div>
    <el-alert v-else type="info" :closable="false" show-icon title="登录后即可留言提问" class="brix-mb-16" />

    <div v-loading="loading" class="comment-list__body">
      <template v-if="comments.length">
        <div v-for="comment in comments" :key="comment.id" class="comment-item">
          <el-avatar :size="32" :src="resolveImage(comment.userAvatar)">
            {{ nicknameInitial(comment.userNickname) }}
          </el-avatar>
          <div class="comment-item__main">
            <div class="comment-item__head">
              <span class="comment-item__name">{{ comment.userNickname || '匿名同学' }}</span>
              <span class="comment-item__time">{{ formatRelative(comment.createdAt) }}</span>
            </div>
            <p class="comment-item__content">{{ comment.content }}</p>
            <div class="comment-item__actions">
              <el-button v-if="isLogin" text type="primary" size="small" @click="startReply(comment)">
                回复
              </el-button>
              <el-button
                v-if="comment.canDelete"
                text
                type="danger"
                size="small"
                @click="remove(comment.id)"
              >
                删除
              </el-button>
            </div>

            <div v-if="replyTarget === comment.id" class="comment-item__reply-editor">
              <el-input v-model="replyContent" size="small" maxlength="500" placeholder="回复这条留言…" />
              <el-button type="primary" size="small" :loading="submitting" @click="submit(comment.id)">发送</el-button>
              <el-button text size="small" @click="cancelReply">取消</el-button>
            </div>

            <div v-if="comment.replies?.length" class="comment-item__replies">
              <div v-for="reply in comment.replies" :key="reply.id" class="comment-reply">
                <span class="comment-reply__name">{{ reply.userNickname || '匿名同学' }}：</span>
                <span class="comment-reply__content">{{ reply.content }}</span>
                <span class="comment-reply__time">{{ formatRelative(reply.createdAt) }}</span>
                <el-button v-if="reply.canDelete" text type="danger" size="small" @click="remove(reply.id)">
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </template>
      <EmptyState v-else-if="!loading" text="还没有留言，来问第一个问题吧" />
    </div>

    <el-pagination
      v-if="total > pageSize"
      layout="prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="currentPage"
      @current-change="onPageChange"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createComment, deleteComment, fetchCommentUnreadSummary, fetchComments } from '@/api/comment'
import { useAuthStore } from '@/stores/auth'
import { formatRelative, resolveImage, nicknameInitial } from '@/utils/format'
import EmptyState from './EmptyState.vue'

const props = defineProps({
  itemId: { type: [String, Number], required: true }
})

const emit = defineEmits(['changed'])

const auth = useAuthStore()
const isLogin = computed(() => auth.isLogin)

const comments = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const submitting = ref(false)
const content = ref('')
const replyTarget = ref(null)
const replyContent = ref('')

/** 加载留言 */
async function load() {
  loading.value = true
  try {
    const data = await fetchComments(props.itemId, { currentPage: currentPage.value, pageSize: pageSize.value })
    comments.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
    // 卖家查看自己商品时后端会把留言标记已读，这里同步刷新导航红点，避免再等 60 秒轮询
    refreshUnreadBadge()
  }
}

/** 同步导航「留言」未读数（失败不影响留言浏览） */
async function refreshUnreadBadge() {
  if (!auth.isLogin) return
  try {
    const summary = await fetchCommentUnreadSummary()
    auth.setCommentUnread(summary?.count ?? 0)
  } catch (error) {
    // 静默失败，交给 NavBar 的轮询兜底
  }
}

/** 翻页 */
function onPageChange(page) {
  currentPage.value = page
  load()
}

/** 进入回复态 */
function startReply(comment) {
  replyTarget.value = comment.id
  replyContent.value = ''
}

/** 取消回复 */
function cancelReply() {
  replyTarget.value = null
  replyContent.value = ''
}

/** 发表留言或回复 */
async function submit(parentId) {
  const text = (parentId ? replyContent.value : content.value).trim()
  if (!text) {
    ElMessage.warning('内容不能为空')
    return
  }
  submitting.value = true
  try {
    await createComment(props.itemId, { content: text, parentId })
    if (parentId) {
      replyContent.value = ''
      replyTarget.value = null
    } else {
      content.value = ''
    }
    ElMessage.success('留言成功')
    await load()
    emit('changed')
  } finally {
    submitting.value = false
  }
}

/** 删除留言 */
async function remove(id) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定删除这条留言吗？', '删除留言', { type: 'warning' })
  } catch (error) {
    return
  }
  await deleteComment(id)
  ElMessage.success('已删除')
  if (comments.value.length === 1 && currentPage.value > 1) {
    currentPage.value -= 1
  }
  await load()
  emit('changed')
}

onMounted(load)
</script>

<style scoped>
.comment-list {
  padding: 16px;
}

.comment-list__editor-actions {
  display: flex;
  justify-content: flex-end;
  margin: 8px 0 4px;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 14px 0;
  border-top: 0.5px solid var(--brix-border);
}

.comment-item__main {
  flex: 1;
  min-width: 0;
}

.comment-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-item__name {
  font-weight: 600;
  font-size: 13px;
}

.comment-item__time {
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.comment-item__content {
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.comment-item__actions :deep(.el-button) {
  padding: 2px 0;
}

.comment-item__reply-editor {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.comment-item__replies {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f7f8fa;
  border-radius: 8px;
}

.comment-reply {
  font-size: 13px;
  line-height: 1.8;
  color: var(--brix-text-secondary);
}

.comment-reply__name {
  color: var(--brix-primary);
}

.comment-reply__content {
  color: var(--brix-text);
}

.comment-reply__time {
  margin-left: 8px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}
</style>
