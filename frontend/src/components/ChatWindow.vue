<template>
  <div class="chat-window">
    <template v-if="peerId">
      <div class="chat-window__head">
        <div class="chat-window__peer">
          <el-avatar :size="34" :src="resolveImage(peer?.peerAvatar)">
            {{ nicknameInitial(peer?.peerNickname) }}
          </el-avatar>
          <div class="chat-window__peer-text">
            <span class="chat-window__name">{{ peer?.peerNickname || '对话' }}</span>
            <span class="chat-window__conn" :class="connected ? 'is-on' : 'is-off'">
              {{ connected ? '在线' : '重连中…' }}
            </span>
          </div>
        </div>
      </div>

      <div ref="bodyRef" class="chat-window__body" @scroll="onScroll">
        <div v-if="hasMore" class="chat-window__more">
          <el-button text size="small" :loading="loading" @click="loadOlder">查看更早的消息</el-button>
        </div>

        <template v-for="(message, index) in messages" :key="message.id ?? index">
          <div v-if="showDateDivider(index)" class="chat-date-divider">
            <span>{{ dateDividerText(message.createdAt) }}</span>
          </div>
          <div
            :class="['chat-message', Number(message.fromUserId) === Number(myId) ? 'is-mine' : 'is-peer']"
          >
            <el-avatar :size="28" :src="resolveImage(myId === Number(message.fromUserId) ? myAvatar : peer?.peerAvatar)">
              {{ nicknameInitial(myId === Number(message.fromUserId) ? myNickname : peer?.peerNickname) }}
            </el-avatar>
            <div class="chat-message__bubble">
              <p class="chat-message__content">{{ message.content }}</p>
              <span class="chat-message__time">{{ formatTime(message.createdAt, 'HH:mm') }}</span>
            </div>
          </div>
        </template>

        <div v-if="!messages.length && !loading" class="chat-window__empty">
          和 {{ peer?.peerNickname || '对方' }} 打个招呼吧～
        </div>
      </div>

      <div class="chat-window__input">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          maxlength="500"
          resize="none"
          placeholder="点击输入文字"
          @keydown.enter.exact.prevent="send"
        />
        <button
          type="button"
          class="chat-window__send"
          :disabled="!draft.trim() || sending"
          @click="send"
        >
          <el-icon><Promotion /></el-icon>
        </button>
      </div>
      <div class="chat-window__hint">回车发送 · Shift + 回车换行 · {{ draft.length }}/500</div>
    </template>

    <div v-else class="chat-window__placeholder">
      <el-icon class="chat-window__placeholder-icon"><ChatDotRound /></el-icon>
      <p>选择左侧会话开始聊天</p>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { fetchMessages, markRead, sendMessage } from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import { formatTime, resolveImage, nicknameInitial } from '@/utils/format'
import ws from '@/utils/ws'

const props = defineProps({
  peerId: { type: [String, Number], default: '' },
  peer: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['received'])

const auth = useAuthStore()
const myId = Number(auth.user?.id || 0)
const myNickname = auth.user?.nickname || ''
const myAvatar = auth.user?.avatar || ''

const messages = ref([])
const draft = ref('')
const loading = ref(false)
const sending = ref(false)
const hasMore = ref(false)
const currentPage = ref(1)
const pageSize = 20
const bodyRef = ref(null)
const connected = ref(false)
let unsubscribe = null

/** 是否需要插入日期分隔线（与上一条消息不同天时） */
function showDateDivider(index) {
  if (index === 0) return true
  const prev = dayjs(messages.value[index - 1].createdAt).format('YYYY-MM-DD')
  const current = dayjs(messages.value[index].createdAt).format('YYYY-MM-DD')
  return prev !== current
}

/** 日期分隔文案：今天 / 昨天 / 具体日期 */
function dateDividerText(value) {
  const d = dayjs(value)
  const today = dayjs().format('YYYY-MM-DD')
  const yesterday = dayjs().subtract(1, 'day').format('YYYY-MM-DD')
  const day = d.format('YYYY-MM-DD')
  if (day === today) return '今天'
  if (day === yesterday) return '昨天'
  return d.format('YYYY年M月D日')
}

/** 拉取历史消息 */
async function load(reset = true) {
  if (!props.peerId) return
  loading.value = true
  try {
    const data = await fetchMessages({ peerId: props.peerId, currentPage: currentPage.value, pageSize })
    const records = data?.records || []
    hasMore.value = Number(data?.total || 0) > currentPage.value * pageSize
    if (reset) {
      messages.value = records
    } else {
      messages.value = [...records, ...messages.value]
    }
    await nextTick()
    scrollToBottom(reset)
  } finally {
    loading.value = false
  }
}

/** 加载更早一页 */
function loadOlder() {
  currentPage.value += 1
  load(false)
}

/** 滚动到底部 */
function scrollToBottom(instant = true) {
  const el = bodyRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

/** 滚动到顶部加载更多时保持视口位置 */
function onScroll(event) {
  if (event.target.scrollTop <= 4 && hasMore.value && !loading.value) {
    const before = event.target.scrollHeight
    loadOlder().finally(() => {
      nextTick(() => {
        if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight - before
      })
    })
  }
}

/** 发送消息：优先 WebSocket，未连接回落 HTTP 兜底通道 */
async function send() {
  const content = draft.value.trim()
  if (!content) return
  if (content.length > 500) {
    ElMessage.warning('消息不能超过 500 字')
    return
  }
  sending.value = true
  try {
    // 本地临时 id：带随机后缀，避免同一毫秒连发时撞 id（BUG-A）
    const tmpId = `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const sentViaWs = ws.sendChat(
      { toUserId: String(props.peerId), content },
      tmpId
    )
    if (!sentViaWs) {
      // HTTP 兜底：接口返回落库后的消息，只 append 一次
      const saved = await sendMessage({ toUserId: props.peerId, content })
      appendLocal(saved)
    } else {
      // 本地乐观插入，服务端回执到达后按 clientMsgId 原地替换，不再二次 append
      appendLocal({
        id: tmpId,
        fromUserId: String(myId),
        toUserId: String(props.peerId),
        content,
        createdAt: new Date().toISOString()
      })
    }
    draft.value = ''
    await nextTick()
    scrollToBottom()
  } finally {
    sending.value = false
  }
}

/** 本地追加一条消息（按 id 去重） */
function appendLocal(message) {
  const exists = messages.value.some(
    (item) => String(item.id) === String(message.id) && message.id !== undefined
  )
  if (exists) return
  messages.value.push(message)
  emit('received', message)
}

/**
 * 按 clientMsgId 把乐观气泡替换为服务端落库后的真实气泡。
 *
 * @param {Record<string, any>} frame 服务端回执帧
 * @returns {boolean} 是否命中并完成替换（未命中说明是其它标签页发出的消息）
 */
function replaceOptimistic(frame) {
  const clientMsgId = frame.clientMsgId
  if (!clientMsgId) return false
  const target = messages.value.find((item) => String(item.id) === String(clientMsgId))
  if (!target) return false
  target.id = frame.messageId
  target.createdAt = frame.timestamp
  if (frame.content) target.content = frame.content
  return true
}

/** 处理 WS 帧 */
function handleFrame(frame) {
  if (frame.type === 'OPEN') {
    connected.value = true
    return
  }
  if (frame.type === 'CLOSE') {
    connected.value = false
    return
  }
  if (frame.type !== 'CHAT') return
  const fromSelf = Number(frame.fromUserId) === myId
  const fromPeer = String(frame.fromUserId) === String(props.peerId)

  if (fromSelf) {
    // 发送方回执：先尝试原地替换乐观气泡（不发 received，避免会话列表未读重复 +1）；
    // 未命中说明是其它标签页/其它入口发出的，才补一条
    if (replaceOptimistic(frame)) {
      await_scroll()
      return
    }
    if (String(frame.toUserId) === String(props.peerId)) {
      appendLocal({
        id: frame.messageId,
        fromUserId: frame.fromUserId,
        toUserId: frame.toUserId,
        content: frame.content,
        createdAt: frame.timestamp
      })
      await_scroll()
    }
    return
  }

  if (fromPeer) {
    appendLocal({
      id: frame.messageId,
      fromUserId: frame.fromUserId,
      toUserId: frame.toUserId,
      content: frame.content,
      createdAt: frame.timestamp
    })
    await_scroll()
  }
}

async function await_scroll() {
  await nextTick()
  scrollToBottom()
}

watch(
  () => props.peerId,
  async () => {
    currentPage.value = 1
    hasMore.value = false
    messages.value = []
    if (!props.peerId) return
    await load()
    try {
      const data = await markRead(props.peerId)
      auth.setUnread(data?.count ?? 0)
    } catch (error) {
      // 已读失败不阻断聊天
    }
  }
)

onMounted(() => {
  unsubscribe = ws.onMessage(handleFrame)
  connected.value = ws.isOpen()
  if (props.peerId) {
    load()
    markRead(props.peerId)
      .then((data) => auth.setUnread(data?.count ?? 0))
      .catch(() => {})
  }
})

onBeforeUnmount(() => {
  if (unsubscribe) unsubscribe()
})
</script>

<style scoped>
/* iMessage 风格：纯白界面 / 无边框圆角气泡 / 胶囊输入区 */
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}

.chat-window__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 0.5px solid var(--brix-border);
  background: rgba(255, 253, 249, 0.66);
  backdrop-filter: blur(14px) saturate(1.1);
  -webkit-backdrop-filter: blur(14px) saturate(1.1);
}

.chat-window__peer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-window__peer-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.chat-window__name {
  font-weight: 600;
  font-size: 15px;
}

.chat-window__conn {
  font-size: 11px;
}

.chat-window__conn.is-on {
  color: var(--brix-primary);
}

.chat-window__conn.is-off {
  color: #b25e00;
}

.chat-window__body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 16px;
  background: #fff;
  /* iMessage 式隐藏滚动条，滚轮/触控板滚动不受影响 */
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.chat-window__body::-webkit-scrollbar {
  display: none;
}

.chat-window__more {
  text-align: center;
  margin-bottom: 8px;
}

/* 日期分隔：iMessage 居中灰色胶囊 */
.chat-date-divider {
  display: flex;
  justify-content: center;
  margin: 14px 0 18px;
}

.chat-date-divider span {
  padding: 3px 12px;
  border-radius: 999px;
  background: rgba(28, 42, 36, 0.05);
  color: var(--brix-text-secondary);
  font-size: 12px;
}

.chat-window__empty {
  text-align: center;
  color: var(--brix-text-placeholder);
  font-size: 13px;
  padding-top: 120px;
}

/* 气泡：无边框纯色大圆角，靠头像一侧收角 */
.chat-message {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.chat-message.is-mine {
  flex-direction: row-reverse;
}

.chat-message__bubble {
  max-width: 62%;
  padding: 9px 14px 7px;
  border-radius: 18px;
  border-bottom-left-radius: 6px;
  background: #e9e9eb;
  color: #1d1d1f;
}

.chat-message.is-mine .chat-message__bubble {
  background: var(--brix-primary);
  color: #fff;
  border-bottom-left-radius: 18px;
  border-bottom-right-radius: 6px;
}

.chat-message__content {
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
  white-space: pre-wrap;
}

.chat-message__time {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  opacity: 0.55;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* 输入区：iMessage 胶囊输入 + 圆形发送键 */
.chat-window__input {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 16px 4px;
  border-top: 0.5px solid var(--brix-border);
  background: rgba(255, 253, 249, 0.66);
  backdrop-filter: blur(14px) saturate(1.1);
  -webkit-backdrop-filter: blur(14px) saturate(1.1);
}

.chat-window__input :deep(.el-textarea__inner) {
  border-radius: 18px;
  padding: 9px 14px;
  background: #f2f2f7;
  box-shadow: none;
  font-size: 14px;
}

.chat-window__input :deep(.el-textarea__inner:focus) {
  background: #fff;
  box-shadow: 0 0 0 1.5px var(--brix-primary) inset;
}

.chat-window__send {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: var(--brix-primary);
  color: #fff;
  font-size: 17px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.18s ease, transform 0.18s cubic-bezier(0.25, 1, 0.5, 1), opacity 0.18s ease;
}

.chat-window__send:hover:not(:disabled) {
  background: var(--brix-primary-strong);
  transform: scale(1.06);
}

.chat-window__send:disabled {
  opacity: 0.4;
  cursor: default;
}

.chat-window__hint {
  padding: 0 16px 10px;
  font-size: 11px;
  color: var(--brix-text-placeholder);
  text-align: right;
  background: rgba(255, 253, 249, 0.66);
}

.chat-window__placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--brix-text-placeholder);
  gap: 10px;
}

.chat-window__placeholder-icon {
  font-size: 42px;
  color: #d9dde3;
}
</style>
