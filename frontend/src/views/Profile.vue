<template>
  <div class="profile">
    <div class="brix-section-title"><span>个人中心</span></div>

    <div class="profile__grid">
      <!-- 左：资料与头像 -->
      <div class="profile__card brix-card">
        <div class="profile__avatar-row">
          <el-avatar :size="72" :src="avatarSrc">
            {{ nicknameInitial(auth.user?.nickname) }}
          </el-avatar>
          <div>
            <div class="profile__name">{{ auth.user?.nickname }}</div>
            <div class="profile__role">
              <el-tag size="small" effect="plain">{{ auth.user?.roleDesc || auth.user?.role }}</el-tag>
              <span class="profile__credit">信用分 {{ auth.user?.creditScore }}</span>
            </div>
            <!-- 说明：Chrome 点击 <button> 不会把点击转发给 label 关联的 input，
                 因此必须用 ref 直接触发隐藏 input 的 click（BUG-B 修复） -->
            <div class="profile__avatar-btn">
              <input
                ref="avatarInputRef"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                hidden
                @change="onAvatarChange"
              />
              <el-button size="small" :loading="avatarUploading" @click="openFilePicker">更换头像</el-button>
            </div>
            <p class="profile__avatar-tip">支持 jpg / png / webp，不超过 5MB</p>
          </div>
        </div>

        <el-divider />

        <el-form ref="profileRef" :model="profileForm" :rules="profileRules" label-width="70px">
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="profileForm.nickname" maxlength="20" />
          </el-form-item>
          <el-form-item label="学校">
            <el-select
              v-model="profileForm.schoolId"
              placeholder="选择学校"
              filterable
              clearable
              style="width: 100%"
              @change="onSchoolChange"
            >
              <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="校区">
            <el-select
              v-model="profileForm.campusId"
              :placeholder="profileForm.schoolId ? '选择校区' : '请先选择学校'"
              :disabled="!profileForm.schoolId"
              clearable
              style="width: 100%"
              @change="onCampusChange"
            >
              <el-option v-for="c in campusOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input
              v-model="profileForm.phone"
              type="tel"
              maxlength="11"
              placeholder="11 位手机号"
              @input="onPhoneInput"
            />
          </el-form-item>
          <el-form-item label="简介">
            <el-input v-model="profileForm.bio" type="textarea" :rows="3" maxlength="255" show-word-limit />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 右：我的数据 + 改密码 -->
      <div class="profile__side">
        <div class="profile__card brix-card">
          <h3 class="profile__card-title">我的数据</h3>
          <div class="profile__stats">
            <div class="profile__stat">
              <span class="profile__stat-value">{{ stats.itemCount }}</span>
              <span class="profile__stat-label">发布数</span>
            </div>
            <div class="profile__stat">
              <span class="profile__stat-value">{{ stats.onSaleCount }}</span>
              <span class="profile__stat-label">在售</span>
            </div>
            <div class="profile__stat">
              <span class="profile__stat-value">{{ stats.soldCount }}</span>
              <span class="profile__stat-label">卖出</span>
            </div>
            <div class="profile__stat">
              <span class="profile__stat-value">{{ auth.user?.creditScore ?? '-' }}</span>
              <span class="profile__stat-label">信用分</span>
            </div>
          </div>
          <el-button
            class="profile__analysis-btn"
            type="primary"
            plain
            @click="router.push('/my/analysis')"
          >
            <el-icon style="margin-right: 4px"><TrendCharts /></el-icon>
            查看数据分析
          </el-button>
        </div>

        <div class="profile__card brix-card">
          <h3 class="profile__card-title">修改密码</h3>
          <el-form ref="passwordRef" :model="passwordForm" :rules="passwordRules" label-width="80px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPassword" @click="changePassword">确认修改</el-button>
            </el-form-item>
          </el-form>
          <p class="profile__password-tip">修改密码后当前登录态保持不变，下次登录请使用新密码</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { changePassword as changePasswordApi, updateProfile, uploadAvatar } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { loadSchools, resolveCampusByName } from '@/api/school'
import { PHONE_MESSAGE, PHONE_PATTERN, sanitizePhone } from '@/constants'
import { nicknameInitial, resolveImage } from '@/utils/format'

const auth = useAuthStore()
const router = useRouter()

/** 学校字典（含校区） */
const schools = ref([])

const profileRef = ref(null)
const passwordRef = ref(null)
const avatarInputRef = ref(null)
const saving = ref(false)
const changingPassword = ref(false)
const avatarUploading = ref(false)
/** 头像防缓存版本号：上传成功后更新，强制浏览器重新拉取图片 */
const avatarVersion = ref(0)

/** 允许的头像扩展名 */
const AVATAR_ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp']
/** 头像大小上限：5MB（与后端 brix.upload.max-size 一致） */
const AVATAR_MAX_SIZE = 5 * 1024 * 1024

const profileForm = reactive({
  nickname: '',
  /** 学校名称快照（后端存字符串） */
  school: '',
  /** 校区名称快照 */
  campus: '',
  schoolId: '',
  campusId: '',
  phone: '',
  bio: ''
})

/** 当前学校下的校区；未选学校时为空，避免跨校混选 */
const campusOptions = computed(() => {
  if (!profileForm.schoolId) return []
  const school = schools.value.find((s) => String(s.id) === String(profileForm.schoolId))
  return school?.campuses || []
})

/** 切换学校：清空校区并同步学校名称快照 */
function onSchoolChange() {
  const school = schools.value.find((s) => String(s.id) === String(profileForm.schoolId))
  profileForm.school = school ? school.name : ''
  profileForm.campusId = ''
  profileForm.campus = ''
}

/** 选中校区：同步校区名称快照 */
function onCampusChange() {
  const campus = campusOptions.value.find((c) => String(c.id) === String(profileForm.campusId))
  profileForm.campus = campus ? campus.name : ''
}

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const stats = computed(() => auth.user?.stats || { itemCount: 0, onSaleCount: 0, soldCount: 0 })

/**
 * 头像展示地址：带防缓存版本号，换完头像后立刻生效。
 * @returns {string} 可直接用于 el-avatar 的 src
 */
const avatarSrc = computed(() => {
  const url = resolveImage(auth.user?.avatar)
  if (!url) return ''
  if (!avatarVersion.value) return url
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}t=${avatarVersion.value}`
})

const profileRules = {
  nickname: [{ required: true, message: '昵称不能为空', trigger: 'blur' }],
  phone: [{ pattern: PHONE_PATTERN, message: PHONE_MESSAGE, trigger: 'blur' }]
}

/** 输入即过滤非数字，避免字母混进手机号 */
function onPhoneInput(value) {
  profileForm.phone = sanitizePhone(value)
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

/** 回填资料表单 */
function fillProfile() {
  const user = auth.user || {}
  profileForm.nickname = user.nickname || ''
  profileForm.school = user.school || ''
  profileForm.campus = user.campus || ''
  profileForm.schoolId = user.schoolId || ''
  profileForm.campusId = user.campusId || ''
  // 老账号只存了校区名称、没有 ID：名称全库唯一时自动补全，跨校重名留给用户手选
  if (!profileForm.campusId && profileForm.campus) {
    const hit = resolveCampusByName(schools.value, profileForm.campus)
    if (hit) {
      profileForm.schoolId = hit.schoolId
      profileForm.campusId = hit.campusId
    }
  }
  profileForm.phone = user.phone || ''
  profileForm.bio = user.bio || ''
}

/** 触发隐藏的文件选择框（必须由按钮点击显式调用，Chrome 不会为 button 转发 label 点击） */
function openFilePicker() {
  avatarInputRef.value?.click()
}

/**
 * 前端预校验头像文件（格式 + 大小），不通过时提示并中止。
 * @param {File} file 待上传文件
 * @returns {boolean} 是否通过校验
 */
function validateAvatar(file) {
  const name = file.name || ''
  const extension = name.includes('.') ? name.split('.').pop().toLowerCase() : ''
  if (!AVATAR_ALLOWED_EXTENSIONS.includes(extension)) {
    ElMessage.warning('仅支持 jpg / png / webp 格式的图片')
    return false
  }
  if (file.size > AVATAR_MAX_SIZE) {
    ElMessage.warning('头像大小不能超过 5MB')
    return false
  }
  return true
}

/** 上传头像（即时预览：成功后刷新 store + 更新防缓存版本号） */
async function onAvatarChange(event) {
  const input = event.target
  const file = input.files?.[0]
  // 立即清空 value，保证同一个文件再次选择时仍能触发 change
  input.value = ''
  if (!file) return
  if (!validateAvatar(file)) return
  avatarUploading.value = true
  try {
    await uploadAvatar(file)
    await auth.fetchMe()
    avatarVersion.value = Date.now()
    ElMessage.success('头像已更新')
  } finally {
    avatarUploading.value = false
  }
}

/** 保存资料（后端只认 token 里的用户） */
async function saveProfile() {
  try {
    await profileRef.value.validate()
  } catch (error) {
    return
  }
  saving.value = true
  try {
    await updateProfile({
      nickname: profileForm.nickname.trim(),
      school: profileForm.school,
      campus: profileForm.campus,
      schoolId: profileForm.schoolId || null,
      campusId: profileForm.campusId || null,
      phone: profileForm.phone.trim(),
      bio: profileForm.bio.trim()
    })
    await auth.fetchMe()
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}

/** 修改密码（成功后保持登录态） */
async function changePassword() {
  try {
    await passwordRef.value.validate()
  } catch (error) {
    return
  }
  changingPassword.value = true
  try {
    await changePasswordApi({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，下次登录请使用新密码')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } finally {
    changingPassword.value = false
  }
}

onMounted(async () => {
  // 学校字典先就绪，fillProfile 里的名称反查才有数据可用
  try {
    schools.value = (await loadSchools()) || []
  } catch (error) {
    schools.value = []
  }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch (error) {
      return
    }
  }
  fillProfile()
})
</script>

<style scoped>
.profile__grid {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.profile__card {
  padding: 20px;
  margin-bottom: 16px;
}

.profile__card-title {
  font-size: 15px;
  margin: 0 0 14px;
}

.profile__avatar-row {
  display: flex;
  gap: 16px;
  align-items: center;
}

.profile__name {
  font-size: 17px;
  font-weight: 600;
}

.profile__role {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.profile__credit {
  font-size: 12px;
  color: var(--brix-primary);
}

.profile__avatar-btn {
  display: inline-block;
  margin-top: 8px;
}

.profile__avatar-tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.profile__grid > .profile__card:first-child {
  flex: 1;
  min-width: 0;
}

.profile__side {
  width: 360px;
  flex-shrink: 0;
}

.profile__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  text-align: center;
}

.profile__stat-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--brix-primary);
}

.profile__stat-label {
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.profile__analysis-btn {
  width: 100%;
  margin-top: 14px;
  border-radius: 8px;
}

.profile__password-tip {
  margin: 0;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}
</style>
