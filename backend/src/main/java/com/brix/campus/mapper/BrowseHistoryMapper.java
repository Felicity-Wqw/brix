package com.brix.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brix.campus.model.BrowseHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 浏览历史 Mapper。
 */
@Mapper
public interface BrowseHistoryMapper extends BaseMapper<BrowseHistory> {
}
