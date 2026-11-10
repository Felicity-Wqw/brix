package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.dto.WantSaveDTO;
import com.brix.campus.model.vo.WantVO;

/**
 * 求购墙：买家发需求、卖家私信接单、发帖人关闭。
 */
public interface WantService {

    /**
     * 发布求购帖。
     *
     * @param userId 发布人
     * @param dto    求购内容
     * @return 新建的求购帖（与其他创建接口保持一致，前端可直接拿到 id）
     */
    WantVO publish(Long userId, WantSaveDTO dto);

    /** 求购列表（默认求购中，可带状态过滤），昵称脱敏由前端展示层处理 */
    IPage<WantVO> page(String status, long page, long size, Long viewerId);

    /** 发帖人关闭自己的求购帖（OPEN → CLOSED） */
    void close(Long userId, Long id);
}
