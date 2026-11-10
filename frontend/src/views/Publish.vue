<template>
  <div class="publish">
    <div class="publish__head">
      <h1>{{ isEdit ? '编辑商品' : '发布闲置' }}</h1>
      <p class="brix-text-secondary">发布后立即上架展示</p>
    </div>

    <el-alert class="publish__audit-alert" type="warning" :closable="false" show-icon>
      <template #title>发布前将进行两道合规检测</template>
      违禁词检测（枪支弹药 / 毒品 / 假冒 / 站外引流 / 绝对化用语等将无法发布）+
      AI 审核员复审（依据《电子商务法》《广告法》等判定，含谐音变体识别）。
      请如实描述成色与瑕疵，勿留站外联系方式。
    </el-alert>

    <div class="publish__body">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="publish__form brix-card">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="40" show-word-limit placeholder="一句话说清是什么东西（40 字内）" />
        </el-form-item>

        <el-form-item label="选择分类" prop="categoryId">
          <el-cascader
            v-model="categoryPath"
            :options="categoryOptions"
            :props="{ value: 'id', label: 'name', children: 'children', expandTrigger: 'hover' }"
            placeholder="如：数码电子 → 手机/平板"
            style="width: 320px"
            clearable
            @change="onCategoryChange"
          />
        </el-form-item>

        <el-form-item label="价格" prop="price">
          <div class="publish__price-row">
            <el-input-number v-model="form.price" :min="0.01" :max="99999.99" :precision="2" :controls="false" placeholder="0.00" style="width: 160px" />
            <span class="publish__price-label">元</span>
            <el-input-number v-model="form.originalPrice" :min="0.01" :max="999999.99" :precision="2" :controls="false" placeholder="原价（选填）" style="width: 160px" />
            <span class="publish__price-label">原价（选填，不低于现价）</span>
          </div>
        </el-form-item>

        <el-form-item label="成色" prop="conditionLevel">
          <el-radio-group v-model="form.conditionLevel">
            <el-radio-button
              v-for="(info, value) in CONDITION_DICT"
              :key="value"
              :value="value"
            >
              {{ info.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="所在校区" prop="campusId">
          <div class="publish__campus-row">
            <el-select
              v-model="form.schoolId"
              class="publish__campus-select"
              placeholder="选择学校"
              filterable
              @change="onSchoolChange"
            >
              <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
            <el-select
              v-model="form.campusId"
              class="publish__campus-select"
              :placeholder="form.schoolId ? '选择校区' : '请先选择学校'"
              :disabled="!form.schoolId"
              @change="onCampusChange"
            >
              <el-option v-for="c in campusOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </div>
          <div class="publish__campus-tip">校区会展示在商品卡片上，同校买家更容易找到你</div>
        </el-form-item>

        <el-form-item label="交易方式" prop="tradeType">
          <el-radio-group v-model="form.tradeType">
            <el-radio-button
              v-for="(info, value) in TRADE_TYPE_DICT"
              :key="value"
              :value="value"
            >
              {{ info.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="品牌型号">
          <div class="publish__brand">
            <el-input
              v-model="form.brandModel"
              maxlength="60"
              placeholder="如 iPhone 13 128G（选填，能让估价和推荐更准）"
            />
            <div v-if="brandTags.length" class="publish__brand-tags">
              <el-tag
                v-for="tag in brandTags"
                :key="tag"
                class="publish__brand-tag"
                effect="plain"
                @click="applyBrandTag(tag)"
              >
                {{ tag }}
              </el-tag>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="联系方式">
          <el-input v-model="form.contact" maxlength="50" placeholder="微信号 / QQ（选填，也可只留私信）" />
        </el-form-item>

        <el-form-item label="商品图片">
          <ImageUploader v-model="form.images" :max="MAX_ITEM_IMAGES" biz-type="item" />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="成色细节、购买渠道、配件情况、可面交时间等"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="submit">
            {{ isEdit ? '保存修改' : '立即发布' }}
          </el-button>
          <el-button size="large" @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>

      <aside class="publish__ai">
        <AiPricePanel
          v-model:brand-model="form.brandModel"
          v-model:original-price="form.originalPrice"
          :category-id="form.categoryId"
          :condition-level="form.conditionLevel"
          @apply-price="applyPrice"
          @apply-copy="applyCopy"
        />
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createItem, fetchItemDetail, updateItem } from '@/api/item'
import { fetchCategories } from '@/api/category'
import { loadSchools, resolveCampusByName } from '@/api/school'
import {
  BRAND_TAGS_BY_CATEGORY,
  CONDITION_DICT,
  MAX_ITEM_IMAGES,
  TRADE_TYPE_DICT
} from '@/constants'
import ImageUploader from '@/components/ImageUploader.vue'
import AiPricePanel from '@/components/AiPricePanel.vue'

const route = useRoute()
const router = useRouter()

const editId = typeof route.query.id === 'string' && route.query.id ? route.query.id : ''
const isEdit = computed(() => !!editId)

const formRef = ref(null)
const submitting = ref(false)
const categories = ref([])
/** 学校字典（含校区），学校/校区两级联动用 */
const schools = ref([])
/** 级联选择路径：[大类 id, 子类 id] */
const categoryPath = ref([])

/** 级联选项：一级分类 + 其子类（后端 Long 序列化为字符串，统一用 String 比较） */
const categoryOptions = computed(() => {
  const tops = categories.value.filter((c) => !c.parentId || String(c.parentId) === '0')
  return tops.map((top) => ({
    id: top.id,
    name: top.name,
    children: categories.value
      .filter((c) => String(c.parentId) === String(top.id))
      .map((sub) => ({ id: sub.id, name: sub.name }))
  }))
})

/** 级联变化 → 取叶子分类 id 作为表单值 */
function onCategoryChange(path) {
  form.categoryId = path && path.length ? path[path.length - 1] : ''
}

/** 当前子类的品牌快捷标签（无匹配返回空数组，不展示） */
const brandTags = computed(() => {
  if (!categoryPath.value || categoryPath.value.length < 2) return []
  const subId = String(categoryPath.value[categoryPath.value.length - 1])
  const sub = categories.value.find((c) => String(c.id) === subId)
  return (sub && BRAND_TAGS_BY_CATEGORY[sub.name]) || []
})

/** 点击品牌标签 → 填入品牌型号（追加型号描述的活儿交给用户，标签只定品牌） */
function applyBrandTag(tag) {
  form.brandModel = tag
}

// 表单副本（编辑模式先取详情再回填）
const form = reactive({
  title: '',
  description: '',
  categoryId: '',
  price: null,
  originalPrice: null,
  conditionLevel: 'LIGHT_USE',
  schoolId: '',
  campusId: '',
  /** 校区名称快照：后端 campus 必填，编辑回填与历史展示都靠它 */
  campus: '',
  tradeType: 'MEET',
  contact: '',
  brandModel: '',
  images: []
})

/** 当前学校下的校区；未选学校时为空，避免跨校混选 */
const campusOptions = computed(() => {
  if (!form.schoolId) return []
  const school = schools.value.find((s) => String(s.id) === String(form.schoolId))
  return school?.campuses || []
})

/** 切换学校：清空校区，避免出现「A 校 + B 校校区」的非法组合 */
function onSchoolChange() {
  form.campusId = ''
  form.campus = ''
}

/** 选中校区：同步写入名称快照 */
function onCampusChange(campusId) {
  const campus = campusOptions.value.find((c) => String(c.id) === String(campusId))
  form.campus = campus ? campus.name : ''
}

const rules = {
  title: [{ required: true, message: '标题不能为空', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '价格不能为空', trigger: 'blur' }],
  conditionLevel: [{ required: true, message: '请选择成色', trigger: 'change' }],
  campusId: [{ required: true, message: '请选择所在校区', trigger: 'change' }],
  tradeType: [{ required: true, message: '请选择交易方式', trigger: 'change' }],
  description: [{ required: true, message: '描述不能为空', trigger: 'blur' }]
}

/** AI 建议价回填 */
function applyPrice(price) {
  form.price = Number(price)
  ElMessage.success('已使用 AI 建议价')
}

/** AI 文案回填：标题描述直接填入，tags 以话题标签形式追加到描述末尾 */
function applyCopy(copy) {
  if (!copy) return
  if (copy.title) form.title = copy.title
  if (copy.description) {
    let desc = copy.description
    if (copy.tags?.length) {
      desc += '\n' + copy.tags.map((tag) => '#' + tag).join(' ')
    }
    form.description = desc
  }
  ElMessage.success('已填入标题与描述')
}

/** 提交：新建走 POST，编辑走 PUT（后端一律回到待审核） */
async function submit() {
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }
  const payload = {
    title: form.title.trim(),
    description: form.description.trim(),
    categoryId: form.categoryId,
    price: form.price,
    originalPrice: form.originalPrice,
    conditionLevel: form.conditionLevel,
    campus: form.campus,
    schoolId: form.schoolId || null,
    campusId: form.campusId || null,
    tradeType: form.tradeType,
    contact: form.contact.trim(),
    brandModel: form.brandModel.trim(),
    images: form.images
  }
  if (isEdit.value && form.originalPrice !== null && Number(form.originalPrice) < Number(form.price)) {
    ElMessage.warning('原价不能低于现价')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      const updated = await updateItem(editId, payload)
      // 编辑命中中风险会重新进入人工队列，必须告知用户，否则会以为「改完就上架了」
      if (updated && updated.status === 'PENDING') {
        ElMessage.warning('修改成功，内容需人工复核后才会重新上架')
      } else if (updated && updated.status === 'OFF_SHELF') {
        ElMessage.success('修改成功，商品仍保持下架状态')
      } else {
        ElMessage.success('修改成功')
      }
    } else {
      const created = await createItem(payload)
      notifyByAuditResult(created)
    }
    router.push('/my/items')
  } finally {
    submitting.value = false
  }
}

/**
 * 按三级分流结果给出真实反馈。
 *
 * 低风险：发完即上架（改造前的体验，保持不变）；
 * 中风险：已进人工队列，告诉用户大概要看什么；
 * 高风险：已落库但不可见，说明驳回原因，避免用户以为「发布失败」。
 */
function notifyByAuditResult(item) {
  if (!item) {
    ElMessage.success('发布成功')
    return
  }
  if (item.status === 'REJECTED') {
    ElMessage.error({
      message: `发布未通过审核：${item.auditReason || '内容不符合平台规则'}`,
      duration: 6000
    })
    return
  }
  if (item.status === 'PENDING') {
    ElMessage.warning({
      message: `已提交，正在人工复核：${item.auditReason || '内容需审核员确认'}`,
      duration: 6000
    })
    return
  }
  ElMessage.success('发布成功，已上架')
}

onMounted(async () => {
  // 学校字典必须先就绪，否则默认值回填与编辑回填时校区选项是空的
  try {
    schools.value = (await loadSchools()) || []
  } catch (error) {
    schools.value = []
  }
  try {
    categories.value = (await fetchCategories()) || []
  } catch (error) {
    categories.value = []
  }
  applyDefaultSchool()

  // 编辑模式：先取详情再回填（禁止用列表行对象直接回填）
  if (isEdit.value) {
    try {
      const detail = await fetchItemDetail(editId)
      form.title = detail.title
      form.description = detail.description
      form.categoryId = detail.categoryId
      // 重建级联路径：子类 → [父, 子]；旧数据的一级分类 → [自身]
      const current = categories.value.find((c) => String(c.id) === String(detail.categoryId))
      categoryPath.value = current?.parentId && String(current.parentId) !== '0'
        ? [current.parentId, detail.categoryId]
        : [detail.categoryId]
      form.price = Number(detail.price)
      form.originalPrice = detail.originalPrice === null ? null : Number(detail.originalPrice)
      form.conditionLevel = detail.conditionLevel
      form.schoolId = detail.schoolId || ''
      form.campusId = detail.campusId || ''
      form.campus = detail.campus || ''
      // 历史商品只存了校区名称、没有 ID：名称全库唯一时自动补全，跨校重名交给用户手选
      if (!form.campusId && form.campus) {
        applyCampusByName(form.campus)
      }
      form.tradeType = detail.tradeType
      form.contact = detail.contact || ''
      form.brandModel = detail.brandModel || ''
      form.images = (detail.images || []).map((image) => image.url)
    } catch (error) {
      ElMessage.error('商品加载失败')
      router.push('/my/items')
    }
  }
})

/** 用当前登录用户的学校/校区作为默认值（失败则留空由用户手选） */
function applyDefaultSchool() {
  let user = null
  try {
    user = JSON.parse(localStorage.getItem('brix_user') || 'null')
  } catch (error) {
    return false
  }
  if (!user) return false
  if (user.schoolId) {
    form.schoolId = user.schoolId
    form.campusId = user.campusId || ''
    const campus = campusOptions.value.find((c) => String(c.id) === String(form.campusId))
    form.campus = campus ? campus.name : ''
    return true
  }
  // 老账号没有结构化 ID，退回名称反查
  return applyCampusByName(user.campus)
}

/**
 * 用校区名称反查并回填学校/校区（历史数据只有名称，没有 ID）。
 *
 * @param name 校区名称
 * @returns {boolean} 是否成功匹配（重名或未找到时为 false，交给用户手选）
 */
function applyCampusByName(name) {
  const hit = resolveCampusByName(schools.value, name)
  if (!hit) return false
  form.schoolId = hit.schoolId
  form.campusId = hit.campusId
  form.campus = name
  return true
}
</script>

<style scoped>
.publish__brand {
  width: 100%;
}

.publish__brand-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.publish__brand-tag {
  cursor: pointer;
  user-select: none;
}

.publish__brand-tag:hover {
  color: var(--brix-primary);
  border-color: var(--brix-primary);
}

.publish__head {
  margin-bottom: 16px;
}

.publish__head h1 {
  font-size: 22px;
  margin: 0;
}

.publish__audit-alert {
  margin-bottom: 16px;
  border-radius: 10px;
}

.publish__body {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.publish__form {
  flex: 1;
  min-width: 0;
  padding: 24px 24px 8px;
}

.publish__ai {
  width: 520px;
  flex-shrink: 0;
}

.publish__campus-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.publish__campus-select {
  width: 240px;
}

.publish__campus-tip {
  width: 100%;
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--brix-text-placeholder);
}

.publish__price-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.publish__price-label {
  font-size: 12px;
  color: var(--brix-text-placeholder);
}
</style>
