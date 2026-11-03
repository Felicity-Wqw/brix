<template>
  <footer class="footer">
    <div class="brix-container footer__inner">
      <div class="footer__top">
        <!-- 品牌栏 -->
        <div class="footer__brand-col">
          <div class="footer__brand">
            <span class="footer__mark">归</span>
            <span class="footer__brand-name">物归校园</span>
          </div>
          <p class="footer__slogan">
            严格校园版二手交易平台——让每一件闲置，都在校园里找到它的新主人。
          </p>
          <div class="footer__badges">
            <span class="footer__badge">校内面交</span>
            <span class="footer__badge">预付托管</span>
            <span class="footer__badge">AI 审核</span>
          </div>
        </div>

        <!-- 交易指南 -->
        <div class="footer__col">
          <h4 class="footer__col-title">交易指南</h4>
          <router-link to="/publish" class="footer__link">如何发布闲置</router-link>
          <router-link to="/" class="footer__link">如何挑选好物</router-link>
          <router-link to="/my/orders" class="footer__link">查看我的订单</router-link>
        </div>

        <!-- 平台保障 -->
        <div class="footer__col">
          <h4 class="footer__col-title">平台保障</h4>
          <span class="footer__text">预付款平台托管，确认收货才结算</span>
          <span class="footer__text">面交先验货、后放款</span>
          <span class="footer__text">违禁词 + AI 审核员双重合规闸</span>
        </div>

        <!-- 快捷入口 -->
        <div class="footer__col">
          <h4 class="footer__col-title">快捷入口</h4>
          <router-link to="/" class="footer__link">首页</router-link>
          <router-link to="/my/favorites" class="footer__link">我的收藏</router-link>
          <router-link to="/my/analysis" class="footer__link">数据分析</router-link>
          <router-link to="/my/profile" class="footer__link">个人中心</router-link>
        </div>
      </div>

      <div class="footer__bottom">
        <span>© 2026 物归校园 · Brix Campus Market</span>
        <span class="footer__divider">|</span>
        <span>课程演示项目，数据均为仿真种子数据，不产生真实交易</span>
      </div>
    </div>
  </footer>
</template>

<script setup>
// 页脚：品牌 + 交易指南 + 平台保障 + 快捷入口
</script>

<style scoped>
/* 白色毛玻璃：与顶部导航同语言，透出全站浅绿渐变 */
.footer {
  margin-top: 56px;
  background: rgba(255, 253, 249, 0.66);
  backdrop-filter: blur(18px) saturate(1.2);
  -webkit-backdrop-filter: blur(18px) saturate(1.2);
  border-top: 0.5px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 -1px 0 rgba(28, 42, 36, 0.05);
}

.footer__inner {
  padding: 36px 0 22px;
}

.footer__top {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1.3fr 1fr;
  gap: 32px;
}

/* 品牌栏 */
.footer__brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.footer__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--brix-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.footer__brand-name {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--brix-text);
}

.footer__slogan {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--brix-text-secondary);
  max-width: 300px;
}

.footer__badges {
  display: flex;
  gap: 6px;
  margin-top: 12px;
}

.footer__badge {
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--brix-primary-soft);
  color: var(--brix-primary);
  font-size: 12px;
  font-weight: 500;
}

/* 栏目 */
.footer__col {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.footer__col-title {
  margin: 4px 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: var(--brix-text);
}

.footer__link {
  font-size: 13px;
  color: var(--brix-text-secondary);
  transition: color 0.18s ease;
  width: fit-content;
}

.footer__link:hover {
  color: var(--brix-primary);
}

.footer__text {
  font-size: 13px;
  color: var(--brix-text-secondary);
  line-height: 1.6;
}

/* 底部版权行 */
.footer__bottom {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 30px;
  padding-top: 18px;
  border-top: 0.5px solid rgba(28, 42, 36, 0.08);
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.footer__divider {
  color: var(--brix-border-strong);
}
</style>
