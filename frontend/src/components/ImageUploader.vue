<template>
  <div class="image-uploader">
    <div class="image-uploader__list">
      <div v-for="(url, index) in list" :key="url + index" class="image-uploader__item"
           :class="{ 'image-uploader__item--reject': auditOf(url).status === 'REJECT' }">
        <el-image :src="resolveImage(url)" fit="cover" class="image-uploader__image" />
        <span v-if="index === 0" class="image-uploader__cover">首图</span>
        <span v-if="auditOf(url).status" class="image-uploader__audit"
              :class="`image-uploader__audit--${auditOf(url).status.toLowerCase()}`"
              :title="auditOf(url).reason || ''">
          {{ auditLabel(auditOf(url)) }}
        </span>
        <div class="image-uploader__actions">
          <el-button circle size="small" :disabled="index === 0" @click="moveUp(index)">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <el-button circle size="small" :disabled="index === list.length - 1" @click="moveDown(index)">
            <el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-button circle size="small" type="danger" @click="remove(index)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>

      <label v-if="list.length < max" class="image-uploader__add" :class="{ 'is-loading': uploading }">
        <input type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onSelect" />
        <el-icon><Plus /></el-icon>
        <span>{{ uploading ? '上传中…' : '添加图片' }}</span>
      </label>
    </div>
    <p class="image-uploader__tip">
      最多 {{ max }} 张，支持 jpg/jpeg/png/webp，单张不超过 5MB；第一张为封面，可用箭头调整顺序。
      图片上传后自动进行 AI 合规审核，含二维码水印 / 违禁物品的图片无法发布。
    </p>
  </div>
</template>

<script setup>
import { computed, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Delete, Plus } from '@element-plus/icons-vue'
import { uploadImage } from '@/api/upload'
import { resolveImage } from '@/utils/format'
import ws from '@/utils/ws'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 9 },
  bizType: { type: String, default: 'item' }
})

const emit = defineEmits(['update:modelValue'])

const uploading = ref(false)

const list = computed(() => props.modelValue || [])

/** url → { md5, status, reason }：图片审核状态（仅 bizType=item 时有值） */
const auditMap = reactive({})

/** 订阅 WS 图片审核结论：上传时 PENDING 的图，审完在这里更新角标 */
const unsubscribe = ws.onMessage((payload) => {
  if (payload.type !== 'IMAGE_AUDIT') return
  let result = null
  try {
    result = JSON.parse(payload.content)
  } catch (error) {
    return
  }
  if (!result?.md5) return
  for (const [url, state] of Object.entries(auditMap)) {
    if (state.md5 === result.md5) {
      state.status = result.status
      state.reason = result.reason || ''
      if (result.status === 'REJECT') {
        ElMessage.error('图片未通过审核：' + (result.reason || '包含违规内容'))
      }
    }
  }
})
onUnmounted(() => unsubscribe())

function auditOf(url) {
  return auditMap[url] || { md5: '', status: '', reason: '' }
}

function auditLabel(state) {
  switch (state.status) {
    case 'PENDING': return 'AI 审核中'
    case 'PASS': return 'AI 通过'
    case 'REJECT': return 'AI 判违规'
    case 'UNCERTAIN': return '转人工'
    case 'FAILED': return '未完成审核'
    default: return ''
  }
}

/** 是否存在被判违规的图片（发布前由父组件校验） */
function hasRejectedImage() {
  return Object.values(auditMap).some((state) => state.status === 'REJECT')
}
defineExpose({ hasRejectedImage })

/** 选择文件后立即上传，成功则追加 URL 并登记审核状态 */
async function onSelect(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const data = await uploadImage(file, props.bizType)
    emit('update:modelValue', [...list.value, data.url])
    if (data.imageMd5) {
      auditMap[data.url] = { md5: data.imageMd5, status: data.auditStatus || '', reason: data.auditReason || '' }
      if (data.auditStatus === 'REJECT') {
        ElMessage.error('图片未通过审核：' + (data.auditReason || '包含违规内容'))
      }
    }
  } catch (error) {
    // request.js 已提示
  } finally {
    uploading.value = false
  }
}

/** 删除某张 */
function remove(index) {
  const url = list.value[index]
  delete auditMap[url]
  const next = [...list.value]
  next.splice(index, 1)
  emit('update:modelValue', next)
}

/** 上移一位 */
function moveUp(index) {
  if (index === 0) return
  const next = [...list.value]
  ;[next[index - 1], next[index]] = [next[index], next[index - 1]]
  emit('update:modelValue', next)
}

/** 下移一位 */
function moveDown(index) {
  if (index === list.value.length - 1) return
  const next = [...list.value]
  ;[next[index], next[index + 1]] = [next[index + 1], next[index]]
  emit('update:modelValue', next)
}
</script>

<style scoped>
.image-uploader__list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.image-uploader__item {
  position: relative;
  width: 104px;
  height: 104px;
  border-radius: 8px;
  overflow: hidden;
  border: 0.5px solid var(--brix-border);
}

.image-uploader__image {
  width: 100%;
  height: 100%;
}

.image-uploader__cover {
  position: absolute;
  top: 4px;
  left: 4px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--brix-primary);
  color: #fff;
  font-size: 11px;
}

.image-uploader__actions {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 2px;
  padding: 4px 0;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.15s;
}

.image-uploader__item:hover .image-uploader__actions {
  opacity: 1;
}

.image-uploader__add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 104px;
  height: 104px;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
  color: var(--brix-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}

.image-uploader__add:hover {
  border-color: var(--brix-primary);
  color: var(--brix-primary);
}

.image-uploader__tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.image-uploader__audit {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: #fff;
  background: var(--el-color-info);
  pointer-events: none;
}

.image-uploader__audit--pending {
  background: var(--el-color-warning);
}

.image-uploader__audit--pass {
  background: var(--el-color-success);
}

.image-uploader__audit--reject {
  background: var(--el-color-danger);
}

.image-uploader__audit--uncertain {
  background: var(--el-color-warning);
}

.image-uploader__audit--failed {
  background: var(--el-color-info);
}
</style>
