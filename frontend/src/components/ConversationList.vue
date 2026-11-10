<template>
  <div class="conversation-list">
    <div class="conversation-list__head">会话</div>
    <div v-if="!conversations.length" class="conversation-list__empty">还没有会话，去商品详情页联系卖家吧</div>
    <ul v-else>
      <li
        v-for="item in conversations"
        :key="item.peerId"
        :class="['conversation-item', { 'is-active': String(item.peerId) === String(activePeerId) }]"
        @click="emit('select', item)"
      >
        <el-badge :value="item.unreadCount" :hidden="!Number(item.unreadCount)" :max="99">
          <el-avatar :size="38" :src="resolveImage(item.peerAvatar)">
            {{ nicknameInitial(item.peerNickname) }}
          </el-avatar>
        </el-badge>
        <div class="conversation-item__main">
          <div class="conversation-item__top">
            <span class="conversation-item__name">{{ item.peerNickname }}</span>
            <span class="conversation-item__time">{{ formatRelative(item.lastTime) }}</span>
          </div>
          <div class="conversation-item__last">{{ item.lastContent }}</div>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { formatRelative, resolveImage, nicknameInitial } from '@/utils/format'

defineProps({
  conversations: { type: Array, default: () => [] },
  activePeerId: { type: [String, Number], default: '' }
})

const emit = defineEmits(['select'])
</script>

<style scoped>
.conversation-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border-right: 0.5px solid var(--brix-border);
}

.conversation-list__head {
  padding: 14px 16px;
  font-weight: 600;
  border-bottom: 0.5px solid var(--brix-border);
}

.conversation-list__empty {
  padding: 40px 20px;
  font-size: 13px;
  color: var(--brix-text-placeholder);
  text-align: center;
  line-height: 1.7;
}

.conversation-item {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
}

.conversation-item:hover {
  background: #f7f8fa;
}

.conversation-item.is-active {
  background: var(--brix-primary-soft);
  box-shadow: inset 3px 0 0 var(--brix-primary);
}

.conversation-item.is-active .conversation-item__name {
  color: var(--brix-primary);
}

.conversation-item__main {
  flex: 1;
  min-width: 0;
}

.conversation-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conversation-item__name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item__time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--brix-text-placeholder);
}

.conversation-item__last {
  margin-top: 2px;
  font-size: 12px;
  color: var(--brix-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
