<template>
  <div class="messages">
    <div class="messages__layout brix-card">
      <ConversationList
        class="messages__conversations"
        :conversations="conversations"
        :active-peer-id="peerId"
        @select="onSelect"
      />
      <ChatWindow
        class="messages__window"
        :peer-id="peerId"
        :peer="activePeer"
        @received="onReceived"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchConversations, fetchUnreadCount, markRead } from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import ConversationList from '@/components/ConversationList.vue'
import ChatWindow from '@/components/ChatWindow.vue'
import ws from '@/utils/ws'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const conversations = ref([])
const peerId = ref('')
const loading = ref(false)
let unsubscribe = null

/** 当前选中的会话对象 */
const activePeer = computed(
  () => conversations.value.find((item) => String(item.peerId) === String(peerId.value)) || { peerId: peerId.value }
)

/** 加载会话列表与全局未读数 */
async function load() {
  loading.value = true
  try {
    conversations.value = (await fetchConversations()) || []
    const data = await fetchUnreadCount()
    auth.setUnread(data?.count ?? 0)
  } finally {
    loading.value = false
  }
}

/** 选中会话：清该会话未读 */
function onSelect(conversation) {
  peerId.value = String(conversation.peerId)
  router.replace({ path: '/messages', query: { peerId: peerId.value } })
}

/** 收到/发出消息后刷新会话列表摘要 */
function onReceived() {
  load()
}

/** 处理 WS 帧：非当前会话的新消息 → 未读 +1 并提示 */
function handleFrame(frame) {
  if (frame.type !== 'CHAT') return
  const fromSelf = Number(frame.fromUserId) === Number(auth.user?.id)
  if (fromSelf) {
    load()
    return
  }
  if (String(frame.fromUserId) !== String(peerId.value)) {
    auth.incUnread()
    ws.notifyNewMessage(frame.content)
  } else {
    // 当前会话正开着，消息已经看到了：同步落一次已读，避免刷新后红点"复活"
    scheduleMarkRead()
  }
  load()
}

let readTimer = null

/** 300ms 合并多次消息，避免每条消息都打一次已读接口 */
function scheduleMarkRead() {
  if (readTimer) return
  readTimer = setTimeout(async () => {
    readTimer = null
    if (!peerId.value) return
    try {
      const data = await markRead(peerId.value)
      auth.setUnread(data?.count ?? 0)
    } catch (error) {
      // 已读失败不打断聊天，交给下次打开会话补偿
    }
  }, 300)
}

onMounted(async () => {
  if (!auth.isLogin) {
    router.push({ path: '/login', query: { redirect: encodeURIComponent(route.fullPath) } })
    return
  }
  await load()
  // 支持 /messages?peerId=x 直达某个会话（商品详情页「私信卖家」）
  const queryPeerId = typeof route.query.peerId === 'string' ? route.query.peerId : ''
  if (queryPeerId) {
    peerId.value = queryPeerId
  } else if (conversations.value.length) {
    peerId.value = String(conversations.value[0].peerId)
  }
  unsubscribe = ws.onMessage(handleFrame)
})

onBeforeUnmount(() => {
  if (unsubscribe) unsubscribe()
  if (readTimer) {
    clearTimeout(readTimer)
    readTimer = null
  }
})
</script>

<style scoped>
.messages__layout {
  display: flex;
  height: calc(100vh - 200px);
  min-height: 520px;
  overflow: hidden;
}

.messages__conversations {
  width: 280px;
  flex-shrink: 0;
}

.messages__window {
  flex: 1;
  min-width: 0;
}
</style>
