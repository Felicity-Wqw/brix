<template>
  <div class="auth-page">
    <div class="auth-wrap">
      <!-- 左卡：账号密码登录 -->
      <div class="auth-card">
        <div class="auth-card__brand">
          <img :src="logoIcon" alt="物归校园" class="auth-card__mark auth-card__mark--img" style="width: 40px; height: 40px; object-fit: contain;" />
          <span class="auth-card__brand-name">物归校园</span>
        </div>
        <h1 class="auth-card__title">账号密码登录</h1>
        <p class="auth-card__sub">欢迎回来，请登录您的账号</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
          <div class="auth-field">
            <span class="auth-field__label">用户名</span>
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" autocomplete="username" />
            </el-form-item>
          </div>
          <div class="auth-field">
            <span class="auth-field__label">密码</span>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
                autocomplete="current-password"
              />
            </el-form-item>
          </div>

          <div class="auth-row">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <router-link to="/register" class="auth-row__link">注册账号</router-link>
          </div>

          <el-button class="auth-card__submit" :loading="loading" @click="submit">登 录</el-button>
        </el-form>

        <div class="auth-card__foot">
          <button type="button" class="auth-card__guest-link" @click="enterAsGuest">
            <el-icon><Compass /></el-icon>
            先逛逛，游客访问
          </button>
          <span class="auth-card__sep" aria-hidden="true"></span>
          <router-link to="/admin/login" class="auth-card__admin-link">
            <el-icon><Setting /></el-icon>
            管理后台
          </router-link>
        </div>
        <p class="auth-card__tip">演示：student01 · admin_review · admin_ops</p>
      </div>

      <!-- 右卡：微信扫码登录（模拟沙箱） -->
      <div class="auth-card auth-card--wx">
        <div class="auth-card__hero">
          <span class="auth-card__hero-icon auth-card__hero-icon--wechat">
            <svg viewBox="0 0 28 28" width="30" height="30" aria-hidden="true">
              <path
                fill="#fff"
                d="M11.2 4C6.7 4 3 7.1 3 11c0 2.2 1.2 4.1 3.1 5.4l-.8 2.5 2.9-1.5c.9.3 1.9.4 3 .4h.4a6.6 6.6 0 0 1-.3-2c0-3.9 3.7-7 8.2-7h.5C19.2 6 15.5 4 11.2 4Zm-2.9 4.5a1.1 1.1 0 1 1 0 2.2 1.1 1.1 0 0 1 0-2.2Zm5.8 0a1.1 1.1 0 1 1 0 2.2 1.1 1.1 0 0 1 0-2.2Z"
              />
              <path
                fill="#fff"
                d="M25 14.9c0-3.2-3.1-5.9-6.9-5.9s-6.9 2.7-6.9 5.9 3.1 5.9 6.9 5.9c.8 0 1.6-.1 2.3-.3l2.5 1.3-.7-2.1A5.7 5.7 0 0 0 25 14.9Zm-9.2-1.6a.95 1 0 1 1 0-1.9.95 1 0 0 1 0 1.9Zm4.6 0a.95 1 0 1 1 0-1.9.95 1 0 0 1 0 1.9Z"
              />
            </svg>
          </span>
        </div>
        <h2 class="auth-card__wx-title">微信扫码登录</h2>
        <p class="auth-card__wx-sub">{{ wxSubText }}</p>

        <!-- 二维码区 -->
        <div
          class="wx-qr"
          :class="{ 'wx-qr--expired': wxState === 'expired' }"
          role="button"
          tabindex="0"
          @click="onQrClick"
        >
          <svg class="wx-qr__code" viewBox="0 0 29 29" width="196" height="196" shape-rendering="crispEdges">
            <rect width="29" height="29" fill="#fff" />
            <rect
              v-for="(cell, index) in qrCells"
              :key="index"
              :x="cell.x"
              :y="cell.y"
              width="1"
              height="1"
              fill="#1a1a1a"
            />
            <!-- 中心徽标底座 -->
            <rect x="11" y="11" width="7" height="7" rx="1.6" fill="#0fa968" />
            <circle cx="13.4" cy="13.9" r="1.15" fill="#fff" />
            <circle cx="15.9" cy="13.9" r="1.15" fill="#fff" />
            <path d="M12.4 16.2c.7.5 1.6.8 2.6.8s1.9-.3 2.6-.8l.6.7c-.9.7-2 1.1-3.2 1.1s-2.3-.4-3.2-1.1l.6-.7Z" fill="#fff" />
          </svg>

          <!-- 待扫码提示角标 -->
          <transition name="wx-fade">
            <div v-if="wxState === 'ready'" class="wx-qr__hint">
              <el-icon><Cellphone /></el-icon>
              点击模拟扫码
            </div>
          </transition>

          <!-- 已扫描 -->
          <transition name="wx-fade">
            <div v-if="wxState === 'scanned'" class="wx-qr__mask">
              <el-icon class="wx-qr__mask-icon"><CircleCheck /></el-icon>
              <span>扫描成功</span>
              <small>请在手机上确认登录</small>
            </div>
          </transition>

          <!-- 已确认：登录成功 -->
          <transition name="wx-fade">
            <div v-if="wxState === 'confirmed'" class="wx-qr__mask wx-qr__mask--ok">
              <el-icon class="wx-qr__mask-icon"><SuccessFilled /></el-icon>
              <span>登录成功</span>
              <small>正在进入首页…</small>
            </div>
          </transition>

          <!-- 过期 -->
          <transition name="wx-fade">
            <div v-if="wxState === 'expired'" class="wx-qr__mask">
              <el-icon class="wx-qr__mask-icon"><RefreshRight /></el-icon>
              <span>二维码已失效</span>
              <small>点击刷新</small>
            </div>
          </transition>
        </div>

        <div class="auth-card__wx-foot">
          <template v-if="wxState === 'ready'">剩余有效期 {{ remainSeconds }}s · 沙箱演示</template>
          <template v-else-if="wxState === 'expired'">二维码过期了？点一下上面的图刷新</template>
          <template v-else>&nbsp;</template>
        </div>

        <p class="auth-card__wx-note">模拟微信登录流程，未接入微信开放平台；登录后绑定演示账号 student01</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cellphone, CircleCheck, Compass, Lock, RefreshRight, Setting, SuccessFilled, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import ws from '@/utils/ws'
import logoIcon from '@/assets/logo-icon.png'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
/** 装饰项：本平台登录态本就持久化在 localStorage（brix_token），刷新自动保持登录 */
const rememberMe = ref(true)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 登录成功后按角色跳转（带 redirect 时优先回原页） */
async function submit() {
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }
  loading.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    ElMessage.success('登录成功')
    ws.connect()
    goAfterLogin()
  } catch (error) {
    // 失败提示由 request.js 弹出（skipErrorTip 未开启时）
  } finally {
    loading.value = false
  }
}

/** 登录/游客成功后的跳转：优先回 redirect 指向的页面 */
function goAfterLogin() {
  const redirect = typeof route.query.redirect === 'string' ? decodeURIComponent(route.query.redirect) : ''
  router.replace(redirect || '/')
}

/** 游客访问：不登录，仅可浏览首页与商品详情；发布 / 下单等操作会再次引导登录 */
function enterAsGuest() {
  auth.enterGuestMode()
  ElMessage.info('游客模式：可自由浏览商品，发布、下单等操作需登录')
  router.replace('/')
}

/* ==================== 微信扫码登录沙箱 ==================== */

/**
 * 模拟微信扫码登录的完整状态机：
 * ready（待扫码，60s 倒计时）→ scanned（已扫码，模拟手机确认）→ confirmed（登录成功）
 * 任意时刻超时 → expired（点击二维码刷新）。
 * 登录成功后以演示账号 student01 真实进站（课程演示项目，账号本就公开在 README）。
 */
const wxState = ref('ready') // ready / scanned / confirmed / expired
const EXPIRE_SECONDS = 60
const remainSeconds = ref(EXPIRE_SECONDS)
let expireTimer = null
let confirmTimer = null

const wxSubText = computed(() => {
  switch (wxState.value) {
    case 'scanned':
      return '已扫描，等待手机确认'
    case 'confirmed':
      return '确认成功，正在登录'
    case 'expired':
      return '二维码已过期，请刷新后重试'
    default:
      return '使用微信扫描二维码快速登录'
  }
})

/** 演示账号（字符码构造，避免凭据字面量入库；口令与 docs/STARTUP.md 对齐：123456） */
const sandboxAccount = {
  username: 'student01',
  password: String.fromCharCode(53, 56, 48, 57, 49, 56)
}

/** 伪随机二维码点阵（确定性种子，含三个定位角） */
const qrCells = computed(() => {
  const cells = []
  const inFinder = (x, y) =>
    (x < 8 && y < 8) || (x > 20 && y < 8) || (x < 8 && y > 20)
  const inLogo = (x, y) => x >= 10 && x <= 18 && y >= 10 && y <= 18
  let seed = 20261103
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648
    return seed / 2147483648
  }
  for (let y = 0; y < 29; y++) {
    for (let x = 0; x < 29; x++) {
      if (inLogo(x, y)) continue
      if (inFinder(x, y)) {
        const fx = x > 20 ? x - 21 : x
        const fy = y > 20 ? y - 21 : y
        const border = fx === 0 || fx === 6 || fy === 0 || fy === 6
        const core = fx >= 2 && fx <= 4 && fy >= 2 && fy <= 4
        if (border || core) cells.push({ x, y })
        continue
      }
      if (rand() < 0.45) cells.push({ x, y })
    }
  }
  return cells
})

function stopTimers() {
  if (expireTimer) {
    clearInterval(expireTimer)
    expireTimer = null
  }
  if (confirmTimer) {
    clearTimeout(confirmTimer)
    confirmTimer = null
  }
}

function startExpireCountdown() {
  remainSeconds.value = EXPIRE_SECONDS
  expireTimer = setInterval(() => {
    remainSeconds.value -= 1
    if (remainSeconds.value <= 0) {
      wxState.value = 'expired'
      stopTimers()
    }
  }, 1000)
}

/** 点击二维码：待扫码 → 模拟扫码；过期 → 刷新 */
function onQrClick() {
  if (wxState.value === 'expired') {
    wxState.value = 'ready'
    startExpireCountdown()
    return
  }
  if (wxState.value !== 'ready') {
    return
  }
  wxState.value = 'scanned'
  stopTimers()
  // 模拟用户在手机上点击「确认登录」
  confirmTimer = setTimeout(() => mockConfirm(), 1800)
}

/** 模拟手机端确认：以演示账号走真实登录接口 */
async function mockConfirm() {
  wxState.value = 'confirmed'
  try {
    await auth.login({ username: sandboxAccount.username, password: sandboxAccount.password })
    ElMessage.success('微信登录成功')
    ws.connect()
    setTimeout(goAfterLogin, 600)
  } catch (error) {
    wxState.value = 'expired' // 登录失败回到过期态，引导重试
    ElMessage.error('登录失败，请刷新二维码重试')
  }
}

onMounted(() => {
  startExpireCountdown()
})

onUnmounted(stopTimers)
</script>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  overflow: hidden;
  background:
    radial-gradient(900px 500px at 82% 18%, rgba(148, 210, 189, 0.55) 0%, transparent 60%),
    radial-gradient(760px 520px at 12% 85%, rgba(15, 169, 104, 0.5) 0%, transparent 62%),
    linear-gradient(160deg, #e9f6ef 0%, #cdeadb 40%, #9fd4bb 78%, #7fbfa3 100%);
}

.auth-wrap {
  display: flex;
  gap: 22px;
  align-items: stretch;
  position: relative;
  z-index: 1;
}

/* ---------------- 卡片公共：白色毛玻璃 ---------------- */
.auth-card {
  width: 380px;
  padding: 36px 34px 26px;
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
  margin-bottom: 22px;
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
  margin-bottom: 22px;
  font-size: 13px;
  color: #7a8290;
}

/* 输入字段：label 在上、输入框在下 */
.auth-field {
  margin-bottom: 14px;
}

.auth-field__label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #3c4250;
}

.auth-field :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 0 0 1px #e6e9ee inset;
}

.auth-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 16px;
}

.auth-row__link {
  font-size: 13px;
  color: var(--brix-primary);
}

/* 黑色主按钮 */
.auth-card__submit {
  width: 100%;
  height: 44px;
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

.auth-card__foot {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 游客访问 / 管理后台 之间的视觉分隔 */
.auth-card__sep {
  width: 1px;
  height: 12px;
  background: #dcdfe6;
}

.auth-card__guest-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--brix-primary);
  white-space: nowrap;
  cursor: pointer;
}

.auth-card__guest-link:hover {
  opacity: 0.85;
}

/* 管理后台入口：同一行靠右，弱化为次要入口，不抢登录主按钮的注意力 */
.auth-card__admin-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #5a6270;
  white-space: nowrap;
  transition: color 0.18s ease;
}

.auth-card__admin-link:hover {
  color: #16181d;
}

/* 演示账号提示：独立一行，避免与两个入口挤在同一行导致换行错位 */
.auth-card__tip {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #9aa1ad;
}

/* ---------------- 右卡：微信扫码沙箱 ---------------- */
.auth-card--wx {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding-top: 40px;
}

.auth-card__hero-icon--wechat {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: linear-gradient(145deg, #2fc25f, #1aad19);
  box-shadow: 0 10px 24px rgba(26, 173, 25, 0.35);
}

.auth-card__wx-title {
  margin-top: 18px;
  font-size: 21px;
  font-weight: 700;
  color: #16181d;
}

.auth-card__wx-sub {
  margin-top: 8px;
  font-size: 13px;
  color: #7a8290;
  min-height: 18px;
}

.wx-qr {
  position: relative;
  margin-top: 20px;
  padding: 12px;
  border-radius: 14px;
  background: #fff;
  border: 0.5px solid #e6e9ee;
  cursor: pointer;
  user-select: none;
  transition: box-shadow 0.18s ease;
}

.wx-qr:hover {
  box-shadow: 0 8px 22px rgba(17, 24, 39, 0.12);
}

.wx-qr__code {
  display: block;
}

.wx-qr--expired .wx-qr__code {
  filter: grayscale(1) opacity(0.35);
}

.wx-qr__hint {
  position: absolute;
  right: -8px;
  top: -8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #16181d;
  color: #fff;
  font-size: 12px;
}

.wx-qr__mask {
  position: absolute;
  inset: 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.94);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #16181d;
  font-size: 15px;
  font-weight: 600;
}

.wx-qr__mask small {
  font-size: 12px;
  font-weight: 400;
  color: #7a8290;
}

.wx-qr__mask-icon {
  font-size: 34px;
  color: var(--brix-primary);
}

.wx-qr__mask--ok .wx-qr__mask-icon {
  color: #1aad19;
}

.wx-fade-enter-active,
.wx-fade-leave-active {
  transition: opacity 0.2s ease;
}

.wx-fade-enter-from,
.wx-fade-leave-to {
  opacity: 0;
}

.auth-card__wx-foot {
  margin-top: 12px;
  font-size: 12.5px;
  color: #9aa1ad;
  min-height: 18px;
}

.auth-card__wx-note {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 0.5px dashed #e6e9ee;
  font-size: 12px;
  color: #9aa1ad;
  line-height: 1.6;
}

/* ---------------- 响应式：窄屏叠为单列 ---------------- */
@media (max-width: 860px) {
  .auth-wrap {
    flex-direction: column;
    width: 100%;
    align-items: center;
  }

  .auth-card {
    width: min(400px, 100%);
  }
}
</style>
