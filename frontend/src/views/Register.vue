<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-card__head">
        <div class="auth-card__brand">
          <span class="auth-card__mark">归</span>
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
        <el-form-item label="学校" prop="school">
          <el-input v-model="form.school" placeholder="示例大学" />
        </el-form-item>
        <el-form-item label="校区" prop="campus">
          <el-select v-model="form.campus" placeholder="选择你的校区" style="width: 100%">
            <el-option v-for="campus in CAMPUS_OPTIONS" :key="campus" :label="campus" :value="campus" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位数字" maxlength="11" />
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
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { CAMPUS_OPTIONS } from '@/constants'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  school: '示例大学',
  campus: '',
  phone: ''
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
  phone: [{ pattern: /^$|^\d{11}$/, message: '手机号必须为 11 位数字', trigger: 'blur' }]
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
      school: form.school.trim(),
      campus: form.campus,
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
