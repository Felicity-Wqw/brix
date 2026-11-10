<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-card__head">
        <div class="auth-card__brand">
          <img :src="logoIcon" alt="物归校园" class="auth-card__mark auth-card__mark--img" style="width: 40px; height: 40px; object-fit: contain;" />
          <span class="auth-card__brand-name">物归校园</span>
        </div>
        <h1 class="auth-card__title">注册账号</h1>
        <p class="auth-card__sub">注册即视为同意「校内面交优先」的交易规则</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3-20 个字符，登录用" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="至少 6 位" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="展示给别人看的名字" />
        </el-form-item>
        <el-form-item label="学校" prop="schoolId">
          <el-select
            v-model="form.schoolId"
            placeholder="选择你就读的学校（可跳过，稍后补全）"
            filterable
            clearable
            style="width: 100%"
            @change="onSchoolChange"
          >
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="校区" prop="campusId">
          <el-select
            v-model="form.campusId"
            :placeholder="form.schoolId ? '选择你的校区' : '请先选择学校'"
            :disabled="!form.schoolId"
            clearable
            style="width: 100%"
            @change="onCampusChange"
          >
            <el-option v-for="c in campusOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            type="tel"
            placeholder="11 位手机号（选填）"
            maxlength="11"
            @input="onPhoneInput"
          />
        </el-form-item>
        <el-button type="primary" class="auth-card__submit" :loading="loading" @click="submit">注册并登录</el-button>
      </el-form>

      <div class="auth-card__footer">
        <span>已有账号？</span>
        <router-link to="/login" class="auth-card__link">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { loadSchools } from '@/api/school'
import logoIcon from '@/assets/logo-icon.png'
import { PHONE_MESSAGE, PHONE_PATTERN, sanitizePhone } from '@/constants'

const router = useRouter()
const auth = useAuthStore()

/** 学校字典（含校区）；接口不可用时保持空列表，注册仍可完成 */
const schools = ref([])

const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  /** 学校名称快照（后端存字符串） */
  school: '',
  /** 校区名称快照 */
  campus: '',
  schoolId: '',
  campusId: '',
  phone: ''
})

/** 当前学校下的校区；未选学校时为空，避免跨校混选 */
const campusOptions = computed(() => {
  if (!form.schoolId) return []
  const school = schools.value.find((s) => String(s.id) === String(form.schoolId))
  return school?.campuses || []
})

/** 切换学校：清空校区，并同步学校名称快照 */
function onSchoolChange() {
  const school = schools.value.find((s) => String(s.id) === String(form.schoolId))
  form.school = school ? school.name : ''
  form.campusId = ''
  form.campus = ''
}

/** 选中校区：同步校区名称快照 */
function onCampusChange() {
  const campus = campusOptions.value.find((c) => String(c.id) === String(form.campusId))
  form.campus = campus ? campus.name : ''
}

onMounted(async () => {
  try {
    schools.value = (await loadSchools()) || []
  } catch (error) {
    schools.value = []
  }
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度需为 3-20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [{ pattern: PHONE_PATTERN, message: PHONE_MESSAGE, trigger: 'blur' }]
}

/** 输入即过滤非数字，避免字母混进手机号（maxlength 只管长度拦不住字符） */
function onPhoneInput(value) {
  form.phone = sanitizePhone(value)
}

/** 注册成功即登录 */
async function submit() {
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }
  loading.value = true
  try {
    await auth.register({
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim(),
      school: form.school,
      campus: form.campus,
      schoolId: form.schoolId || null,
      campusId: form.campusId || null,
      phone: form.phone.trim()
    })
    ElMessage.success('注册成功，欢迎加入')
    router.replace('/')
  } catch (error) {
    // 用户名重复等错误由 request.js 提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  background:
    radial-gradient(900px 500px at 82% 18%, rgba(148, 210, 189, 0.55) 0%, transparent 60%),
    radial-gradient(760px 520px at 12% 85%, rgba(15, 169, 104, 0.5) 0%, transparent 62%),
    linear-gradient(160deg, #e9f6ef 0%, #cdeadb 40%, #9fd4bb 78%, #7fbfa3 100%);
}

.auth-card {
  width: 420px;
  padding: 32px 36px 24px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(22px) saturate(1.15);
  -webkit-backdrop-filter: blur(22px) saturate(1.15);
  border: 0.5px solid rgba(255, 255, 255, 0.65);
  box-shadow: 0 22px 60px rgba(10, 60, 40, 0.18);
}

.auth-card__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.auth-card__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #16181d;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

/* 图标版 logo：去掉方块底色 */
.auth-card__mark--img {
  background: none;
  border-radius: 0;
}

.auth-card__brand-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #16181d;
}

.auth-card__title {
  font-size: 24px;
  font-weight: 700;
  color: #16181d;
}

.auth-card__sub {
  margin-top: 6px;
  font-size: 13px;
  color: #7a8290;
}

.auth-card :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 0 0 1px #e6e9ee inset;
}

.auth-card__submit {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  border: none;
  border-radius: 12px;
  background: #16181d;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  transition: background 0.18s ease;
}

.auth-card__submit:hover {
  background: #2a2d35;
  color: #fff;
}

.auth-card__footer {
  margin-top: 14px;
  font-size: 13px;
  color: #7a8290;
}

.auth-card__link {
  color: var(--brix-primary);
}
</style>
