package com.brix.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brix.campus.model.ImageAuditCache;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片审核结论缓存 Mapper。
 */
@Mapper
public interface ImageAuditCacheMapper extends BaseMapper<ImageAuditCache> {
}
