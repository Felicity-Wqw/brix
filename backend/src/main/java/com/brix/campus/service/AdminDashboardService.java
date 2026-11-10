package com.brix.campus.service;

import com.brix.campus.model.vo.AdminDashboardVO;

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
}
