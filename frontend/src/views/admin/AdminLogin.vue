<template>
  <div class="admin-login">
    <!-- 左侧：品牌区（背景吉祥物 + 玻璃职能徽章，与前台登录页刻意区分） -->
    <aside class="admin-login__aside">
      <div class="admin-login__brand">
        <p class="admin-login__brand-name">物归校园 · 管理后台</p>
        <p class="admin-login__brand-sub">Brix Campus Market Console</p>

        <img
          class="admin-login__logo"
          :src="adminMascot"
          alt="物归校园吉祥物"
          style="width: 300px; height: 350px; object-fit: contain;"
        />

        <ul class="admin-login__features">
          <li class="admin-login__feature">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3l7 3v5.2c0 4.6-3 7.6-7 9-4-1.4-7-4.4-7-9V6l7-3z" />
              <path d="M9.2 12.2l2 2 3.6-4" />
            </svg>
            商品审核
          </li>
          <li class="admin-login__feature">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 9a6 6 0 10-12 0c0 6-2.5 7-2.5 7h17S18 15 18 9z" />
              <path d="M10 19.5a2.2 2.2 0 004 0" />
            </svg>
            举报处理
          </li>
          <li class="admin-login__feature">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 20h16" />
              <path d="M7 16v-5" />
              <path d="M12 16V7" />
              <path d="M17 16v-8" />
            </svg>
            运营看板
          </li>
        </ul>
      </div>

      <p class="admin-login__copyright">© 物归校园 · 让闲置回到校园</p>
    </aside>

    <!-- 右侧：登录表单 -->
    <main class="admin-login__main">
      <div class="admin-login__panel">
        <h2 class="admin-login__title">后台登录</h2>
        <p class="admin-login__sub">仅限审核员与运营账号，普通用户请回前台登录</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="后台账号" :prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="登录密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>

          <el-button class="admin-login__submit" :loading="loading" @click="submit">登 录 后 台</el-button>
        </el-form>

        <div class="admin-login__foot">
          <router-link to="/login" class="admin-login__back">
            <el-icon><Back /></el-icon>
            返回用户登录
          </router-link>
          <span class="admin-login__tip">演示：admin_review / admin_ops</span>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import adminMascot from '@/assets/admin-mascot.png'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入后台账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }
  loading.value = true
  try {
    await auth.adminLogin({ username: form.username.trim(), password: form.password })
    ElMessage.success(`欢迎回来，${auth.user?.nickname || auth.role}`)
    const redirect =
      typeof route.query.redirect === 'string' ? decodeURIComponent(route.query.redirect) : ''
    router.replace(redirect && redirect.startsWith('/admin') ? redirect : '/admin/dashboard')
  } catch (error) {
    // 失败提示由 request 拦截器统一弹出（含「无后台权限」）
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 520px);
  background: #0f1319;
  color: #e8edf3;
}

/* ---------------- 左侧信息区 ---------------- */
.admin-login__aside {
  position: relative;
  padding: 64px 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  /* 深色氛围背景图原样展示，不加任何暗化层 */
  background:
    url('../../assets/admin-login-bg.png') center top / cover no-repeat,
    #0f1319;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.admin-login__brand {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

/* 标题下方的吉祥物（已从背景图抠出为独立透明图） */
.admin-login__logo {
  margin: 30px 0 0;
  filter: drop-shadow(0 10px 30px rgba(0, 0, 0, 0.35));
}

.admin-login__brand-name {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: #ffffff;
  text-shadow: 0 2px 18px rgba(0, 0, 0, 0.55);
}

.admin-login__brand-sub {
  margin: 8px 0 0;
  font-size: 14px;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: #b6c2ce;
  text-shadow: 0 1px 12px rgba(0, 0, 0, 0.5);
}

/* 玻璃质感职能徽章：填充标题下的留白 */
.admin-login__features {
  margin: 34px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.admin-login__feature {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: #d9e4de;
  background: rgba(255, 255, 255, 0.055);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.28);
}

.admin-login__feature svg {
  width: 15px;
  height: 15px;
  color: #5fc79a;
}

/* 左下角落款（独立类名，避免与右侧表单区的 admin-login__foot 冲突） */
.admin-login__copyright {
  position: absolute;
  z-index: 1;
  left: 60px;
  bottom: 34px;
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.6px;
  color: rgba(214, 226, 220, 0.55);
}

/* ---------------- 右侧表单区 ---------------- */
.admin-login__main {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 48px;
  background: #12171e;
}

.admin-login__panel {
  width: 100%;
  max-width: 360px;
}

.admin-login__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: #f2f5f9;
}

.admin-login__sub {
  margin: 8px 0 28px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #7c8898;
}

.admin-login__panel :deep(.el-input__wrapper) {
  background: #1a212b;
  border-radius: 10px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08) inset;
}

.admin-login__panel :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.16) inset;
}

.admin-login__panel :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #0e7a50 inset;
}

.admin-login__panel :deep(.el-input__inner) {
  color: #e8edf3;
}

.admin-login__panel :deep(.el-input__inner::placeholder) {
  color: #5d6774;
}

.admin-login__panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.admin-login__submit {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  border: none;
  border-radius: 11px;
  background: #0e7a50;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
}

.admin-login__submit:hover,
.admin-login__submit:focus {
  background: #0a5f3e;
  color: #fff;
}

.admin-login__foot {
  margin-top: 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.admin-login__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: #8d99a8;
  transition: color 0.18s ease;
}

.admin-login__back:hover {
  color: #e8edf3;
}

.admin-login__tip {
  font-size: 11.5px;
  color: #55606d;
}

@media (max-width: 1000px) {
  .admin-login {
    grid-template-columns: 1fr;
  }

  .admin-login__aside {
    display: none;
  }
}
</style>
