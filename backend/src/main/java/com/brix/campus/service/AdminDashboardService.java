package com.brix.campus.service;

import com.brix.campus.model.vo.AdminDashboardVO;
import com.brix.campus.model.vo.AdminScreenVO;

/**
 * 后台数据看板服务（仅 OPERATOR 可访问，权限由 SecurityConfig 的 URL 规则强制）。
 */
public interface AdminDashboardService {

    /**
     * 加载看板全量数据。
     *
     * @param days 统计周期（天）：7 或 30，越界自动收敛到 7~90
     * @return 看板数据（trend 已按天补零，可直接喂图表）
     */
    AdminDashboardVO load(int days);

    /**
     * 数据可视化大屏聚合数据（口径与 {@link #load(int)} 一致，30 秒轮询渲染）。
     *
     * <p>在 30 天看板之上补充：AI 调用统计（估价/文案/图片审核）与实时动态
     * （最新成交/上架/拦截），并带待办角标。</p>
     *
     * @return 大屏聚合数据
     */
    AdminScreenVO screen();

    /**
     * AI 运营数据分析报告（周 / 月 / 季度三周期复用）。
     *
     * <p>基于 {@link #load(int)} 的统计切片生成自然语言解读，仅供运营参考；
     * AI 未配置或生成失败时抛业务异常。</p>
     *
     * @param days 统计周期（天）：7=周报、30=月报、90=季度汇报
     * @return 报告正文
     */
    String aiReport(int days);
}
